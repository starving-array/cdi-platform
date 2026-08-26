import React from 'react';
import { GitPullRequest, GitMerge, XCircle } from 'lucide-react';

export interface ChangeStatusBadgeProps {
  status: 'OPEN' | 'MERGED' | 'CLOSED';
}

export const ChangeStatusBadge: React.FC<ChangeStatusBadgeProps> = ({ status }) => {
  switch (status) {
    case 'OPEN':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <GitPullRequest className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Open</span>
        </span>
      );
    case 'MERGED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-purple-500/10 text-purple-400 border border-purple-500/20">
          <GitMerge className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Merged</span>
        </span>
      );
    case 'CLOSED':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/20">
          <XCircle className="w-3.5 h-3.5" aria-hidden="true" />
          <span>Closed</span>
        </span>
      );
  }
};