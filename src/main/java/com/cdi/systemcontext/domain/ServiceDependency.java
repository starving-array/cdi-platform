package com.cdi.systemcontext.domain;

import com.cdi.common.domain.exception.DomainException;
import com.cdi.common.domain.id.ServiceId;

import java.util.Objects;

/**
 * Value Object representing a dependency on a target service.
 */
public record ServiceDependency(ServiceId targetServiceId) {
    public ServiceDependency {
        if (targetServiceId == null) {
            throw new DomainException("Target Service ID cannot be null");
        }
    }
}
