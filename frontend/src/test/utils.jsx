import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';
import AppRoutes from '../AppRoutes';
import { AuthProvider } from '../auth/AuthContext';

/** Affiche l'application complète à l'adresse donnée (routeur en mémoire, pas de navigateur). */
export function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </MemoryRouter>,
  );
}

/**
 * Remplace fetch par une fausse API : handlers = { 'POST /api/auth/login': { status, body } | (init) => ... }.
 * Toute requête non prévue répond 404. Renvoie le mock pour inspecter les appels.
 */
export function mockApi(handlers) {
  const fetchMock = vi.fn(async (url, init = {}) => {
    const key = `${init.method ?? 'GET'} ${new URL(url).pathname}`;
    const handler = handlers[key];
    const { status, body } = typeof handler === 'function'
      ? handler(init)
      : handler ?? { status: 404, body: { status: 404, message: `Non simulé : ${key}` } };
    return {
      ok: status >= 200 && status < 300,
      status,
      text: async () => (body === undefined ? '' : JSON.stringify(body)),
    };
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export const CLIENT = {
  id: 2, name: 'Camille Client', email: 'camille@test.local', role: 'CLIENT', active: true,
  createdAt: '2026-11-11T10:00:00Z',
};
export const ADMIN = {
  id: 1, name: 'Alice Admin', email: 'alice@test.local', role: 'ADMIN', active: true,
  createdAt: '2026-11-01T10:00:00Z',
};
