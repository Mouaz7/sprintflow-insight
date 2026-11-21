package com.sprintflowinsight.crdt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class CrdtStore {
  private static final int SNAPSHOT_EVERY = 128;
  private static final String DOC_COLUMNS =
      "id,project_id,initial_text,revision,framed_bytes,snapshot,snapshot_revision,projection_revision";

  record Doc(
      long id,
      long projectId,
      String initialText,
      long revision,
      int framedBytes,
      byte[] snapshot,
      long snapshotRevision,
      long projectionRevision) {}

  record Update(long revision, byte[] bytes) {}

  record Receipt(long revision, byte[] hash) {}

  record Appended(long revision, boolean duplicate) {}

  private final JdbcTemplate jdbc;

  CrdtStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<Doc> find(long projectId, CrdtTarget target, long targetId, boolean lock) {
    return jdbc
        .query(
            "select "
                + DOC_COLUMNS
                + " from crdt_doc where project_id=? and "
                + target.column
                + "=?"
                + (lock ? " for update" : ""),
            CrdtStore::row,
            projectId,
            targetId)
        .stream()
        .findFirst();
  }

  boolean exists(long projectId, CrdtTarget target, long targetId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from crdt_doc where project_id=? and " + target.column + "=?)",
            Boolean.class,
            projectId,
            targetId));
  }

  Doc open(long projectId, CrdtTarget target, long targetId, String text) {
    jdbc.update(
        "insert into crdt_doc(project_id,"
            + target.column
            + ",initial_text) values(?,?,?)"
            + " on conflict("
            + target.column
            + ") do nothing",
        projectId,
        targetId,
        text);
    return find(projectId, target, targetId, false).orElseThrow();
  }

  List<Update> after(long docId, long revision) {
    return jdbc.query(
        "select revision,payload from crdt_update where doc_id=? and revision>? order by revision",
        (row, index) -> new Update(row.getLong(1), row.getBytes(2)),
        docId,
        revision);
  }

  /** The document row must be locked for the duration of the append and any compaction. */
  Appended append(Doc doc, UUID updateId, byte[] update) {
    if (update.length < 1 || update.length > CrdtFrames.MAX_UPDATE_BYTES)
      throw new IllegalArgumentException("Invalid collaboration update size.");
    byte[] hash = digest(update);
    var receipt =
        jdbc
            .query(
                "select revision,sha256 from crdt_receipt where doc_id=? and update_id=?",
                (row, index) -> new Receipt(row.getLong(1), row.getBytes(2)),
                doc.id(),
                updateId)
            .stream()
            .findFirst();
    if (receipt.isPresent()) {
      if (!MessageDigest.isEqual(receipt.get().hash(), hash))
        throw new IllegalArgumentException("Update ID already used.");
      return new Appended(receipt.get().revision(), true);
    }
    int bytes = doc.framedBytes() + Integer.BYTES + update.length;
    long revision = doc.revision() + 1;
    if (bytes > CrdtFrames.MAX_DOCUMENT_BYTES || revision > CrdtFrames.MAX_FRAME_COUNT)
      throw new IllegalStateException("Collaboration history has reached the document limit.");

    jdbc.update(
        "insert into crdt_receipt(doc_id,update_id,revision,sha256) values(?,?,?,?)",
        doc.id(),
        updateId,
        revision,
        hash);
    jdbc.update(
        "insert into crdt_update(doc_id,revision,payload) values(?,?,?)",
        doc.id(),
        revision,
        update);
    jdbc.update(
        "update crdt_doc set revision=?,framed_bytes=? where id=?", revision, bytes, doc.id());
    if (revision - doc.snapshotRevision() >= SNAPSHOT_EVERY) compact(doc, revision);
    return new Appended(revision, false);
  }

  void projected(Doc doc, long revision) {
    jdbc.update(
        "update crdt_doc set projection_revision=? where id=? and projection_revision<?",
        revision,
        doc.id(),
        revision);
  }

  private void compact(Doc doc, long revision) {
    var frames = new ArrayList<byte[]>();
    if (doc.snapshot() != null) frames.addAll(CrdtFrames.unpack(doc.snapshot()));
    after(doc.id(), doc.snapshotRevision()).forEach(item -> frames.add(item.bytes()));
    jdbc.update(
        "update crdt_doc set snapshot=?,snapshot_revision=? where id=?",
        CrdtFrames.pack(frames),
        revision,
        doc.id());
    jdbc.update("delete from crdt_update where doc_id=? and revision<=?", doc.id(), revision);
  }

  private static Doc row(ResultSet row, int index) throws SQLException {
    return new Doc(
        row.getLong(1),
        row.getLong(2),
        row.getString(3),
        row.getLong(4),
        row.getInt(5),
        row.getBytes(6),
        row.getLong(7),
        row.getLong(8));
  }

  private static byte[] digest(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable.", error);
    }
  }
}
