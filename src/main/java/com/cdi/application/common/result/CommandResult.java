package com.cdi.application.common.result;

/**
 * Marker contract for the outcome of a state-mutating command.
 *
 * <p>Represents <em>success</em> only; application <em>failures</em> are
 * modelled as {@link com.cdi.application.common.error.ApplicationException}.
 * Known implementations: {@link IdempotentCommandResult},
 * {@link CreateOrganizationResult}, {@link CreateRepositoryResult},
 * {@link CreateServiceResult}, {@link CreatePolicyResult},
 * {@link OverrideDecisionResult}, {@link SuspendOrganizationResult},
 * {@link ArchiveRepositoryResult}. Not
 * coupled to HTTP responses.
 */
public interface CommandResult {
}