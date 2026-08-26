import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiClient, ApiClientError } from '../api/client';

describe('ApiClient', () => {
  const config = {
    baseUrl: 'http://localhost:8080',
    tenantId: '11111111-1111-1111-1111-111111111111',
    actorId: 'test-user',
    actorRole: 'ENGINEER',
  };

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('sends X-Tenant-Id, X-Actor-Id, and X-Actor-Role headers on GET', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => [{ id: 'change-1', title: 'Test change' }],
    });
    global.fetch = mockFetch;

    const client = new ApiClient(config);
    const data = await client.get('/api/v1/changes', { repositoryId: 'repo-1' });

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [url, options] = mockFetch.mock.calls[0];

    expect(url).toBe('http://localhost:8080/api/v1/changes?repositoryId=repo-1');
    expect(options.headers['X-Tenant-Id']).toBe('11111111-1111-1111-1111-111111111111');
    expect(options.headers['X-Actor-Id']).toBe('test-user');
    expect(options.headers['X-Actor-Role']).toBe('ENGINEER');
    expect(data).toEqual([{ id: 'change-1', title: 'Test change' }]);
  });

  it('throws ApiClientError with error payload on 4xx/5xx responses', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({
        code: 'REPOSITORY_NOT_FOUND',
        message: 'Repository not found',
        details: {},
      }),
    });
    global.fetch = mockFetch;

    const client = new ApiClient(config);

    await expect(client.get('/api/v1/changes')).rejects.toThrow(ApiClientError);
    await expect(client.get('/api/v1/changes')).rejects.toMatchObject({
      status: 404,
      error: {
        code: 'REPOSITORY_NOT_FOUND',
        message: 'Repository not found',
      },
    });
  });
});