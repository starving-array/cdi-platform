import React, { createContext, useContext, useState, useMemo, ReactNode } from 'react';
import { ActorRole } from '../types';
import { ApiClient } from '../api/client';

export interface DevSecurityContextState {
  tenantId: string;
  actorId: string;
  actorRole: ActorRole;
  setTenantId: (tenantId: string) => void;
  setActorId: (actorId: string) => void;
  setActorRole: (role: ActorRole) => void;
  api: ApiClient;
}

// Deterministic dev default for mock testing in local environment
const DEFAULT_DEV_TENANT = '00000000-0000-0000-0000-000000000001';
const DEFAULT_DEV_ACTOR_ID = 'dev-engineer-1';
const DEFAULT_DEV_ROLE: ActorRole = 'ENGINEER';

const DevSecurityContext = createContext<DevSecurityContextState | undefined>(undefined);

export interface DevSecurityProviderProps {
  children: ReactNode;
  initialTenantId?: string;
  initialActorId?: string;
  initialActorRole?: ActorRole;
  baseUrl?: string;
}

export const DevSecurityProvider: React.FC<DevSecurityProviderProps> = ({
  children,
  initialTenantId = DEFAULT_DEV_TENANT,
  initialActorId = DEFAULT_DEV_ACTOR_ID,
  initialActorRole = DEFAULT_DEV_ROLE,
  baseUrl = 'http://localhost:8080',
}) => {
  const [tenantId, setTenantId] = useState<string>(initialTenantId);
  const [actorId, setActorId] = useState<string>(initialActorId);
  const [actorRole, setActorRole] = useState<ActorRole>(initialActorRole);

  const api = useMemo(() => {
    return new ApiClient({
      baseUrl,
      tenantId,
      actorId,
      actorRole,
    });
  }, [baseUrl, tenantId, actorId, actorRole]);

  const value = useMemo(
    () => ({
      tenantId,
      actorId,
      actorRole,
      setTenantId,
      setActorId,
      setActorRole,
      api,
    }),
    [tenantId, actorId, actorRole, api]
  );

  return <DevSecurityContext.Provider value={value}>{children}</DevSecurityContext.Provider>;
};

export const useDevSecurity = (): DevSecurityContextState => {
  const context = useContext(DevSecurityContext);
  if (!context) {
    throw new Error('useDevSecurity must be used within a DevSecurityProvider');
  }
  return context;
};