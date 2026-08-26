import React from 'react';
import { AlertOctagon, GitCommit, Server, GitPullRequest, FileText, Layers, Activity, HelpCircle } from 'lucide-react';

export interface EvidenceSourceBadgeProps {
  source: string;
}

export const EvidenceSourceBadge: React.FC<EvidenceSourceBadgeProps> = ({ source }) => {
  const normalized = source.toUpperCase();

  switch (normalized) {
    case 'INCIDENT':
    case 'POSTMORTEM':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
          <AlertOctagon className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Incident</span>
        </span>
      );
    case 'COMMIT':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
          <GitCommit className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Commit</span>
        </span>
      );
    case 'SERVICE':
    case 'DEPENDENCY':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
          <Server className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Service</span>
        </span>
      );
    case 'CHANGE':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-500/10 text-purple-400 border border-purple-500/20">
          <GitPullRequest className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Change</span>
        </span>
      );
    case 'DEPLOYMENT':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20">
          <Activity className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Deployment</span>
        </span>
      );
    case 'DOCUMENTATION':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <FileText className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Documentation</span>
        </span>
      );
    case 'TEST_HISTORY':
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20">
          <Layers className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Test History</span>
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/20">
          <HelpCircle className="w-3.5 h-3.5" aria-hidden="true" />
          <span>{source}</span>
        </span>
      );
  }
};