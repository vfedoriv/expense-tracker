import { http, HttpResponse } from 'msw'

const mockUser = {
  id: 1,
  provider: 'fake',
  providerUserId: 'test@test.com',
  email: 'test@test.com',
  displayName: 'test',
  avatarUrl: null,
  createdAt: '2026-01-01T00:00:00Z',
}

export const handlers = [
  http.get('/api/users/me', () => {
    return HttpResponse.json(mockUser)
  }),
]
