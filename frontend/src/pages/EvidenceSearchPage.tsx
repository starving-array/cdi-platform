import React, { useState, useCallback } from 'react';
import { useDevSecurity } from '../context/DevSecurityContext';
import { EvidenceRecord, SearchEvidenceResponse, ApiError } from '../types';
import { EvidenceCard } from '../components/EvidenceCard';
import { DegradedSearchBanner } from '../components/DegradedSearchBanner';
import { ApiClientError } from '../api/client';
import { Search, X, AlertCircle, RefreshCw, Layers } from 'lucide-react';

export const EvidenceSearchPage: React.FC = () => {
  const { api } = useDevSecurity();

  const [queryInput, setQueryInput] = useState<string>('');
  const [submittedQuery, setSubmittedQuery] = useState<string>('');
  const [limit, setLimit] = useState<number>(10);

  const [results, setResults] = useState<EvidenceRecord[]>([]);
  const [degraded, setDegraded] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [hasSearched, setHasSearched] = useState<boolean>(false);

  const executeSearch = useCallback(
    async (searchQuery: string, searchLimit: number) => {
      const trimmed = searchQuery.trim();
      if (!trimmed) {
        return;
      }

      setLoading(true);
      setError(null);
      setHasSearched(true);
      setSubmittedQuery(trimmed);

      try {
        const response = await api.get<SearchEvidenceResponse>('/api/v1/evidence/search', {
          query: trimmed,
          limit: searchLimit,
        });
        setResults(response.results);
        setDegraded(response.degraded);
      } catch (err) {
        if (err instanceof ApiClientError) {
          setError(err.error);
        } else {
          setError({
            code: 'NETWORK_ERROR',
            message: 'Unable to connect to the evidence search service.',
          });
        }
      } finally {
        setLoading(false);
      }
    },
    [api]
  );

  const handleFormSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    executeSearch(queryInput, limit);
  };

  const handleClear = () => {
    setQueryInput('');
    setSubmittedQuery('');
    setResults([]);
    setDegraded(false);
    setError(null);
    setHasSearched(false);
  };

  const handleLimitChange = (newLimit: number) => {
    setLimit(newLimit);
    if (hasSearched && submittedQuery) {
      executeSearch(submittedQuery, newLimit);
    }
  };

  return (
    <div className="space-y-6 max-w-5xl">
      {/* Page Heading */}
      <div className="flex flex-col gap-1">
        <h2 className="text-2xl font-bold text-slate-100 tracking-tight">Evidence Search</h2>
        <p className="text-sm text-slate-400">
          Explore deterministic evidence records, historical telemetry, and incident context for the active tenant.
        </p>
      </div>

      {/* Search Input & Limit Controls */}
      <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 flex flex-wrap items-center justify-between gap-4">
        <form onSubmit={handleFormSubmit} className="flex flex-wrap items-center gap-3 flex-1">
          <div className="flex items-center gap-2 px-3.5 py-2 rounded-lg bg-slate-950/70 border border-slate-800 focus-within:border-indigo-500/50 flex-1 max-w-lg">
            <Search className="w-4 h-4 text-slate-500 shrink-0" aria-hidden="true" />
            <input
              type="text"
              placeholder="Search evidence titles, logs, and incident records..."
              value={queryInput}
              aria-label="Search query"
              onChange={(e) => setQueryInput(e.target.value)}
              className="bg-transparent border-none text-xs text-slate-200 focus:outline-none w-full placeholder:text-slate-500"
            />
            {queryInput && (
              <button
                type="button"
                onClick={handleClear}
                aria-label="Clear search input"
                className="p-1 rounded text-slate-500 hover:text-slate-300 transition-colors"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <button
            type="submit"
            disabled={!queryInput.trim()}
            className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed text-white text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-indigo-500/50"
          >
            Search
          </button>
        </form>

        {/* Limit Selector */}
        <div className="flex items-center gap-2 text-xs text-slate-400">
          <span>Limit:</span>
          <select
            aria-label="Result limit"
            value={limit}
            onChange={(e) => handleLimitChange(Number(e.target.value))}
            className="bg-slate-950 border border-slate-800 text-slate-200 rounded-md px-2.5 py-1.5 focus:outline-none focus:border-indigo-500 cursor-pointer"
          >
            <option value={10}>10</option>
            <option value={25}>25</option>
            <option value={50}>50</option>
          </select>
        </div>
      </div>

      {/* Degraded Search Banner (when degraded === true) */}
      {degraded && <DegradedSearchBanner />}

      {/* Main Results / States Area */}
      <div className="space-y-4">
        {/* Loading Skeletons */}
        {loading && (
          <div className="space-y-3" data-testid="search-loading-skeleton">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-32 rounded-xl bg-slate-900/40 border border-slate-800 animate-pulse p-5" />
            ))}
          </div>
        )}

        {/* Error State */}
        {error && !loading && (
          <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/20 text-rose-300 flex flex-col items-center justify-center text-center gap-3">
            <AlertCircle className="w-8 h-8 text-rose-400" />
            <div>
              <h3 className="font-semibold text-base">Search Request Failed</h3>
              <p className="text-xs text-rose-200 mt-1">{error.message}</p>
              {error.code && (
                <span className="inline-block mt-2 font-mono text-[11px] px-2 py-0.5 rounded bg-rose-950 border border-rose-800 text-rose-300">
                  {error.code}
                </span>
              )}
            </div>
            {submittedQuery && (
              <button
                onClick={() => executeSearch(submittedQuery, limit)}
                className="mt-2 px-4 py-1.5 rounded-lg bg-rose-500/20 hover:bg-rose-500/30 text-rose-200 text-xs font-semibold flex items-center gap-1.5"
              >
                <RefreshCw className="w-3.5 h-3.5" />
                <span>Retry Search</span>
              </button>
            )}
          </div>
        )}

        {/* Initial Untouched State */}
        {!hasSearched && !loading && !error && (
          <div
            className="p-16 rounded-xl bg-slate-900/30 border border-slate-800 flex flex-col items-center justify-center text-center gap-3"
            data-testid="search-initial-state"
          >
            <div className="p-3.5 rounded-full bg-slate-800/60 text-slate-400">
              <Search className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-200 text-base">Explore Evidence Records</h3>
              <p className="text-xs text-slate-400 mt-1 max-w-md">
                Enter a query above to search evidence records in this workspace.
              </p>
            </div>
          </div>
        )}

        {/* Empty Search Results */}
        {hasSearched && !loading && !error && results.length === 0 && (
          <div
            className="p-12 rounded-xl bg-slate-900/30 border border-slate-800 flex flex-col items-center justify-center text-center gap-3"
            data-testid="search-empty-state"
          >
            <div className="p-3 rounded-full bg-slate-800/60 text-slate-400">
              <Layers className="w-6 h-6" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-200 text-base">No evidence records matched your search query.</h3>
              <p className="text-xs text-slate-400 mt-1">
                Try refining your search terms or checking a different keyword.
              </p>
            </div>
          </div>
        )}

        {/* Results List */}
        {hasSearched && !loading && !error && results.length > 0 && (
          <div className="space-y-3" data-testid="evidence-results-list">
            <div className="text-xs text-slate-400 font-medium px-1 flex items-center justify-between">
              <span>Showing {results.length} evidence record(s) for &ldquo;{submittedQuery}&rdquo;</span>
            </div>
            {results.map((record) => (
              <EvidenceCard key={record.id} record={record} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};