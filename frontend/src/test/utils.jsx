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
 * Remplace fetch par une fausse API :
 * handlers = { 'POST /api/auth/login': { status, body } | (init, url) => ({ status, body }) }.
 * La clé ne contient que le chemin (sans query string) ; un handler fonction reçoit l'URL complète.
 * Toute requête non prévue répond 404. Renvoie le mock pour inspecter les appels.
 */
export function mockApi(handlers) {
  const fetchMock = vi.fn(async (url, init = {}) => {
    const key = `${init.method ?? 'GET'} ${new URL(url).pathname}`;
    const handler = handlers[key];
    const { status, body } = typeof handler === 'function'
      ? handler(init, new URL(url))
      : handler ?? { status: 404, body: { status: 404, message: `Non simulé : ${key}` } };
    return {
      ok: status >= 200 && status < 300,
      status,
      text: async () => (body === undefined ? '' : JSON.stringify(body)),
      blob: async () => (body instanceof Blob ? body : new Blob([body === undefined ? '' : String(body)])),
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
export const AGENT = {
  id: 3, name: 'Arthur Agent', email: 'arthur@test.local', role: 'AGENT', active: true,
  createdAt: '2026-11-05T10:00:00Z',
};

export const FACTURATION = { id: 1, name: 'Facturation' };

/** ClaimDetail au format du claim-service (Dtos.ClaimDetail), réclamation du client CLIENT. */
export function claimDetail(overrides = {}) {
  return {
    id: 42,
    title: 'Facture erronée',
    description: 'Montant facturé en double.',
    status: 'NEW',
    clientId: CLIENT.id,
    clientEmail: CLIENT.email,
    category: null,
    service: null,
    assignedAgent: null,
    classificationSource: null,
    confidenceScore: null,
    createdAt: '2026-11-10T09:00:00Z',
    updatedAt: '2026-11-10T09:00:00Z',
    history: [
      { oldStatus: null, newStatus: 'NEW', authorId: CLIENT.id, authorRole: 'CLIENT', note: 'Réclamation soumise',
        changedAt: '2026-11-10T09:00:00Z' },
    ],
    comments: [],
    attachments: [],
    ...overrides,
  };
}

/** Réclamation affectée à AGENT dans le service Facturation, au statut donné. */
export function assignedClaim(status, overrides = {}) {
  return claimDetail({
    status,
    category: FACTURATION,
    service: FACTURATION,
    assignedAgent: { id: AGENT.id, name: AGENT.name },
    classificationSource: 'MANUAL',
    ...overrides,
  });
}

/** Réponses des référentiels lues par les agents et les admins sur la page de détail. */
export const REFERENCE_HANDLERS = {
  'GET /api/claims/categories': {
    status: 200,
    body: [{ id: 1, name: 'Facturation', description: null, service: FACTURATION }],
  },
  'GET /api/claims/services': { status: 200, body: [{ ...FACTURATION, description: null }] },
  'GET /api/claims/services/1/agents': {
    status: 200,
    body: [{ agentId: AGENT.id, agentName: AGENT.name, serviceId: 1 }],
  },
};
