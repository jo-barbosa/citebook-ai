package io.github.jo_barbosa.citebook.infrastructure.adapters.out.pgvector;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import io.github.jo_barbosa.citebook.FakeEmbeddingModel;
import io.github.jo_barbosa.citebook.TestDatabase;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;

/**
 * A course-filtered search must return k chunks when the HNSW index is used and the course is a small share of the
 * table. The index yields the nearest chunks of all courses (about hnsw.ef_search = 40 of them) and the course filter
 * runs afterwards; without iterative index scans (V2) every one of them can belong to another course, and the search
 * returns nothing.
 */
class FilteredHnswSearchTest {

	private static final String QUERY_VECTOR = "[1" + ",0".repeat(FakeEmbeddingModel.DIMENSIONS - 1) + "]";

	@BeforeEach
	void clean() {
		TestDatabase.clear();
	}

	@AfterEach
	void cleanUp() {
		TestDatabase.clear();
	}

	@Test
	void returnsKChunksOfASmallCourseWhenTheHnswIndexIsUsed() throws SQLException {
		// "where i > 0" ties the subquery to the row, so random() gives each row its own vector.
		// 3000 chunks of another course around the query (first axis), cosine similarity about 0.74.
		TestDatabase.jdbcClient().sql("""
				insert into vector_store (id, content, metadata, embedding)
				select gen_random_uuid(), 'other ' || i,
				       json_build_object('course_code', 'other', 'file_name', 'other.pdf', 'page', 1),
				       array(select case when d = 1 then 1.0 else (random() - 0.5) * 0.1 end
				             from generate_series(1, 1024) d where i > 0)::vector(1024)
				from generate_series(1, 3000) i
				""").update();
		// 5 chunks of "esinf" in the same region, a little further away (about 0.63): every chunk
		// of the other course ranks above them. Outliers far from the rest would be poorly linked in the HNSW graph and
		// unreachable even with iterative scans.
		TestDatabase.jdbcClient().sql("""
				insert into vector_store (id, content, metadata, embedding)
				select gen_random_uuid(), 'esinf ' || i,
				       json_build_object('course_code', 'esinf', 'file_name', 'esinf.pdf', 'page', i),
				       array(select case when d = 1 then 1.0 else (random() - 0.5) * 0.13 end
				             from generate_series(1, 1024) d where i > 0)::vector(1024)
				from generate_series(1, 5) i
				""").update();
		TestDatabase.jdbcClient().sql("analyze vector_store").update();

		// SET, EXPLAIN and the search must run on the same connection.
		SingleConnectionDataSource connection = new SingleConnectionDataSource(
				TestDatabase.dataSource().getConnection(), true);
		try {
			JdbcTemplate session = new JdbcTemplate(connection);
			// A large table gets the HNSW plan on its own; this small one needs a push. Bitmap scans are off too,
			// so the GIN metadata index cannot replace the HNSW plan with an exact one.
			session.execute("set enable_seqscan = off");
			session.execute("set enable_bitmapscan = off");
			String plan = String.join("\n", session.queryForList("""
					explain select * from vector_store
					where metadata::jsonb @@ '$.course_code == "esinf"'::jsonpath
					order by embedding <=> '%s'::vector limit 5
					""".formatted(QUERY_VECTOR), String.class));
			ChunkStorePort store = new ChunkStoreAdapter(PgVectorStore.builder(session, new QueryVectorModel())
					.dimensions(FakeEmbeddingModel.DIMENSIONS)
					.initializeSchema(false)
					.build());

			List<ChunkMatch> matches = store.search("esinf", "any question", 5, 0.0);

			assertThat(plan).as("the search uses the HNSW index").contains("vector_store_embedding_idx");
			assertThat(matches).hasSize(5).extracting(ChunkMatch::fileName).containsOnly("esinf.pdf");
		}
		finally {
			connection.destroy();
		}
	}

	/** Embeds every text as the query vector: a unit vector on the first axis. */
	private static final class QueryVectorModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<Embedding> embeddings = new ArrayList<>();
			for (int i = 0; i < request.getInstructions().size(); i++) {
				embeddings.add(new Embedding(vector(), i));
			}
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public float[] embed(Document document) {
			return vector();
		}

		@Override
		public int dimensions() {
			return FakeEmbeddingModel.DIMENSIONS;
		}

		private static float[] vector() {
			float[] vector = new float[FakeEmbeddingModel.DIMENSIONS];
			vector[0] = 1f;
			return vector;
		}

	}

}
