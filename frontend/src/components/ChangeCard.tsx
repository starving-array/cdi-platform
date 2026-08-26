import React from 'react';
import { ChangeSummary } from '../types';
import { ChangeStatusBadge } from './ChangeStatusBadge';
import { GitBranch, GitCommit, User, Calendar } from 'lucide-react';

export interface ChangeCardProps {
  change: ChangeSummary;
  isSelected?: boolean;
  onSelect: (changeId: string) => void;
}

export const ChangeCard: React.FC<ChangeCardProps> = ({ change, isSelected = false, onSelect }) => {
  const formattedDate = new Date(change.createdAt).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div
      role="button"
      tabIndex={0}
      aria-pressed={isSelected}
      aria-label={`View details for ${change.title} (${change.providerChangeId})`}
      onClick={() => onSelect(change.id)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onSelect(change.id);
        }
      }}
      className={`p-5 rounded-xl border transition-all cursor-pointer text-left focus:outline-none focus:ring-2 focus:ring-indigo-500/50 ${
        isSelected
          ? 'bg-slate-900/90 border-indigo-500/50 ring-1 ring-indigo-500/30'
          : 'bg-slate-900/40 border-slate-800/80 hover:bg-slate-900/70 hover:border-slate-700'
      }`}
    >
      <div className="flex items-start justify-between gap-4 mb-2">
        <div className="flex items-center gap-2.5">
          <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
            {change.providerChangeId}
          </span>
          <h3 className="text-base font-semibold text-slate-100 line-clamp-1">{change.title}</h3>
        </div>
        <ChangeStatusBadge status={change.status} />
      </div>

      <div className="flex flex-wrap items-center gap-y-2 gap-x-4 text-xs text-slate-400 mt-3">
        <div className="flex items-center gap-1.5">
          <User className="w-3.5 h-3.5 text-slate-500" aria-hidden="true" />
          <span className="text-slate-300 font-medium">{change.author}</span>
        </div>

        <div className="flex items-center gap-1.5 font-mono">
          <GitBranch className="w-3.5 h-3.5 text-slate-500" aria-hidden="true" />
          <span className="text-indigo-300">{change.sourceBranch}</span>
          <span className="text-slate-600">→</span>
          <span className="text-slate-400">{change.targetBranch}</span>
        </div>

        <div className="flex items-center gap-1.5 font-mono">
          <GitCommit className="w-3.5 h-3.5 text-slate-500" aria-hidden="true" />
          <span>{change.latestCommitSha.substring(0, 7)}</span>
        </div>

        <div className="flex items-center gap-1.5 ml-auto text-slate-500">
          <Calendar className="w-3.5 h-3.5" aria-hidden="true" />
          <span>{formattedDate}</span>
        </div>
      </div>
    </div>
  );
};