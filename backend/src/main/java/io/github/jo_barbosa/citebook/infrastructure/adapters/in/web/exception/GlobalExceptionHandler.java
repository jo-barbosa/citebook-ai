package io.github.jo_barbosa.citebook.infrastructure.adapters.in.web.exception;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.domain.exceptions.NotFoundException;
import io.github.jo_barbosa.citebook.domain.exceptions.UnprocessableDocumentException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final URI ABOUT_BLANK = URI.create("about:blank");

	private final DataSize maxFileSize;

	public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxFileSize) {
		this.maxFileSize = maxFileSize;
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(UnprocessableDocumentException.class)
	ProblemDetail handleUnprocessable(UnprocessableDocumentException ex) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable document", ex.getMessage());
	}

	@ExceptionHandler(ExternalServiceUnavailableException.class)
	ProblemDetail handleUnavailable(ExternalServiceUnavailableException ex) {
		log.warn("External service unavailable", ex);
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable", ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.");
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
				"The file exceeds the " + maxFileSize.toMegabytes() + " MB upload limit.");
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> name(result, error) + ": " + error.getDefaultMessage()))
				.collect(Collectors.joining("; "));
		return handleExceptionInternal(ex, ProblemDetail.forStatusAndDetail(status, detail), headers, status, request);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		Object enriched = body;
		if (enriched == null && ex instanceof ErrorResponse errorResponse) {
			enriched = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
		}
		if (enriched instanceof ProblemDetail problem) {
			if (problem.getType() == null || ABOUT_BLANK.equals(problem.getType())) {
				problem.setType(URI.create("/problems/" + slug(status)));
			}
			problem.setProperty("timestamp", Instant.now());
		}
		return super.handleExceptionInternal(ex, enriched, headers, status, request);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("/problems/" + slug(status)));
		problem.setTitle(title);
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}

	/**
	 * The name the client sent: the field for an error inside a request body ("question"), the parameter otherwise
	 * ("courseCode"). With a constraint on a path variable, Spring validates the whole method, so body errors arrive
	 * here too.
	 */
	private static String name(ParameterValidationResult result, MessageSourceResolvable error) {
		return error instanceof FieldError field ? field.getField() : result.getMethodParameter().getParameterName();
	}

	private static String slug(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "validation";
			case 404 -> "not-found";
			case 405 -> "method-not-allowed";
			case 413 -> "payload-too-large";
			case 415 -> "unsupported-media-type";
			case 422 -> "unprocessable-document";
			case 503 -> "service-unavailable";
			default -> "internal-error";
		};
	}

}
