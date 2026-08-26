import { ApiError } from '../types';

export interface ClientConfig {
  baseUrl?: string;
  tenantId: string;
  actorId: string;
  actorRole: string;
}

export class ApiClientError extends Error {
  constructor(
    public readonly status: number,
    public readonly error: ApiError
  ) {
    super(`[${error.code}] ${error.message}`);
    this.name = 'ApiClientError';
  }
}

export class ApiClient {
  private baseUrl: string;

  constructor(private config: ClientConfig) {
    this.baseUrl = (config.baseUrl || 'http://localhost:8080').replace(/\/$/, '');
  }

  public updateConfig(newConfig: Partial<ClientConfig>): void {
    this.config = { ...this.config, ...newConfig };
    if (newConfig.baseUrl !== undefined) {
      this.baseUrl = (newConfig.baseUrl || 'http://localhost:8080').replace(/\/$/, '');
    }
  }

  public async get<T>(path: string, params?: Record<string, string | number | undefined>): Promise<T> {
    const url = new URL(`${this.baseUrl}${path}`);
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) {
          url.searchParams.append(k, String(v));
        }
      });
    }

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-Tenant-Id': this.config.tenantId,
      'X-Actor-Id': this.config.actorId,
      'X-Actor-Role': this.config.actorRole,
    };

    const response = await fetch(url.toString(), {
      method: 'GET',
      headers,
    });

    if (!response.ok) {
      let errPayload: ApiError;
      try {
        errPayload = await response.json();
      } catch {
        errPayload = {
          code: 'HTTP_ERROR',
          message: response.statusText || 'Unknown network error',
        };
      }
      throw new ApiClientError(response.status, errPayload);
    }

    return response.json() as Promise<T>;
  }
}