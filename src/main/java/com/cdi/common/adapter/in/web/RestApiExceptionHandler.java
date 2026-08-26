package com.cdi.common.adapter.in.web;

import com.cdi.application.common.error.ApplicationError;
import com.cdi.application.common.error.ApplicationException;
import com.cdi.common.domain.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Centralized exception translation for the REST API web adapter.
 * Translates {@link ApplicationException} and {@link DomainException} into
 * canonical API error JSON payloads.
 */
@RestControllerAdvice
public class RestApiExceptionHandler {

  public record ErrorResponse(String code, String message, Map<String, ?> details) {}

  @ExceptionHandler(ApplicationException.class)
  public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex) {
    ApplicationError error = ex.getError();
    HttpStatus status = mapHttpStatus(error);
    ErrorResponse body = new ErrorResponse(error.code(), error.defaultMessage(), ex.getDetails());
    return ResponseEntity.status(status).body(body);
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex) {
    ErrorResponse body = new ErrorResponse("INVALID_ARGUMENT", ex.getMessage(), Map.of());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
  }

  private HttpStatus mapHttpStatus(ApplicationError error) {
    return switch (error) {
      case UNAUTHORIZED -> HttpStatus.FORBIDDEN;
      case CHANGE_NOT_FOUND,
           ANALYSIS_RUN_NOT_FOUND,
           ORGANIZATION_NOT_FOUND,
           REPOSITORY_NOT_FOUND,
           SERVICE_NOT_FOUND,
           POLICY_NOT_FOUND,
           DEPLOYMENT_NOT_FOUND,
           ATTRIBUTION_NOT_FOUND,
           DECISION_NOT_FOUND -> HttpStatus.NOT_FOUND;
      case DECISION_ALREADY_OVERRIDDEN,
           ORGANIZATION_ALREADY_SUSPENDED,
           REPOSITORY_ALREADY_ARCHIVED,
           SERVICE_ALREADY_DEPRECATED,
           OUTCOME_ALREADY_RECORDED,
           ANALYSIS_ALREADY_RUNNING,
           ANALYSIS_SUPERSEDED -> HttpStatus.CONFLICT;
      case INSUFFICIENT_CONTEXT,
           POLICY_EVALUATION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;
      case EVIDENCE_COLLECTION_FAILED,
           AGENT_INVESTIGATION_FAILED -> HttpStatus.BAD_GATEWAY;
    };
  }
}