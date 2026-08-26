import React from 'react';
import { useDevSecurity } from '../context/DevSecurityContext';
import { Shield, Building, Terminal } from 'lucide-react';
import { ActorRole } from '../types';

export const AppHeader: React.FC = () => {
  const { tenantId, actorRole, setActorRole, setTenantId } = useDevSecurity();

  return (
    <header className="border-b border-slate-800 bg-slate-900/80 backdrop-blur px-6 py-4 flex flex-wrap items-center justify-between gap-4">
      <div className="flex items-center gap-3">
        <div className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
          <Terminal className="w-5 h-5" />
        </div>
        <div>
          <h1 className="text-base font-semibold text-slate-100 tracking-tight">CDI Platform</h1>
          <p className="text-xs text-slate-400">Change Decision Intelligence</p>
        </div>
      </div>

      <div className="flex items-center gap-4 text-xs">
        {/* Mock Tenant Selector */}
        <div className="flex items-center gap-2 bg-slate-950/60 border border-slate-800 rounded-md px-3 py-1.5 text-slate-300">
          <Building className="w-3.5 h-3.5 text-slate-400" />
          <span className="text-slate-400 font-medium">Tenant:</span>
          <input
            type="text"
            aria-label="Tenant ID"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            className="bg-transparent border-none focus:outline-none text-slate-200 font-mono w-40 text-xs truncate"
            title={tenantId}
          />
        </div>

        {/* Mock Role Switcher */}
        <div className="flex items-center gap-2 bg-slate-950/60 border border-slate-800 rounded-md px-3 py-1.5">
          <Shield className="w-3.5 h-3.5 text-indigo-400" />
          <span className="text-slate-400 font-medium">Role:</span>
          <select
            aria-label="Actor Role"
            value={actorRole}
            onChange={(e) => setActorRole(e.target.value as ActorRole)}
            className="bg-transparent border-none focus:outline-none text-indigo-300 font-medium text-xs cursor-pointer"
          >
            <option value="ENGINEER" className="bg-slate-900 text-slate-200">ENGINEER</option>
            <option value="TENANT_ADMIN" className="bg-slate-900 text-slate-200">TENANT_ADMIN</option>
          </select>
        </div>
      </div>
    </header>
  );
};