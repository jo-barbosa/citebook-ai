package io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.jo_barbosa.citebook.application.ports.out.document.DocumentRepositoryPort;
import io.github.jo_barbosa.citebook.domain.model.document.Document;

@Component
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {

	private static final String COLUMNS = "id, course_id, file_name, sha256, page_count, chunk_count, ingested_at";

	private final JdbcClient jdbc;

	public DocumentRepositoryAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Document> findByCourseIdAndFileName(UUID courseId, String fileName) {
		return jdbc.sql("select " + COLUMNS + " from document where course_id = :courseId and file_name = :fileName")
				.param("courseId", courseId)
				.param("fileName", fileName)
				.query(DocumentRepositoryAdapter::toDocument)
				.optional();
	}

	@Override
	public Document create(UUID courseId, String fileName, String sha256, int pageCount, int chunkCount) {
		return jdbc.sql("""
				insert into document (course_id, file_name, sha256, page_count, chunk_count)
				values (:courseId, :fileName, :sha256, :pageCount, :chunkCount)
				returning\s""" + COLUMNS)
				.param("courseId", courseId)
				.param("fileName", fileName)
				.param("sha256", sha256)
				.param("pageCount", pageCount)
				.param("chunkCount", chunkCount)
				.query(DocumentRepositoryAdapter::toDocument)
				.single();
	}

	@Override
	public void deleteById(UUID id) {
		jdbc.sql("delete from document where id = :id")
				.param("id", id)
				.update();
	}

	private static Document toDocument(ResultSet rs, int rowNum) throws SQLException {
		return new Document(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
				rs.getString("file_name"), rs.getString("sha256"), rs.getInt("page_count"), rs.getInt("chunk_count"),
				rs.getTimestamp("ingested_at").toInstant());
	}

}
