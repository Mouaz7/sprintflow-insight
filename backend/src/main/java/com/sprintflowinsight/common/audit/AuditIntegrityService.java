package com.sprintflowinsight.common.audit;

import com.sprintflowinsight.common.crypto.EncryptedColumn;
import com.sprintflowinsight.common.crypto.EncryptionException;
import com.sprintflowinsight.common.crypto.EncryptionService;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditIntegrityService {
  private static final EncryptedColumn SEAL =
      EncryptedColumn.of("admin_audit_anchor", "id", "authenticated_proof");
  private static final HexFormat HEX = HexFormat.of();

  public record Anchor(LocalDate day, long position, String hash) {}

  public record Verification(
      boolean valid,
      long checkedEvents,
      long floorPosition,
      long headPosition,
      String headHash,
      Long invalidPosition,
      Anchor dailyAnchor) {}

  private record Chain(long head, byte[] headHash, long floor, byte[] floorHash) {}

  private record StoredAnchor(LocalDate day, long position, byte[] hash, String proof) {
    Anchor summary() {
      return new Anchor(day, position, HEX.formatHex(hash));
    }
  }

  private final JdbcTemplate jdbc;
  private final EncryptionService encryption;

  AuditIntegrityService(JdbcTemplate jdbc, EncryptionService encryption) {
    this.jdbc = jdbc;
    this.encryption = encryption;
  }

  @Bean
  EncryptedColumn auditAnchorSeal() {
    return SEAL;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Verification verify(long projectId) {
    Chain chain =
        jdbc
            .query(
                """
select head_position,head_hash,floor_position,floor_hash from admin_audit_chain where scope_id=?
""",
                (row, index) ->
                    new Chain(row.getLong(1), row.getBytes(2), row.getLong(3), row.getBytes(4)),
                projectId)
            .stream()
            .findFirst()
            .orElse(
                new Chain(
                    0,
                    new byte[AuditChainHash.HASH_BYTES],
                    0,
                    new byte[AuditChainHash.HASH_BYTES]));
    StoredAnchor anchor =
        jdbc
            .query(
                """
                select anchor_day,position,hash,authenticated_proof from admin_audit_anchor
                where scope_id=? order by anchor_day desc limit 1
                """,
                (row, index) ->
                    new StoredAnchor(
                        row.getObject(1, LocalDate.class),
                        row.getLong(2),
                        row.getBytes(3),
                        row.getString(4)),
                projectId)
            .stream()
            .findFirst()
            .orElse(null);

    var cursor = new Cursor(chain);
    if (anchor != null) {
      if (!sealValid(projectId, anchor) || anchor.position() > chain.head())
        cursor.invalid = anchor.position();
      else if (anchor.position() == chain.floor()
          && !MessageDigest.isEqual(chain.floorHash(), anchor.hash()))
        cursor.invalid = anchor.position();
    }
    jdbc.query(
        connection -> {
          var statement =
              connection.prepareStatement(
                  """
                  select chain_position,previous_hash,chain_hash,audit_event_bytes(e)
                  from admin_audit_event e where coalesce(project_id,0)=? order by chain_position
                  """);
          statement.setLong(1, projectId);
          statement.setFetchSize(256);
          return statement;
        },
        (RowCallbackHandler)
            row ->
                cursor.add(
                    row.getLong(1), row.getBytes(2), row.getBytes(3), row.getBytes(4), anchor));
    if (cursor.invalid == null
        && (cursor.position != chain.head()
            || !MessageDigest.isEqual(cursor.hash, chain.headHash())))
      cursor.invalid = cursor.position + 1;
    return new Verification(
        cursor.invalid == null,
        cursor.position - chain.floor(),
        chain.floor(),
        chain.head(),
        HEX.formatHex(chain.headHash()),
        cursor.invalid,
        anchor == null ? null : anchor.summary());
  }

  /** Capture one authenticated head per stream and UTC day. */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public void anchor(LocalDate day) {
    var chains =
        jdbc.query(
            """
            select scope_id,head_position,head_hash from admin_audit_chain
            where not exists (select 1 from admin_audit_anchor a
                              where a.scope_id=admin_audit_chain.scope_id and a.anchor_day=?)
            order by scope_id
            """,
            (row, index) -> new Head(row.getLong(1), row.getLong(2), row.getBytes(3)),
            day);
    for (var head : chains) {
      if (!verify(head.scope()).valid()) continue;
      String proof =
          encryption.encrypt(
              sealPayload(head.scope(), day, head.position(), head.hash()), SEAL.context());
      jdbc.update(
          """
          insert into admin_audit_anchor(scope_id,anchor_day,position,hash,authenticated_proof)
          values (?,?,?,?,?) on conflict (scope_id,anchor_day) do nothing
          """,
          head.scope(),
          day,
          head.position(),
          head.hash(),
          proof);
    }
  }

  private record Head(long scope, long position, byte[] hash) {}

  private boolean sealValid(long scope, StoredAnchor anchor) {
    try {
      return sealPayload(scope, anchor.day(), anchor.position(), anchor.hash())
          .equals(encryption.decrypt(anchor.proof(), SEAL.context()));
    } catch (EncryptionException | IllegalArgumentException failure) {
      return false;
    }
  }

  private static String sealPayload(long scope, LocalDate day, long position, byte[] hash) {
    return "sprintflow-audit-anchor-v1\n"
        + scope
        + "\n"
        + day
        + "\n"
        + position
        + "\n"
        + HEX.formatHex(hash);
  }

  private static final class Cursor {
    private long position;
    private byte[] hash;
    private Long invalid;

    private Cursor(Chain chain) {
      position = chain.floor();
      hash = chain.floorHash();
    }

    private void add(
        long next, byte[] previous, byte[] result, byte[] payload, StoredAnchor anchor) {
      if (invalid != null) return;
      if (!AuditChainHash.links(position + 1, hash, next, previous, payload, result)
          || (anchor != null
              && anchor.position() == next
              && !MessageDigest.isEqual(anchor.hash(), result))) {
        invalid = next;
        return;
      }
      position = next;
      hash = result;
    }
  }
}
