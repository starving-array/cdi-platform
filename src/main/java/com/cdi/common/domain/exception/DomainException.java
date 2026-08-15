package com.cdi.common.domain.exception;

/**
 * Base exception for all domain invariant violations.
 * Domain logic should throw this (or subclasses of it) when a business rule is violated.
 * This ensures the domain layer does not depend on HTTP, Spring, or persistence exceptions.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
