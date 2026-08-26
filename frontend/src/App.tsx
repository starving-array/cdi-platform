import React, { useState } from 'react';
import { DevSecurityProvider } from './context/DevSecurityContext';
import { AppHeader } from './components/AppHeader';
import { GitPullRequest, Search } from 'lucide-react';

export const AppContent: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'changes' | 'search'>('changes');

  return (
    <div className="flex flex-col min-h-screen">
      <AppHeader />

      <div className="flex flex-1">
        {/* Sidebar navigation */}
        <aside className="w-64 border-r border-slate-800 bg-slate-900/30 p-4 flex flex-col gap-2">
          <button
            onClick={() => setActiveTab('changes')}
            className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
              activeTab === 'changes'
                ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <GitPullRequest className="w-4 h-4" />
            <span>Change Decision Feed</span>
          </button>

          <button
            onClick={() => setActiveTab('search')}
            className={`flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${
              activeTab === 'search'
                ? 'bg-indigo-600/20 text-indigo-300 border border-indigo-500/30'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50'
            }`}
          >
            <Search className="w-4 h-4" />
            <span>Evidence Search</span>
          </button>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 p-8">
          {activeTab === 'changes' ? (
            <div className="max-w-4xl" data-testid="feed-placeholder">
              <div className="border border-slate-800 bg-slate-900/40 rounded-xl p-6">
                <h2 className="text-lg font-semibold text-slate-100 mb-2">Change Decision Feed</h2>
                <p className="text-sm text-slate-400">
                  Ready for Change Decision Feed implementation. API client and security context initialized.
                </p>
              </div>
            </div>
          ) : (
            <div className="max-w-4xl" data-testid="search-placeholder">
              <div className="border border-slate-800 bg-slate-900/40 rounded-xl p-6">
                <h2 className="text-lg font-semibold text-slate-100 mb-2">Evidence Search</h2>
                <p className="text-sm text-slate-400">
                  Ready for Evidence Search implementation. API client and security context initialized.
                </p>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <DevSecurityProvider>
      <AppContent />
    </DevSecurityProvider>
  );
};

export default App;