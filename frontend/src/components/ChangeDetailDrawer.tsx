import React, { useEffect } from 'react';
import { ChangeDetail, ApiError } from '../types';
import { ChangeStatusBadge } from './ChangeStatusBadge';
import { DecisionOutcomeBadge } from './DecisionOutcomeBadge';
import { RiskLevelBadge } from './RiskLevelBadge';
import { X, GitBranch, GitCommit, User, FileText, Activity, AlertCircle, RefreshCw } from 'lucide-react';

export interface ChangeDetailDrawerProps {
  detail: ChangeDetail | null;
  loading: boolean;
  error: ApiError | null;
  onClose: () => void;
  onRetry: () => void;
}

export const ChangeDetailDrawer: React.FC<ChangeDetailDrawerProps> = ({
  detail,
  loading,
  error,
  onClose,
  onRetry,
}) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Change details inspector"
      className="fixed inset-y-0 right-0 w-full max-w-xl bg-slate-900 border-l border-slate-800 shadow-2xl z-50 flex flex-col focus:outline-none"
    >
      {/* Drawer Header */}
      <div className="p-6 border-b border-slate-800 flex items-center justify-between gap-4">
        <div>
          <span className="text-xs font-mono font-medium text-indigo-400">
            {detail?.change.providerChangeId || 'Change Inspector'}
          </span>
          <h2 className="text-lg font-semibold text-slate-100 line-clamp-1 mt-0.5">
            {detail?.change.title || 'Loading details...'}
          </h2>
        </div>
        <button
          onClick={onClose}
          aria-label="Close inspector"
          className="p-2 rounded-lg text-slate-400 hover:text-slate-100 hover:bg-slate-800 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Drawer Content */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {loading && (
          <div className="flex flex-col items-center justify-center py-16 text-slate-400 gap-3">
            <RefreshCw className="w-6 h-6 animate-spin text-indigo-400" />
            <span className="text-sm">Fetching decision intelligence...</span>
          </div>
        )}

        {error && !loading && (
          <div className="p-4 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-5 h-5" />
              <span className="font-semibold text-sm">Failed to load change details</span>
            </div>
            <p className="text-xs text-rose-200">{error.message || 'An error occurred.'}</p>
            <button
              onClick={onRetry}
              className="mt-2 self-start px-3 py-1.5 rounded-md bg-rose-500/20 hover:bg-rose-500/30 text-rose-200 text-xs font-semibold"
            >
              Retry
            </button>
          </div>
        )}

        {detail && !loading && (
          <>
            {/* Decision Intelligence Section */}
            <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800 space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Policy Decision
                </span>
                {detail.latestDecision ? (
                  <DecisionOutcomeBadge
                    outcome={detail.latestDecision.outcome}
                    overridden={detail.latestDecision.overridden}
                  />
                ) : (
                  <span className="text-xs text-slate-500 italic">No decision generated</span>
                )}
              </div>

              <div className="flex items-center justify-between pt-3 border-t border-slate-800/80">
                <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Risk Assessment
                </span>
                {detail.latestRisk ? (
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-bold text-slate-200 font-mono">
                      {detail.latestRisk.overallScore}/100
                    </span>
                    <RiskLevelBadge level={detail.latestRisk.riskLevel} />
                  </div>
                ) : (
                  <span className="text-xs text-slate-500 italic">No risk score available</span>
                )}
              </div>
            </div>

            {/* Metadata Grid */}
            <div className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Change Details
              </h3>
              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="p-3 rounded-lg bg-slate-800/40 border border-slate-800">
                  <div className="text-slate-500 mb-1 flex items-center gap-1.5">
                    <User className="w-3.5 h-3.5" /> Author
                  </div>
                  <div className="font-semibold text-slate-200">{detail.change.author}</div>
                </div>

                <div className="p-3 rounded-lg bg-slate-800/40 border border-slate-800">
                  <div className="text-slate-500 mb-1 flex items-center gap-1.5">
                    <Activity className="w-3.5 h-3.5" /> Status
                  </div>
                  <div>
                    <ChangeStatusBadge status={detail.change.status} />
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-slate-800/40 border border-slate-800 col-span-2">
                  <div className="text-slate-500 mb-1 flex items-center gap-1.5">
                    <GitBranch className="w-3.5 h-3.5" /> Branches
                  </div>
                  <div className="font-mono text-slate-200">
                    <span className="text-indigo-400">{detail.change.sourceBranch}</span> →{' '}
                    <span className="text-slate-300">{detail.change.targetBranch}</span>
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-slate-800/40 border border-slate-800 col-span-2">
                  <div className="text-slate-500 mb-1 flex items-center gap-1.5">
                    <GitCommit className="w-3.5 h-3.5" /> Latest Commit SHA
                  </div>
                  <div className="font-mono text-slate-200 text-xs break-all">
                    {detail.change.latestCommitSha}
                  </div>
                </div>
              </div>
            </div>

            {/* Description */}
            {detail.change.description && (
              <div className="space-y-2">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                  <FileText className="w-3.5 h-3.5" /> Description
                </h3>
                <div className="p-3.5 rounded-lg bg-slate-800/30 border border-slate-800 text-xs text-slate-300 leading-relaxed whitespace-pre-wrap">
                  {detail.change.description}
                </div>
              </div>
            )}

            {/* Analysis History */}
            <div className="space-y-3">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Analysis Run History ({detail.analysisRuns.length})
              </h3>
              {detail.analysisRuns.length === 0 ? (
                <p className="text-xs text-slate-500 italic">No analysis runs recorded.</p>
              ) : (
                <div className="space-y-2">
                  {detail.analysisRuns.map((run) => (
                    <div
                      key={run.id}
                      className="p-3 rounded-lg bg-slate-800/30 border border-slate-800 flex items-center justify-between text-xs"
                    >
                      <div className="flex items-center gap-2 font-mono">
                        <GitCommit className="w-3.5 h-3.5 text-slate-500" />
                        <span className="text-slate-300">{run.commitSha.substring(0, 7)}</span>
                      </div>
                      <span className="px-2 py-0.5 rounded text-[11px] font-semibold bg-slate-800 text-slate-300 border border-slate-700">
                        {run.status}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
};