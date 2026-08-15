package com.cdi.risk.domain;

/**
 * Types of deterministic risk factors that can increase risk.
 */
public enum RiskFactorType {
    HIGH_CHANGE_SIZE,
    HIGH_SERVICE_CRITICALITY,
    HIGH_DEPENDENCY_IMPACT,
    DATABASE_SCHEMA_CHANGE,
    CONFIGURATION_CHANGE,
    HISTORICAL_INCIDENT_MATCH
}
