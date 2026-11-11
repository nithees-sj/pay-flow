package com.payflow.common.error;

import com.payflow.common.security.TraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ApiErrors {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> business(ApiException e, HttpServletRequest r) {
    return response(e.status(), e.code(), e.getMessage(), r);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class
  })
  ResponseEntity<ProblemDetail> invalid(Exception e, HttpServletRequest r) {
    return response(400, "VALIDATION_ERROR", "Request contains invalid fields", r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest r) {
    LoggerFactory.getLogger(ApiErrors.class)
        .error(
            "Unhandled error type={} traceId={}",
            e.getClass().getSimpleName(),
            TraceFilter.trace(r));
    return response(500, "INTERNAL_ERROR", "An unexpected error occurred", r);
  }

  private ResponseEntity<ProblemDetail> response(
      int status, String code, String message, HttpServletRequest r) {
    var b = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (code.equals("REQUEST_IN_PROGRESS") || status == 429) b.header("Retry-After", "1");
    return b.body(Problems.create(status, code, message, TraceFilter.trace(r)));
  }
}
