import React from 'react';
import { ShieldAlert, ShieldCheck, Shield } from 'lucide-react';

export interface RiskLevelBadgeProps {
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
}

export const RiskLevelBadge: React.FC<RiskLevelBadgeProps> = ({ level }) => {
  switch (level) {
    case 'LOW':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <ShieldCheck className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Low Risk</span>
        </span>
      );
    case 'MEDIUM':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
          <Shield className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Medium Risk</span>
        </span>
      );
    case 'HIGH':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-orange-500/10 text-orange-400 border border-orange-500/20">
          <ShieldAlert className="w-3.5 h-3.5" aria-hidden="true" />
          <span>High Risk</span>
        </span>
      );
    case 'CRITICAL':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
          <ShieldAlert className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Critical Risk</span>
        </span>
      );
  }
};