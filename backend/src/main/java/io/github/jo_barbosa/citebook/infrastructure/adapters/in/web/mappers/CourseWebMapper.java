package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.mappers;

import io.github.jo_barbosa.citebook.application.ports.in.course.CourseView;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.CourseResponse;

public final class CourseWebMapper {

	private CourseWebMapper() {
	}

	public static CourseResponse toResponse(CourseView view) {
		return new CourseResponse(view.code(), view.documentCount());
	}

}
