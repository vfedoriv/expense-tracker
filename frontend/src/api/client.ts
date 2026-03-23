import type { ApiError } from '../types';

const BASE_URL = '/api';

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
      headers: getHeaders(),
      body: body ? JSON.stringify(body) : undefined,
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },

  async put<T>(path: string, body?: unknown): Promise<T> {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'PUT',
      headers: getHeaders(),
      body: body ? JSON.stringify(body) : undefined,
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },

  async delete<T>(path: string): Promise<T> {
    const response = await fetch(`${BASE_URL}${path}`, {
      method: 'DELETE',
      headers: getHeaders(),
      credentials: 'include',
    });
    return handleResponse<T>(response);
  },
};
