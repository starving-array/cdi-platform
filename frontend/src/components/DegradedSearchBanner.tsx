import React from 'react';
import { AlertTriangle } from 'lucide-react';

export const DegradedSearchBanner: React.FC = () => {
  return (
    <div
      role="alert"
      aria-label="Degraded search warning"
      className="p-4 rounded-xl bg-amber-500/10 border border-amber-500/30 text-amber-300 flex items-start gap-3"
    >
      <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0 mt-0.5" aria-hidden="true" />
      <div className="text-xs space-y-1">
        <h4 className="font-semibold text-amber-200 text-sm">Degraded Fallback Mode</h4>
        <p className="text-amber-300/90 leading-relaxed">
          Evidence search is operating in degraded fallback mode. Live retrieval was unavailable; partial or empty results may be returned.
        </p>
      </div>
    </div>
  );
};