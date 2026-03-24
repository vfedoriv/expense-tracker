import type { ApiError } from '../types';

const BASE_URL = '/api';

/**
 * Reads the XSRF-TOKEN cookie value from document.cookie.
 * Spring Security's CookieCsrfTokenRepository sets this cookie (httpOnly=false)
 * so the frontend can read it and send it back as the X-XSRF-TOKEN header.
 */
export function getCsrfToken(): string | null {
  const match = document.cookie.split('; ').find((row) => row.startsWith('XSRF-TOKEN='));
  if (!match) return null;
  return decodeURIComponent(match.split('=')[1]);
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = await response.json();
      if (body.message) {
        message = body.message;
      } else if (body.error) {
        message = body.error;
      }
    } catch {
      // ignore parse errors
    }
    const error: ApiError = { message, status: response.status };
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

function getHeaders(): HeadersInit {
  return {
    'Content-Type': 'application/json',
  };
}

/**
 * Returns headers for mutation requests (POST/PUT/DELETE).
 * Includes the X-XSRF-TOKEN header when the CSRF cookie is available.
 */
function getMutationHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  const csrfToken = getCsrfToken();
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
  return headers;
}

export const apiClient = {
  async get<T>(path: string): Promise<T> {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'GET',
      headers: getHeaders(),
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },

  async post<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: getMutationHeaders(),
      body: body ? JSON.stringify(body) : undefined,
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },

  async put<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'PUT',
      headers: getMutationHeaders(),
      body: body ? JSON.stringify(body) : undefined,
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },

  async delete<T>(path: string): Promise<T> {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'DELETE',
      headers: getMutationHeaders(),
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },
};
