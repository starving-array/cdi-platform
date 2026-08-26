import React, { useState, useEffect, useCallback } from 'react';
import { useDevSecurity } from '../context/DevSecurityContext';
import { ChangeSummary, ChangeDetail, ApiError } from '../types';
import { ChangeCard } from '../components/ChangeCard';
import { ChangeDetailDrawer } from '../components/ChangeDetailDrawer';
import { ApiClientError } from '../api/client';
import { Filter, RefreshCw, AlertCircle, GitPullRequest } from 'lucide-react';

export const ChangesFeedPage: React.FC = () => {
  const { api, tenantId, actorRole } = useDevSecurity();

  const [changes, setChanges] = useState<ChangeSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | null>(null);

  const [repoFilterInput, setRepoFilterInput] = useState<string>('');
  const [activeRepoFilter, setActiveRepoFilter] = useState<string | undefined>(undefined);

  const [selectedChangeId, setSelectedChangeId] = useState<string | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<ChangeDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState<boolean>(false);
  const [detailError, setDetailError] = useState<ApiError | null>(null);

  const fetchChanges = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const params = activeRepoFilter ? { repositoryId: activeRepoFilter } : undefined;
      const data = await api.get<ChangeSummary[]>('/api/v1/changes', params);
      setChanges(data);
    } catch (err) {
      if (err instanceof ApiClientError) {
        setError(err.error);
      } else {
        setError({
          code: 'NETWORK_ERROR',
          message: 'Unable to connect to the CDI API service.',
        });
      }
    } finally {
      setLoading(false);
    }
  }, [api, activeRepoFilter]);

  const fetchChangeDetail = useCallback(
    async (changeId: string) => {
      setDetailLoading(true);
      setDetailError(null);
      try {
        const detail = await api.get<ChangeDetail>(`/api/v1/changes/${changeId}`);
        setSelectedDetail(detail);
      } catch (err) {
        if (err instanceof ApiClientError) {
          setDetailError(err.error);
        } else {
          setDetailError({
            code: 'NETWORK_ERROR',
            message: 'Unable to load change details.',
          });
        }
      } finally {
        setDetailLoading(false);
      }
    },
    [api]
  );

  useEffect(() => {
    fetchChanges();
  }, [fetchChanges, tenantId, actorRole]);

  const handleSelectChange = (id: string) => {
    setSelectedChangeId(id);
    fetchChangeDetail(id);
  };

  const handleCloseDrawer = () => {
    setSelectedChangeId(null);
    setSelectedDetail(null);
    setDetailError(null);
  };

  const handleApplyRepoFilter = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = repoFilterInput.trim();
    setActiveRepoFilter(trimmed ? trimmed : undefined);
  };

  const handleClearRepoFilter = () => {
    setRepoFilterInput('');
    setActiveRepoFilter(undefined);
  };

  return (
    <div className="space-y-6 max-w-5xl">
      {/* Page Heading */}
      <div className="flex flex-col gap-1">
        <h2 className="text-2xl font-bold text-slate-100 tracking-tight">Change Decision Feed</h2>
        <p className="text-sm text-slate-400">
          Live engineering change proposals and automated decision intelligence for the active tenant.
        </p>
      </div>

      {/* Filter Bar */}
      <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 flex flex-wrap items-center justify-between gap-4">
        <form onSubmit={handleApplyRepoFilter} className="flex flex-wrap items-center gap-3 flex-1">
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-950/70 border border-slate-800 focus-within:border-indigo-500/50 flex-1 max-w-md">
            <Filter className="w-4 h-4 text-slate-500" aria-hidden="true" />
            <input
              type="text"
              placeholder="Filter by Repository ID (UUID)..."
              value={repoFilterInput}
              aria-label="Repository ID filter"
              onChange={(e) => setRepoFilterInput(e.target.value)}
              className="bg-transparent border-none text-xs text-slate-200 focus:outline-none w-full placeholder:text-slate-500"
            />
          </div>

          <button
            type="submit"
            className="px-3.5 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
          >
            Apply Filter
          </button>

          {activeRepoFilter && (
            <button
              type="button"
              onClick={handleClearRepoFilter}
              className="px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
            >
              Clear Filter
            </button>
          )}
        </form>

        <button
          onClick={() => fetchChanges()}
          aria-label="Refresh feed"
          className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
          title="Refresh Feed"
        >
          <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* Feed List Container */}
      <div className="space-y-3">
        {loading && (
          <div className="space-y-3" data-testid="feed-loading-skeleton">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-28 rounded-xl bg-slate-900/40 border border-slate-800 animate-pulse p-5" />
            ))}
          </div>
        )}

        {error && !loading && (
          <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 flex flex-col items-center justify-center text-center gap-3">
            <AlertCircle className="w-8 h-8 text-rose-400" />
            <div>
              <h3 className="font-semibold text-base">Failed to load changes</h3>
              <p className="text-xs text-rose-200 mt-1">{error.message}</p>
              {error.code && (
                <span className="inline-block mt-2 font-mono text-[11px] px-2 py-0.5 rounded bg-rose-950 border border-rose-800 text-rose-300">
                  {error.code}
                </span>
              )}
            </div>
            <button
              onClick={() => fetchChanges()}
              className="mt-2 px-4 py-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 text-rose-200 text-xs font-semibold"
            >
              Retry Request
            </button>
          </div>
        )}

        {!loading && !error && changes.length === 0 && (
          <div className="p-12 rounded-xl bg-slate-900/30 border border-slate-800 flex flex-col items-center justify-center text-center gap-3" data-testid="feed-empty-state">
            <div className="p-3 rounded-full bg-slate-800/60 text-slate-400">
              <GitPullRequest className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-200 text-base">No changes detected</h3>
              <p className="text-xs text-slate-400 mt-1 max-w-sm">
                {activeRepoFilter
                  ? `No changes match repository filter "${activeRepoFilter}".`
                  : 'There are no active or historical change proposals in this tenant workspace.'}
              </p>
            </div>
            {activeRepoFilter && (
              <button
                onClick={handleClearRepoFilter}
                className="mt-2 px-3.5 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold"
              >
                Show All Repositories
              </button>
            )}
          </div>
        )}

        {!loading && !error && changes.length > 0 && (
          <div className="space-y-3" data-testid="changes-feed-list">
            {changes.map((change) => (
              <ChangeCard
                key={change.id}
                change={change}
                isSelected={selectedChangeId === change.id}
                onSelect={handleSelectChange}
              />
            ))}
          </div>
        )}
      </div>

      {/* Detail Slide-Over Drawer */}
      {selectedChangeId && (
        <ChangeDetailDrawer
          detail={selectedDetail}
          loading={detailLoading}
          error={detailError}
          onClose={handleCloseDrawer}
          onRetry={() => selectedChangeId && fetchChangeDetail(selectedChangeId)}
        />
      )}
    </div>
  );
};