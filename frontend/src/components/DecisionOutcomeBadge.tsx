import React from 'react';
import { CheckCircle2, AlertTriangle, XOctagon } from 'lucide-react';

export interface DecisionOutcomeBadgeProps {
  outcome: 'APPROVE' | 'REVIEW_REQUIRED' | 'BLOCK';
  overridden?: boolean;
}

export const DecisionOutcomeBadge: React.FC<DecisionOutcomeBadgeProps> = ({ outcome, overridden = false }) => {
  return (
    <div className="inline-flex items-center gap-2">
      {outcome === 'APPROVE' && (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
          <CheckCircle2 className="w-4 h-4" aria-hidden="true" />
          <span>Approved</span>
        </span>
      )}
      {outcome === 'REVIEW_REQUIRED' && (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/30">
          <AlertTriangle className="w-4 h-4" aria-hidden="true" />
          <span>Review Required</span>
        </span>
      )}
      {outcome === 'BLOCK' && (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/30">
          <XOctagon className="w-4 h-4" aria-hidden="true" />
          <span>Blocked</span>
        </span>
      )}
      {overridden && (
        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-400/10 text-amber-300 border border-amber-400/20">
          Overridden
        </span>
      )}
    </div>
  );
};