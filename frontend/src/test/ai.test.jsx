import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { tokenStorage } from '../auth/tokenStorage';
import { ADMIN, AGENT, CLIENT, claimDetail, mockApi, renderApp } from './utils';

const me = (user) => ({ 'GET /api/auth/me': { status: 200, body: user } });
const LIVRAISON = { id: 3, name: 'Livraison' };
const FACTURATION = { id: 1, name: 'Facturation' };

const REFERENCES = {
  'GET /api/claims/categories': {
    status: 200,
    body: [
      { id: 1, name: 'Facturation', description: null, service: FACTURATION },
      { id: 3, name: 'Livraison', description: null, service: LIVRAISON },
    ],
  },
  'GET /api/claims/services': {
    status: 200,
    body: [{ ...FACTURATION, description: null }, { ...LIVRAISON, description: null }],
  },
  'GET /api/claims/services/1/agents': { status: 200, body: [] },
  'GET /api/claims/services/3/agents': { status: 200, body: [] },
};

function prediction(overrides = {}) {
  return {
    outcome: 'APPLIED', failureReason: null, predictedCategory: 'Livraison', predictedService: 'Livraison',
    confidence: 0.9969, modelVersion: '1.0.0', threshold: 0.57, predictedAt: '2026-11-15T10:00:00Z',
    decision: null, finalCategory: null, finalService: null, decidedByRole: null, decidedByName: null,
    decidedAt: null,
    ...overrides,
  };
}

/** Réclamation classée automatiquement (Livraison, 99 %), telle que la voit un agent ou un admin. */
function aiClaim(overrides = {}) {
  return claimDetail({
    title: 'Colis',
    description: 'Mon colis n’est jamais arrivé',
    status: 'CLASSIFIED',
    category: LIVRAISON,
    service: LIVRAISON,
    classificationSource: 'AI',
    confidenceScore: 0.9969,
    autoClassified: true,
    history: [
      { oldStatus: null, newStatus: 'NEW', authorId: CLIENT.id, authorRole: 'CLIENT', note: 'Réclamation soumise',
        changedAt: '2026-11-15T10:00:00Z' },
      { oldStatus: 'NEW', newStatus: 'CLASSIFIED', authorId: 0, authorRole: 'SYSTEM',
        note: 'Classement automatique : Livraison (99 %)', changedAt: '2026-11-15T10:00:01Z' },
    ],
    aiPrediction: prediction(),
    ...overrides,
  });
}

const callsTo = (fetchMock, method, path) =>
  fetchMock.mock.calls.filter(([url, init = {}]) => (init.method ?? 'GET') === method && new URL(url).pathname === path);

function page(content) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

/** Réclamation classée par l'IA telle que la reçoit un CLIENT depuis l'étape 7c du claim-service. */
function clientView(overrides = {}) {
  return claimDetail({
    title: 'Colis',
    description: 'Mon colis n’est jamais arrivé',
    status: 'CLASSIFIED',
    category: LIVRAISON,
    service: LIVRAISON,
    autoClassified: true,
    classificationSource: undefined,
    confidenceScore: undefined,
    history: [
      { oldStatus: null, newStatus: 'NEW', authorId: CLIENT.id, authorRole: 'CLIENT', note: 'Réclamation soumise',
        changedAt: '2026-11-15T10:00:00Z' },
      { oldStatus: 'NEW', newStatus: 'CLASSIFIED', authorId: 0, authorRole: 'SYSTEM',
        note: 'Classement automatique : Livraison', changedAt: '2026-11-15T10:00:01Z' },
    ],
    ...overrides,
  });
}

describe('Classification IA : affichage selon le rôle', () => {
  it('le badge client se base sur autoClassified (classificationSource n’est plus envoyé)', async () => {
    tokenStorage.set('jeton-client');
    mockApi({ ...me(CLIENT), 'GET /api/claims/42': { status: 200, body: clientView() } });
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Colis' })).toBeInTheDocument();
    expect(screen.getByText('Classée automatiquement')).toBeInTheDocument();
    expect(screen.getByText('Classement automatique : Livraison')).toBeInTheDocument();
    expect(screen.getByText(/Système/)).toBeInTheDocument();
    expect(screen.queryByText(/confiance/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
    expect(screen.queryByText(/v1\.0\.0|modèle/i)).not.toBeInTheDocument();
  });

  it('pas de badge client si autoClassified est faux (classement manuel)', async () => {
    tokenStorage.set('jeton-client');
    mockApi({ ...me(CLIENT), 'GET /api/claims/42': { status: 200, body: clientView({ autoClassified: false }) } });
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Colis' })).toBeInTheDocument();
    expect(screen.queryByText('Classée automatiquement')).not.toBeInTheDocument();
  });

  it('défense en profondeur : même si des données du modèle arrivaient, le client ne les voit pas', async () => {
    tokenStorage.set('jeton-client');
    // Même si une prédiction était renvoyée par erreur, l'interface client ne l'affiche pas.
    mockApi({ ...me(CLIENT), 'GET /api/claims/42': { status: 200, body: aiClaim() } });
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Colis' })).toBeInTheDocument();
    expect(screen.getByText('Classée automatiquement')).toBeInTheDocument();
    expect(screen.getAllByText('Livraison').length).toBeGreaterThanOrEqual(2); // catégorie + service
    expect(screen.queryByRole('heading', { name: 'Classification' })).not.toBeInTheDocument();
    expect(screen.queryByText(/confiance/i)).not.toBeInTheDocument();
    expect(screen.queryByText('99 %')).not.toBeInTheDocument();
    expect(screen.queryByText('v1.0.0')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Corriger' })).not.toBeInTheDocument();
  });

  it('l’agent voit la proposition, la confiance avec son niveau et la version du modèle', async () => {
    tokenStorage.set('jeton-agent');
    mockApi({ ...me(AGENT), ...REFERENCES, 'GET /api/claims/42': { status: 200, body: aiClaim() } });
    renderApp('/reclamations/42');

    const panel = (await screen.findByRole('heading', { name: 'Classification' })).closest('section');
    expect(within(panel).getByText('Classement automatique (IA)')).toBeInTheDocument();
    expect(within(panel).getByText('Livraison')).toBeInTheDocument();
    expect(within(panel).getByText('99 %')).toBeInTheDocument();
    expect(within(panel).getByText('Confiance élevée')).toBeInTheDocument();
    expect(within(panel).getByRole('meter', { name: 'Confiance de l’IA' })).toHaveAttribute('aria-valuenow', '99');
    expect(within(panel).getByText('v1.0.0')).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Confirmer' })).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Corriger' })).toBeInTheDocument();
    // Le formulaire de correction n'est ouvert que par « Corriger ».
    expect(screen.queryByRole('heading', { name: 'Corriger la classification' })).not.toBeInTheDocument();
  });

  it('libellé « Système » pour l’auteur SYSTEM dans l’historique et les commentaires', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN), ...REFERENCES,
      'GET /api/claims/42': {
        status: 200,
        body: aiClaim({
          comments: [{ id: 5, content: 'Classement automatique non appliqué : confiance insuffisante.', internal: true,
            authorId: 0, authorRole: 'SYSTEM', createdAt: '2026-11-15T10:00:01Z' }],
        }),
      },
    });
    renderApp('/reclamations/42');

    const history = await screen.findByRole('list', { name: 'Historique des statuts' });
    expect(within(history).getByText(/Système/)).toBeInTheDocument();
    expect(screen.getAllByText(/Système/).length).toBeGreaterThanOrEqual(2); // historique + commentaire
    expect(screen.queryByText(/SYSTEM/)).not.toBeInTheDocument();
  });

  it('classement non appliqué : explication claire, pas de Confirmer, formulaire de tri pour l’admin', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN), ...REFERENCES,
      'GET /api/claims/42': {
        status: 200,
        body: claimDetail({
          title: 'bonjour',
          aiPrediction: prediction({ outcome: 'LOW_CONFIDENCE', predictedCategory: 'Support technique',
            predictedService: 'Support technique', confidence: 0.5223 }),
        }),
      },
    });
    renderApp('/reclamations/42');

    const panel = (await screen.findByRole('heading', { name: 'Classification' })).closest('section');
    expect(within(panel).getByText(/Classement automatique non appliqué/)).toHaveTextContent(
      'Confiance trop faible : la proposition de l’IA n’a pas été retenue. La réclamation est à classer manuellement.',
    );
    expect(within(panel).getByText('Confiance faible')).toBeInTheDocument();
    expect(within(panel).getByText('52 %')).toBeInTheDocument();
    expect(within(panel).queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Classer la réclamation' })).toBeInTheDocument();
  });

  it('service IA indisponible : message sans détail technique', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN), ...REFERENCES,
      'GET /api/claims/42': {
        status: 200,
        body: claimDetail({ aiPrediction: prediction({ outcome: 'UNAVAILABLE', failureReason: 'TIMEOUT',
          predictedCategory: null, predictedService: null, confidence: null, modelVersion: null }) }),
      },
    });
    renderApp('/reclamations/42');

    expect(await screen.findByText(/Le service IA était indisponible lors de la soumission/)).toBeInTheDocument();
    expect(screen.queryByText(/TIMEOUT/)).not.toBeInTheDocument();
  });
});

describe('Confirmer et Corriger', () => {
  it.each([
    ['déjà confirmée', aiClaim({ aiPrediction: prediction({ decision: 'CONFIRMED', finalCategory: 'Livraison',
      finalService: 'Livraison', decidedByRole: 'AGENT', decidedAt: '2026-11-15T11:00:00Z' }) })],
    ['classée manuellement', aiClaim({ classificationSource: 'MANUAL', confidenceScore: null, autoClassified: false,
      aiPrediction: null })],
    ['résolue', aiClaim({ status: 'RESOLVED' })],
  ])('boutons masqués pour une réclamation %s', async (_, claim) => {
    tokenStorage.set('jeton-agent');
    mockApi({ ...me(AGENT), ...REFERENCES, 'GET /api/claims/42': { status: 200, body: claim } });
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Classification' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Corriger' })).not.toBeInTheDocument();
  });

  it('affiche la décision prise (confirmée, par qui, quand)', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN), ...REFERENCES,
      'GET /api/claims/42': {
        status: 200,
        body: aiClaim({
          classificationSource: 'MANUAL', autoClassified: false, category: FACTURATION, service: FACTURATION,
          aiPrediction: prediction({ decision: 'CORRECTED', finalCategory: 'Facturation', finalService: 'Facturation',
            decidedByRole: 'AGENT', decidedByName: 'Arthur Agent', decidedAt: '2026-11-15T11:00:00Z' }),
        }),
      },
    });
    renderApp('/reclamations/42');

    expect(await screen.findByText('Corrigée')).toBeInTheDocument();
    expect(screen.getByText(/par Arthur Agent le .* : Facturation \(service Facturation\)/)).toBeInTheDocument();
    expect(screen.queryByText(/par un agent/)).not.toBeInTheDocument();
  });

  it('le seuil vient de l’API : il fixe le niveau de confiance et est affiché', async () => {
    tokenStorage.set('jeton-agent');
    // Modèle plus exigeant (seuil 0,90) : 88 % devient une confiance faible.
    mockApi({
      ...me(AGENT), ...REFERENCES,
      'GET /api/claims/42': { status: 200, body: aiClaim({ aiPrediction: prediction({ confidence: 0.88, threshold: 0.9 }) }) },
    });
    renderApp('/reclamations/42');

    const panel = (await screen.findByRole('heading', { name: 'Classification' })).closest('section');
    expect(within(panel).getByText('88 %')).toBeInTheDocument();
    expect(within(panel).getByText('Confiance faible')).toBeInTheDocument();
    expect(within(panel).getByText('Seuil du modèle').nextSibling).toHaveTextContent(/^90 %$/);
    expect(within(panel).queryByText(/valeur par défaut/)).not.toBeInTheDocument();
  });

  it('seuil absent de l’API (ancienne prédiction) : valeur de repli signalée', async () => {
    tokenStorage.set('jeton-agent');
    mockApi({
      ...me(AGENT), ...REFERENCES,
      'GET /api/claims/42': { status: 200, body: aiClaim({ aiPrediction: prediction({ confidence: 0.7, threshold: null }) }) },
    });
    renderApp('/reclamations/42');

    const panel = (await screen.findByRole('heading', { name: 'Classification' })).closest('section');
    expect(within(panel).getByText('Seuil du modèle').nextSibling).toHaveTextContent('57 % (valeur par défaut)');
    expect(within(panel).getByText('Confiance moyenne')).toBeInTheDocument();
  });

  it('nom de l’auteur affiché ; repli sur le rôle quand le nom est inconnu (admin)', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN), ...REFERENCES,
      'GET /api/claims/42': {
        status: 200,
        body: aiClaim({ aiPrediction: prediction({ decision: 'CONFIRMED', finalCategory: 'Livraison',
          finalService: 'Livraison', decidedByRole: 'ADMIN', decidedByName: null, decidedAt: '2026-11-15T11:00:00Z' }) }),
      },
    });
    renderApp('/reclamations/42');

    expect(await screen.findByText(/par un administrateur le/)).toBeInTheDocument();
  });

  it('l’agent confirme la proposition de l’IA', async () => {
    tokenStorage.set('jeton-agent');
    const confirmed = aiClaim({ aiPrediction: prediction({ decision: 'CONFIRMED', finalCategory: 'Livraison',
      finalService: 'Livraison', decidedByRole: 'AGENT', decidedByName: 'Arthur Agent',
      decidedAt: '2026-11-15T11:00:00Z' }),
    updatedAt: '2026-11-15T11:00:00Z' });
    const fetchMock = mockApi({
      ...me(AGENT), ...REFERENCES,
      'GET /api/claims/42': { status: 200, body: aiClaim() },
      'POST /api/claims/42/classification/confirmation': { status: 200, body: confirmed },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.click(await screen.findByRole('button', { name: 'Confirmer' }));

    expect(await screen.findByText('Classification confirmée.')).toBeInTheDocument();
    expect(callsTo(fetchMock, 'POST', '/api/claims/42/classification/confirmation')).toHaveLength(1);
    expect(screen.getByText('Confirmée')).toBeInTheDocument();
    expect(screen.getByText(/par Arthur Agent le/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
  });

  it('l’agent corrige via le formulaire de classification existant', async () => {
    tokenStorage.set('jeton-agent');
    const fetchMock = mockApi({
      ...me(AGENT), ...REFERENCES,
      'GET /api/claims/42': { status: 200, body: aiClaim() },
      'PUT /api/claims/42/classification': {
        status: 200,
        body: aiClaim({ classificationSource: 'MANUAL', autoClassified: false, category: FACTURATION, service: FACTURATION,
          updatedAt: '2026-11-15T11:00:00Z',
          aiPrediction: prediction({ decision: 'CORRECTED', finalCategory: 'Facturation', finalService: 'Facturation',
            decidedByRole: 'AGENT', decidedAt: '2026-11-15T11:00:00Z' }) }),
      },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.click(await screen.findByRole('button', { name: 'Corriger' }));
    expect(screen.getByRole('heading', { name: 'Corriger la classification' })).toBeInTheDocument();
    // Annuler referme le formulaire et réaffiche les boutons.
    await user.click(screen.getByRole('button', { name: 'Annuler' }));
    expect(screen.queryByRole('heading', { name: 'Corriger la classification' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Corriger' }));
    await user.selectOptions(screen.getByLabelText('Catégorie'), 'Facturation');
    await user.click(screen.getByRole('button', { name: 'Enregistrer la correction' }));

    expect(await screen.findByText('Classification corrigée.')).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'PUT', '/api/claims/42/classification')[0];
    expect(JSON.parse(init.body)).toEqual({ categoryId: 1 });
    expect(screen.getByText('Corrigée')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
  });

  it('un 409 à la confirmation affiche le message du serveur et recharge la réclamation', async () => {
    tokenStorage.set('jeton-agent');
    let reads = 0;
    const fetchMock = mockApi({
      ...me(AGENT), ...REFERENCES,
      // Entre-temps, un autre agent a déjà confirmé : le second chargement renvoie la décision.
      'GET /api/claims/42': () => {
        reads += 1;
        return {
          status: 200,
          body: reads === 1 ? aiClaim() : aiClaim({ aiPrediction: prediction({ decision: 'CONFIRMED',
            finalCategory: 'Livraison', finalService: 'Livraison', decidedByRole: 'AGENT',
            decidedAt: '2026-11-15T11:00:00Z' }) }),
        };
      },
      'POST /api/claims/42/classification/confirmation': {
        status: 409,
        body: { status: 409, error: 'Conflict', message: 'La classification proposée par l\'IA a déjà été confirmée' },
      },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.click(await screen.findByRole('button', { name: 'Confirmer' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Action impossible : La classification proposée par l\'IA a déjà été confirmée',
    );
    await waitFor(() => expect(callsTo(fetchMock, 'GET', '/api/claims/42')).toHaveLength(2));
    expect(await screen.findByText('Confirmée')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Confirmer' })).not.toBeInTheDocument();
  });

  it('un 403 à la confirmation affiche « Action non autorisée »', async () => {
    tokenStorage.set('jeton-agent');
    mockApi({
      ...me(AGENT), ...REFERENCES,
      'GET /api/claims/42': { status: 200, body: aiClaim() },
      'POST /api/claims/42/classification/confirmation': {
        status: 403, body: { status: 403, message: 'Seul un agent ou un administrateur peut confirmer une classification' },
      },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.click(await screen.findByRole('button', { name: 'Confirmer' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/^Action non autorisée : /);
  });
});

describe('Liste : badge IA', () => {
  const list = page([
    { id: 42, title: 'Colis', status: 'CLASSIFIED', category: LIVRAISON, service: LIVRAISON, assignedAgent: null,
      classificationSource: 'AI', confidenceScore: 0.99, autoClassified: true, updatedAt: '2026-11-15T10:00:00Z' },
    { id: 41, title: 'Facture', status: 'CLASSIFIED', category: FACTURATION, service: FACTURATION, assignedAgent: null,
      classificationSource: 'MANUAL', confidenceScore: null, autoClassified: false, updatedAt: '2026-11-14T10:00:00Z' },
  ]);

  it('badge « IA » sur les réclamations classées automatiquement (agent)', async () => {
    tokenStorage.set('jeton-agent');
    mockApi({ ...me(AGENT), 'GET /api/claims': { status: 200, body: list } });
    renderApp('/reclamations');

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row');
    expect(within(rows[1]).getByLabelText('Classée automatiquement par l’IA')).toBeInTheDocument();
    expect(within(rows[2]).queryByLabelText('Classée automatiquement par l’IA')).not.toBeInTheDocument();
  });

  it('pas de badge IA dans la liste du client', async () => {
    tokenStorage.set('jeton-client');
    mockApi({ ...me(CLIENT), 'GET /api/claims': { status: 200, body: list } });
    renderApp('/reclamations');

    await screen.findByRole('table');
    expect(screen.queryByLabelText('Classée automatiquement par l’IA')).not.toBeInTheDocument();
  });
});

describe('Filtre « À confirmer »', () => {
  it('l’agent filtre « À confirmer » : paramètre toConfirm envoyé, filtre et statut conservés dans l’URL', async () => {
    tokenStorage.set('jeton-agent');
    const fetchMock = mockApi({
      ...me(AGENT),
      'GET /api/claims': (init, url) => ({
        status: 200,
        body: url.searchParams.get('toConfirm') === 'true'
          ? page([{ id: 42, title: 'Colis à vérifier', status: 'CLASSIFIED', category: LIVRAISON, service: LIVRAISON,
            assignedAgent: null, classificationSource: 'AI', confidenceScore: 0.99, autoClassified: true,
            updatedAt: '2026-11-15T10:00:00Z' }])
          : page([]),
      }),
    });
    const user = userEvent.setup();
    renderApp('/reclamations');

    const checkbox = await screen.findByRole('checkbox', { name: /À confirmer/ });
    expect(checkbox).not.toBeChecked();
    await user.click(checkbox);

    expect(await screen.findByRole('link', { name: 'Colis à vérifier' })).toBeInTheDocument();
    expect(checkbox).toBeChecked();
    let last = new URL(callsTo(fetchMock, 'GET', '/api/claims').at(-1)[0]);
    expect(last.searchParams.get('toConfirm')).toBe('true');
    expect(last.searchParams.get('page')).toBe('0');

    // Combinable avec le statut ; une liste vide affiche un message dédié.
    await user.selectOptions(screen.getByLabelText('Filtrer par statut'), 'ASSIGNED');
    await waitFor(() => {
      last = new URL(callsTo(fetchMock, 'GET', '/api/claims').at(-1)[0]);
      expect(last.searchParams.get('status')).toBe('ASSIGNED');
    });
    expect(last.searchParams.get('toConfirm')).toBe('true');

    await user.click(checkbox);
    await waitFor(() => {
      last = new URL(callsTo(fetchMock, 'GET', '/api/claims').at(-1)[0]);
      expect(last.searchParams.has('toConfirm')).toBe(false);
    });
    expect(last.searchParams.get('status')).toBe('ASSIGNED');
  });

  it('le filtre est relu depuis l’URL (?aconfirmer=1) avec la page', async () => {
    tokenStorage.set('jeton-admin');
    const fetchMock = mockApi({
      ...me(ADMIN),
      'GET /api/claims': { status: 200, body: { content: [], page: 1, size: 10, totalElements: 11, totalPages: 2 } },
    });
    renderApp('/reclamations?aconfirmer=1&page=1');

    expect(await screen.findByText('Aucune réclamation à confirmer.')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /À confirmer/ })).toBeChecked();
    const url = new URL(callsTo(fetchMock, 'GET', '/api/claims')[0][0]);
    expect(url.searchParams.get('toConfirm')).toBe('true');
    expect(url.searchParams.get('page')).toBe('1');
  });

  it('le client n’a pas ce filtre et ne l’envoie jamais, même via l’URL', async () => {
    tokenStorage.set('jeton-client');
    const fetchMock = mockApi({ ...me(CLIENT), 'GET /api/claims': { status: 200, body: page([]) } });
    renderApp('/reclamations?aconfirmer=1');

    expect(await screen.findByRole('heading', { name: 'Mes réclamations' })).toBeInTheDocument();
    await waitFor(() => expect(callsTo(fetchMock, 'GET', '/api/claims').length).toBeGreaterThan(0));
    expect(screen.queryByRole('checkbox', { name: /À confirmer/ })).not.toBeInTheDocument();
    expect(new URL(callsTo(fetchMock, 'GET', '/api/claims')[0][0]).searchParams.has('toConfirm')).toBe(false);
  });

  it('badge client dans la liste basé sur autoClassified : jamais affiché au client (liste staff uniquement)', async () => {
    tokenStorage.set('jeton-agent');
    mockApi({
      ...me(AGENT),
      'GET /api/claims': {
        status: 200,
        body: page([{ id: 42, title: 'Colis', status: 'CLASSIFIED', category: LIVRAISON, service: LIVRAISON,
          assignedAgent: null, autoClassified: true, updatedAt: '2026-11-15T10:00:00Z' }]),
      },
    });
    renderApp('/reclamations');

    // Même sans classificationSource, autoClassified suffit pour le badge « IA ».
    expect(await screen.findByLabelText('Classée automatiquement par l’IA')).toBeInTheDocument();
  });
});

describe('Page admin « Intelligence artificielle »', () => {
  const STATS = {
    predictions: 8, autoClassified: 6, confirmed: 3, corrected: 1, awaitingReview: 2, sentToManual: 2,
    lowConfidence: 1, unknownCategory: 0, unavailable: 1, autoClassificationRate: 0.75, correctionRate: 0.25,
  };

  it('affiche les indicateurs et télécharge le CSV par un appel authentifié', async () => {
    tokenStorage.set('jeton-admin');
    const fetchMock = mockApi({
      ...me(ADMIN),
      'GET /api/claims/admin/ai-stats': { status: 200, body: STATS },
      'GET /api/claims/admin/ai-feedback/export': { status: 200, body: new Blob(['claim_id,title\n']) },
    });
    const createObjectURL = vi.fn(() => 'blob:csv');
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const user = userEvent.setup();
    renderApp('/admin/ia');

    expect(await screen.findByRole('heading', { name: 'Intelligence artificielle' })).toBeInTheDocument();
    const card = (label) => screen.getByText(label, { selector: 'p' }).closest('div');
    expect(await screen.findByText('Prédictions')).toBeInTheDocument();
    expect(card('Prédictions')).toHaveTextContent('8');
    expect(card('Classées automatiquement')).toHaveTextContent('6');
    expect(card('Confirmées')).toHaveTextContent('3');
    expect(card('Corrigées')).toHaveTextContent('1');
    expect(card('Envoyées en classement manuel')).toHaveTextContent('2');
    expect(card('Taux de classement automatique')).toHaveTextContent('75 %');
    expect(card('Taux de correction')).toHaveTextContent('25 %');
    expect(card('Service IA indisponible')).toHaveTextContent('1');

    await user.click(screen.getByRole('button', { name: 'Exporter les corrections (CSV)' }));

    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    const [url, init] = callsTo(fetchMock, 'GET', '/api/claims/admin/ai-feedback/export')[0];
    expect(url).not.toContain('jeton');
    expect(init.headers.Authorization).toBe('Bearer jeton-admin');
    expect(await screen.findByText('Export téléchargé.')).toBeInTheDocument();
  });

  it('état « aucune donnée »', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN),
      'GET /api/claims/admin/ai-stats': {
        status: 200,
        body: { ...STATS, predictions: 0, autoClassified: 0, confirmed: 0, corrected: 0, awaitingReview: 0,
          sentToManual: 0, lowConfidence: 0, unavailable: 0, autoClassificationRate: null, correctionRate: null },
      },
    });
    renderApp('/admin/ia');

    expect(await screen.findByText(/Aucune donnée pour l’instant/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exporter les corrections (CSV)' })).toBeInTheDocument();
  });

  it('état d’erreur avec « Réessayer », et export en erreur signalé', async () => {
    tokenStorage.set('jeton-admin');
    let calls = 0;
    mockApi({
      ...me(ADMIN),
      'GET /api/claims/admin/ai-stats': () => {
        calls += 1;
        return calls === 1
          ? { status: 503, body: { status: 503, message: 'Service temporairement indisponible' } }
          : { status: 200, body: STATS };
      },
      'GET /api/claims/admin/ai-feedback/export': { status: 403, body: { status: 403, message: 'Réservé aux administrateurs' } },
    });
    const user = userEvent.setup();
    renderApp('/admin/ia');

    expect(await screen.findByRole('alert')).toHaveTextContent('Indicateurs indisponibles');
    await user.click(screen.getByRole('button', { name: 'Réessayer' }));
    expect(await screen.findByText('Prédictions')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Exporter les corrections (CSV)' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Export impossible : Action non autorisée : Réservé aux administrateurs');
  });

  it('réservée à l’admin : menu pour l’admin, page 403 pour un agent', async () => {
    tokenStorage.set('jeton-agent');
    mockApi({ ...me(AGENT) });
    renderApp('/admin/ia');

    expect(await screen.findByRole('heading', { name: /accès refusé/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Intelligence artificielle' })).not.toBeInTheDocument();
  });

  it('l’admin a l’entrée « Intelligence artificielle » dans le menu', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({ ...me(ADMIN), 'GET /api/claims/admin/ai-stats': { status: 200, body: STATS } });
    renderApp('/admin/ia');

    expect((await screen.findAllByRole('link', { name: 'Intelligence artificielle' }))[0]).toHaveAttribute('href', '/admin/ia');
  });
});
