package io.github.jo_barbosa.citebook.infrastructure.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.course.CourseSummary;

@Component
public class CourseRepositoryAdapter implements CourseRepositoryPort {

	private final JdbcClient jdbc;

	public CourseRepositoryAdapter(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Course> findByCode(String code) {
		return jdbc.sql("select id, code, created_at from course where code = :code")
				.param("code", code)
				.query(CourseRepositoryAdapter::toCourse)
				.optional();
	}

	@Override
	public Course create(String code) {
		return jdbc.sql("insert into course (code) values (:code) returning id, code, created_at")
				.param("code", code)
				.query(CourseRepositoryAdapter::toCourse)
				.single();
	}

	@Override
	public List<String> findAllCodes() {
		return jdbc.sql("select code from course order by code")
				.query(String.class)
				.list();
	}

	@Override
	public List<CourseSummary> findAllSummaries() {
		return jdbc.sql("""
				select c.code, count(d.id) as documents
				from course c
				left join document d on d.course_id = c.id
				group by c.code
				order by c.code
				""")
				.query((rs, rowNum) -> new CourseSummary(rs.getString("code"), rs.getInt("documents")))
				.list();
	}

	private static Course toCourse(ResultSet rs, int rowNum) throws SQLException {
		return new Course(rs.getObject("id", UUID.class), rs.getString("code"),
				rs.getTimestamp("created_at").toInstant());
	}

}
