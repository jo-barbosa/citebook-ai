package io.github.jo_barbosa.citebook.application.services.course;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.jo_barbosa.citebook.application.ports.in.course.CourseUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.course.CourseView;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;

@Service
public class CourseService implements CourseUseCases {

	private final CourseRepositoryPort courses;

	public CourseService(CourseRepositoryPort courses) {
		this.courses = courses;
	}

	@Override
	public List<CourseView> list() {
		return courses.findAllSummaries().stream()
				.map(summary -> new CourseView(summary.code(), summary.documentCount()))
				.toList();
	}

}
