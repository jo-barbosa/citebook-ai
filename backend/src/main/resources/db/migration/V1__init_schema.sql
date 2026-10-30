CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE course (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code       text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE document (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   uuid NOT NULL REFERENCES course (id),
    file_name   text NOT NULL,
    sha256      text NOT NULL,
    page_count  integer NOT NULL,
    chunk_count integer NOT NULL,
    ingested_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (course_id, file_name)
);

-- Shape expected by Spring AI's PgVectorStore (id is supplied by the store).
CREATE TABLE vector_store (
    id        uuid PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1024)
);

CREATE INDEX vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);
CREATE INDEX vector_store_document_id_idx
    ON vector_store ((metadata ->> 'document_id'));
CREATE INDEX vector_store_course_code_idx
    ON vector_store ((metadata ->> 'course_code'));
