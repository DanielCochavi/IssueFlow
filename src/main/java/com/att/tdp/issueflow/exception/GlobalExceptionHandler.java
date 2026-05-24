package com.att.tdp.issueflow.exception;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
	}

	@ExceptionHandler(AuthorizationDeniedException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(AuthorizationDeniedException exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
	}

	@ExceptionHandler({ OptimisticLockingFailureException.class, OptimisticLockException.class })
	public ResponseEntity<ErrorResponse> handleOptimisticLock(
			Exception exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.CONFLICT, "Ticket was modified by another request", request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		String message = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.collect(Collectors.joining("; "));

		if (message.isBlank()) {
			message = "Validation failed";
		}

		return buildResponse(HttpStatus.BAD_REQUEST, message, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableMessage(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body", request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request parameter", request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleDataIntegrity(
			DataIntegrityViolationException exception,
			HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, "Request violates a data constraint", request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
	}

	private ResponseEntity<ErrorResponse> buildResponse(
			HttpStatus status,
			String message,
			HttpServletRequest request) {
		return ResponseEntity.status(status)
			.body(new ErrorResponse(
					java.time.Instant.now(),
					status.value(),
					status.getReasonPhrase(),
					message,
					request.getRequestURI()));
	}
}
