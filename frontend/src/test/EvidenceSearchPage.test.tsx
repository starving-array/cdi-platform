import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EvidenceSearchPage } from '../pages/EvidenceSearchPage';
import { DevSecurityProvider, useDevSecurity } from '../context/DevSecurityContext';
import { EvidenceRecord, SearchEvidenceResponse } from '../types';

const mockEvidenceList: EvidenceRecord[] = [
  {
    id: 'ev-1',
    analysisRunId: 'run-101',
    source: 'INCIDENT',
    origin: 'RETRIEVED',
    title: 'INC-404 Database Connection Saturation',
    content: 'Root cause was unbounded connection leak in payment worker pool.',
    capturedAt: '2026-08-14T12:00:00Z',
  },
  {
    id: 'ev-2',
    analysisRunId: 'run-102',
    source: 'COMMIT',
    origin: 'SYSTEM',
    title: 'Fix connection leak in worker',
    content: 'Closed idle sessions after 30s timeout.',
    capturedAt: '2026-08-14T13:00:00Z',
  },
];

describe('EvidenceSearchPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders initial untouched state prompt before search is run', () => {
    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    expect(screen.getByTestId('search-initial-state')).toBeInTheDocument();
    expect(
      screen.getByText('Enter a query above to search evidence records in this workspace.')
    ).toBeInTheDocument();
  });

  it('executes search on button submit and renders result cards with source and origin labels', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: false,
      results: mockEvidenceList,
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'connection leak' } });

    const searchBtn = screen.getByRole('button', { name: 'Search' });
    fireEvent.click(searchBtn);

    await waitFor(() => {
      expect(screen.getByText('INC-404 Database Connection Saturation')).toBeInTheDocument();
      expect(screen.getByText('Fix connection leak in worker')).toBeInTheDocument();
    });

    expect(screen.getByText('Incident')).toBeInTheDocument();
    expect(screen.getByText('Origin: RETRIEVED')).toBeInTheDocument();
    expect(screen.getByText('Commit')).toBeInTheDocument();
    expect(screen.getByText('Origin: SYSTEM')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('submits search on Enter key', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: false,
      results: [mockEvidenceList[0]],
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'database' } });
    fireEvent.submit(input.closest('form')!);

    await waitFor(() => {
      expect(screen.getByText('INC-404 Database Connection Saturation')).toBeInTheDocument();
    });
  });

  it('displays empty state when search returns zero results', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: false,
      results: [],
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'nonexistent query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(screen.getByTestId('search-empty-state')).toBeInTheDocument();
      expect(
        screen.getByText('No evidence records matched your search query.')
      ).toBeInTheDocument();
    });
  });

  it('displays DegradedSearchBanner when response has degraded=true', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: true,
      results: [],
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'timeout query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(
        screen.getByText(
          'Evidence search is operating in degraded fallback mode. Live retrieval was unavailable; partial or empty results may be returned.'
        )
      ).toBeInTheDocument();
    });
  });

  it('handles canonical UNAUTHORIZED role rejection cleanly with error display and retry', async () => {
    const mockFetch = vi
      .fn()
      .mockResolvedValueOnce({
        ok: false,
        status: 403,
        json: async () => ({
          code: 'UNAUTHORIZED',
          message: 'Active actor is not authorized to search evidence records.',
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          degraded: false,
          results: mockEvidenceList,
        }),
      });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'unauthorized test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(screen.getByText('Search Request Failed')).toBeInTheDocument();
      expect(screen.getByText('UNAUTHORIZED')).toBeInTheDocument();
      expect(
        screen.getByText('Active actor is not authorized to search evidence records.')
      ).toBeInTheDocument();
    });

    const retryBtn = screen.getByRole('button', { name: /Retry Search/i });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getByText('INC-404 Database Connection Saturation')).toBeInTheDocument();
    });
  });

  it('clears input and resets state on clear button click', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: false,
      results: mockEvidenceList,
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(screen.getByText('INC-404 Database Connection Saturation')).toBeInTheDocument();
    });

    const clearBtn = screen.getByLabelText('Clear search input');
    fireEvent.click(clearBtn);

    expect(input.value).toBe('');
    expect(screen.getByTestId('search-initial-state')).toBeInTheDocument();
  });

  it('allows changing result limit and re-queries', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: false,
      results: mockEvidenceList,
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    render(
      <DevSecurityProvider>
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      expect(screen.getByText('INC-404 Database Connection Saturation')).toBeInTheDocument();
    });

    const limitSelect = screen.getByLabelText('Result limit');
    fireEvent.change(limitSelect, { target: { value: '25' } });

    await waitFor(() => {
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('limit=25'),
        expect.anything()
      );
    });
  });

  it('sends updated tenant header when dev context changes', async () => {
    const mockResponse: SearchEvidenceResponse = {
      degraded: false,
      results: mockEvidenceList,
    };
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockResponse,
    });
    global.fetch = mockFetch;

    const TenantChanger = () => {
      const { setTenantId } = useDevSecurity();
      return <button onClick={() => setTenantId('tenant-xyz-999')}>Change Tenant</button>;
    };

    render(
      <DevSecurityProvider>
        <TenantChanger />
        <EvidenceSearchPage />
      </DevSecurityProvider>
    );

    fireEvent.click(screen.getByText('Change Tenant'));

    const input = screen.getByLabelText('Search query');
    fireEvent.change(input, { target: { value: 'query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));

    await waitFor(() => {
      const lastCall = mockFetch.mock.calls[mockFetch.mock.calls.length - 1];
      expect(lastCall[1].headers['X-Tenant-Id']).toBe('tenant-xyz-999');
    });
  });
});