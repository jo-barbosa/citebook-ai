package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.jo_barbosa.citebook.application.ports.in.course.CourseUseCases;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.dto.response.CourseResponse;
import io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.mappers.CourseWebMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Courses", description = "Courses and their material")
@RequestMapping("/api/courses")
public class CourseController {

	private final CourseUseCases courseUseCases;

	public CourseController(CourseUseCases courseUseCases) {
		this.courseUseCases = courseUseCases;
	}

	@Operation(summary = "List courses",
			description = "Every course with the number of documents indexed in it, ordered by code.")
	@ApiResponse(responseCode = "200", description = "The courses; an empty list when there are none")
	@GetMapping
	public List<CourseResponse> list() {
		return courseUseCases.list().stream()
				.map(CourseWebMapper::toResponse)
				.toList();
	}

}
