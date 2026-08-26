import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ChangesFeedPage } from '../pages/ChangesFeedPage';
import { DevSecurityProvider, useDevSecurity } from '../context/DevSecurityContext';
import { ChangeSummary, ChangeDetail } from '../types';

const mockChanges: ChangeSummary[] = [
  {
    id: 'change-1',
    repositoryId: 'repo-1',
    providerChangeId: 'PR-101',
    title: 'Migrate to resilient database pooling',
    description: 'Replaces generic driver with HikariCP',
    author: 'alice',
    sourceBranch: 'feature/hikari',
    targetBranch: 'main',
    latestCommitSha: 'abc123456789',
    status: 'OPEN',
    createdAt: '2026-08-14T12:00:00Z',
    updatedAt: '2026-08-14T12:10:00Z',
  },
  {
    id: 'change-2',
    repositoryId: 'repo-1',
    providerChangeId: 'PR-102',
    title: 'Update policy enforcement rules',
    description: 'Adds mandatory review for tier-0 services',
    author: 'bob',
    sourceBranch: 'policy-update',
    targetBranch: 'main',
    latestCommitSha: 'def987654321',
    status: 'MERGED',
    createdAt: '2026-08-14T10:00:00Z',
    updatedAt: '2026-08-14T11:00:00Z',
  },
];

const mockDetail: ChangeDetail = {
  change: mockChanges[0],
  analysisRuns: [
    {
      id: 'run-1',
      commitSha: 'abc123456789',
      status: 'COMPLETED',
      createdAt: '2026-08-14T12:01:00Z',
      completedAt: '2026-08-14T12:03:00Z',
    },
  ],
  latestRisk: {
    id: 'risk-1',
    overallScore: 28,
    riskLevel: 'LOW',
    assessmentVersion: 'v1',
  },
  latestDecision: {
    id: 'dec-1',
    outcome: 'APPROVE',
    policyVersion: 'v1',
    overridden: false,
  },
};

describe('ChangesFeedPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders initial loading skeleton then displays changes feed', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockChanges,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    expect(screen.getByTestId('feed-loading-skeleton')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText('Migrate to resilient database pooling')).toBeInTheDocument();
      expect(screen.getByText('Update policy enforcement rules')).toBeInTheDocument();
    });

    expect(screen.getByText('Open')).toBeInTheDocument();
    expect(screen.getByText('Merged')).toBeInTheDocument();
  });

  it('displays empty state message when feed returns zero changes', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [],
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('feed-empty-state')).toBeInTheDocument();
      expect(screen.getByText('No changes detected')).toBeInTheDocument();
    });
  });

  it('applies repository filter on form submit and handles clear filter', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [mockChanges[0]],
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Migrate to resilient database pooling')).toBeInTheDocument();
    });

    const repoInput = screen.getByLabelText('Repository ID filter');
    fireEvent.change(repoInput, { target: { value: 'repo-uuid-123' } });

    const applyBtn = screen.getByText('Apply Filter');
    fireEvent.click(applyBtn);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/changes?repositoryId=repo-uuid-123'),
        expect.anything()
      );
    });

    const clearBtn = screen.getByText('Clear Filter');
    fireEvent.click(clearBtn);

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringMatching(/\/api\/v1\/changes$/),
        expect.anything()
      );
    });
  });

  it('handles API errors gracefully and allows retry', async () => {
    const mockFetch = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: async () => ({
          code: 'UNAUTHORIZED',
          message: 'Active actor is unauthorized.',
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockChanges,
      });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Failed to load changes')).toBeInTheDocument();
      expect(screen.getByText('UNAUTHORIZED')).toBeInTheDocument();
    });

    const retryBtn = screen.getByText('Retry Request');
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('Migrate to resilient database pooling')).toBeInTheDocument();
    });
  });

  it('opens change detail drawer with telemetry when card is clicked and closes on Escape', async () => {
    const mockFetch = vi.fn().mockImplementation(async (url: string) => {
      if (url.includes('/api/v1/changes/change-1')) {
        return {
          ok: true,
          json: async () => mockDetail,
        };
      }
      return {
        ok: true,
        json: async () => mockChanges,
      };
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Migrate to resilient database pooling')).toBeInTheDocument();
    });

    const card = screen.getByLabelText(/View details for Migrate to resilient database pooling/i);
    fireEvent.click(card);

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
      expect(screen.getByText('Approved')).toBeInTheDocument();
      expect(screen.getByText('28/100')).toBeInTheDocument();
      expect(screen.getByText('Low Risk')).toBeInTheDocument();
      expect(screen.getByText('Analysis Run History (1)')).toBeInTheDocument();
    });

    // Close on Escape
    fireEvent.keyDown(window, { key: 'Escape', code: 'Escape' });

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('closes detail drawer on close button click', async () => {
    const mockFetch = vi.fn().mockImplementation(async (url: string) => {
      if (url.includes('/api/v1/changes/change-1')) {
        return {
          ok: true,
          json: async () => mockDetail,
        };
      }
      return {
        ok: true,
        json: async () => mockChanges,
      };
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Migrate to resilient database pooling')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByLabelText(/View details for Migrate to resilient database pooling/i));

    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    const closeBtn = screen.getByLabelText('Close inspector');
    fireEvent.click(closeBtn);

    await waitFor(() => {
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });

  it('re-fetches changes when tenant or role changes in dev context', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockChanges,
    });
    global.fetch = mockFetch;

    const TenantSwitcher = () => {
      const { setTenantId } = useDevSecurity();
      return <button onClick={() => setTenantId('new-tenant-uuid')}>Switch Tenant</button>;
    };

    render(
      <DevSecurityProvider>
        <TenantSwitcher />
        <ChangesFeedPage />
      </DevSecurityProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Migrate to resilient database pooling')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Switch Tenant'));

    await waitFor(() => {
      const calls = mockFetch.mock.calls;
      const lastCallHeaders = calls[calls.length - 1][1].headers;
      expect(lastCallHeaders['X-Tenant-Id']).toBe('new-tenant-uuid');
    });
  });
});