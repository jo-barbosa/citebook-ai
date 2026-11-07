package io.github.jo_barbosa.citebook.application.ports.out.course;

import java.util.List;
import java.util.Optional;

import io.github.jo_barbosa.citebook.domain.model.course.Course;

public interface CourseRepositoryPort {

	Optional<Course> findByCode(String code);

	Course create(String code);

	List<String> findAllCodes();

}
