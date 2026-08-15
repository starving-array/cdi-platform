package com.cdi.application.port.out;

import com.cdi.common.domain.id.RepositoryId;
import com.cdi.common.domain.id.ServiceId;
import com.cdi.common.domain.id.TenantId;
import com.cdi.systemcontext.domain.CriticalityTier;
import com.cdi.systemcontext.domain.ServiceDependency;

import java.util.List;

/**
 * Outbound port retrieving architecture metadata and ownership information
 * (ports-and-adapters.md §2.2, application-layer.md §7).
 *
 * <p>Abstracted from whether the data lives in a local YAML catalog, an
 * internal database, or an external IDP (e.g., Backstage).
 *
 * <p><b>Failure policy:</b> catalog unavailable is retryable (short backoff,
 * max 3); the worker degrades by proceeding without a resolved criticality,
 * and unknown criticality is treated conservatively as high risk.
 */
public interface SystemContextPort {

  CriticalityTier getServiceCriticality(TenantId tenantId, RepositoryId repositoryId, List<String> filePaths);

  List<ServiceDependency> getDependencies(TenantId tenantId, ServiceId serviceId);
}