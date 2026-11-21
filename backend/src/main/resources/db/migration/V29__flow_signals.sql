-- Score title pairs with the same function in the app and the labelled Jira evaluation.
CREATE FUNCTION task_duplicate_score(a text, b text) RETURNS double precision
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
  WITH lexemes AS (
    SELECT tsvector_to_array(to_tsvector('english'::regconfig, a) || to_tsvector('swedish'::regconfig, a)) AS left_words,
           tsvector_to_array(to_tsvector('english'::regconfig, b) || to_tsvector('swedish'::regconfig, b)) AS right_words
  ), inventory AS (
    SELECT cardinality(left_words) + cardinality(right_words) AS word_count,
      (SELECT count(*) FROM (SELECT unnest(left_words) INTERSECT SELECT unnest(right_words)) shared) AS overlap
    FROM lexemes
  )
  SELECT 0.75 * extensions.similarity(a, b) + 0.25 * coalesce(overlap / nullif(word_count - overlap, 0)::double precision, 0)
  FROM inventory
$$;

CREATE TABLE project_flow_anomaly (
  project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  period date NOT NULL,
  metric varchar(12) NOT NULL CHECK (metric IN ('INFLOW', 'THROUGHPUT')),
  direction varchar(4) NOT NULL CHECK (direction IN ('LOW', 'HIGH')),
  observed integer NOT NULL CHECK (observed >= 0),
  weighted double precision NOT NULL,
  baseline double precision NOT NULL,
  lower_limit double precision NOT NULL,
  upper_limit double precision NOT NULL,
  PRIMARY KEY (project_id, period, metric)
);
