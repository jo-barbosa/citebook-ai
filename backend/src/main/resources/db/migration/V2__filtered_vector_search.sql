-- PgVectorStore filters metadata with a jsonpath on metadata::jsonb (e.g. $.course_code == "esinf"). The V1
-- btree indexes on metadata ->> '...' cannot serve that operator, so they cost writes and serve no query.
-- One GIN index with jsonb_path_ops serves the @@ jsonpath filters: course_code (search) and document_id (delete).
DROP INDEX vector_store_document_id_idx;
DROP INDEX vector_store_course_code_idx;

CREATE INDEX vector_store_metadata_idx
    ON vector_store USING gin ((metadata::jsonb) jsonb_path_ops);

-- With the HNSW plan the course filter runs after the index scan, which yields about hnsw.ef_search (40) nearest
-- chunks of all courses; a small course can then get fewer than k results, or none. Iterative scans keep reading
-- the index until k rows pass the filter or hnsw.max_scan_tuples (20000) is reached. strict_order keeps exact
-- distance order (PgVectorStore orders by distance). Set as the database default, so it applies to every new
-- connection, whatever the database is called.
-- Applies to connections opened after this migration; the app's pool also sets it per connection
-- (spring.datasource.hikari.connection-init-sql), because the pool opens connections while Flyway runs.
-- ALTER DATABASE needs the database owner (true for compose and Testcontainers).
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET hnsw.iterative_scan = strict_order', current_database());
END
$$;
