package com.airconcepts.dashboard.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiFailureHandler {
  @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
      org.springframework.web.bind.MissingServletRequestParameterException.class,
      org.springframework.http.converter.HttpMessageNotReadableException.class})
  ResponseEntity<ProblemDetail> invalidRequest() {
    return failure(new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check the request values and try again."));
  }
  @ExceptionHandler(ApiFailure.class)
  ResponseEntity<ProblemDetail> failure(ApiFailure failure) {
    var detail = ProblemDetail.forStatusAndDetail(failure.status(), failure.getMessage());
    detail.setProperty("code", failure.code());
    if (failure instanceof ProviderFailure provider) detail.setProperty("upstreamStatus", provider.upstreamStatus());
    var response = ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore());
    if (failure instanceof ProviderFailure provider && provider.retryAfterSeconds() != null) {
      detail.setProperty("retryAfterSeconds", provider.retryAfterSeconds());
      response.header(HttpHeaders.RETRY_AFTER, provider.retryAfterSeconds().toString());
    }
    return response.body(detail);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict() {
    return failure(new ApiFailure(HttpStatus.CONFLICT, "RECORD_CONFLICT",
        "This record conflicts with an existing record. Refresh and check the current details."));
  }
}
