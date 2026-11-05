package io.github.jo_barbosa.citebook.domain.exceptions;

import java.util.Collection;

public class CourseNotFoundException extends NotFoundException {

	public CourseNotFoundException(String code, Collection<String> availableCodes) {
		super(message(code, availableCodes));
	}

	private static String message(String code, Collection<String> availableCodes) {
		String prefix = "No course with code '" + code + "'. ";
		if (availableCodes.isEmpty()) {
			return prefix + "There are no courses yet.";
		}
		return prefix + "Available courses: " + String.join(", ", availableCodes) + ".";
	}

}
