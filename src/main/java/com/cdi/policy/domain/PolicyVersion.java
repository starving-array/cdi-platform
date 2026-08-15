package com.cdi.policy.domain;

import com.cdi.common.domain.exception.DomainException;

/**
 * Value object representing the version of a Policy.
 */
public record PolicyVersion(String value) {
    public PolicyVersion {
        if (value == null || value.isBlank()) {
            throw new DomainException("PolicyVersion value cannot be blank");
        }
    }

    public static PolicyVersion of(String version) {
        return new PolicyVersion(version);
    }
}
