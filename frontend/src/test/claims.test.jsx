import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { tokenStorage } from '../auth/tokenStorage';
import {
  ADMIN,
  AGENT,
  CLIENT,
  FACTURATION,
  REFERENCE_HANDLERS,
  assignedClaim,
  claimDetail,
  mockApi,
  renderApp,
} from './utils';

const me = (user) => ({ 'GET /api/auth/me': { status: 200, body: user } });

function page(content) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

const callsTo = (fetchMock, method, path) =>
  fetchMock.mock.calls.filter(([url, init = {}]) => (init.method ?? 'GET') === method && new URL(url).pathname === path);

describe('Liste des réclamations', () => {
  it('affiche les réclamations du client avec leur badge et filtre par statut', async () => {
    tokenStorage.set('jeton-client');
    const fetchMock = mockApi({
      ...me(CLIENT),
      'GET /api/claims': (init, url) => ({
        status: 200,
        body: url.searchParams.get('status')
          ? page([])
          : page([
            { id: 42, title: 'Facture erronée', status: 'IN_PROGRESS', category: FACTURATION, service: FACTURATION,
              assignedAgent: null, updatedAt: '2026-11-10T09:00:00Z' },
            { id: 41, title: 'Colis abîmé', status: 'NEW', category: null, service: null, assignedAgent: null,
              updatedAt: '2026-11-09T09:00:00Z' },
          ]),
      }),
    });
    const user = userEvent.setup();
    renderApp('/reclamations');

    expect(await screen.findByRole('heading', { name: 'Mes réclamations' })).toBeInTheDocument();
    const table = await screen.findByRole('table');
    expect(within(table).getByRole('link', { name: 'Facture erronée' })).toHaveAttribute('href', '/reclamations/42');
    expect(within(table).getByText('En cours')).toBeInTheDocument();
    expect(within(table).getByText('Nouvelle')).toBeInTheDocument();
    // Un client ne voit pas la colonne « Agent ».
    expect(within(table).queryByRole('columnheader', { name: 'Agent' })).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Filtrer par statut'), 'RESOLVED');

    expect(await screen.findByText('Aucune réclamation au statut « Résolue ».')).toBeInTheDocument();
    const lastList = callsTo(fetchMock, 'GET', '/api/claims').at(-1);
    expect(new URL(lastList[0]).searchParams.get('status')).toBe('RESOLVED');
    expect(new URL(lastList[0]).searchParams.get('page')).toBe('0');
  });

  it("l'agent voit la colonne Agent et repère les réclamations qui lui sont affectées", async () => {
    tokenStorage.set('jeton-agent');
    mockApi({
      ...me(AGENT),
      'GET /api/claims': {
        status: 200,
        body: page([{ id: 42, title: 'Facture erronée', status: 'ASSIGNED', category: FACTURATION, service: FACTURATION,
          assignedAgent: { id: AGENT.id, name: AGENT.name }, updatedAt: '2026-11-10T09:00:00Z' }]),
      },
    });
    renderApp('/reclamations');

    expect(await screen.findByRole('heading', { name: 'Réclamations du service' })).toBeInTheDocument();
    const table = await screen.findByRole('table');
    expect(within(table).getByRole('columnheader', { name: 'Agent' })).toBeInTheDocument();
    expect(within(table).getByText('vous')).toBeInTheDocument();
    // Pas de bouton de création pour un agent.
    expect(screen.queryByRole('link', { name: 'Nouvelle réclamation' })).not.toBeInTheDocument();
  });
});

describe("Création d'une réclamation", () => {
  it('crée la réclamation, envoie la pièce jointe puis ouvre le détail', async () => {
    tokenStorage.set('jeton-client');
    const created = claimDetail({ id: 42, title: 'Facture erronée' });
    const fetchMock = mockApi({
      ...me(CLIENT),
      'POST /api/claims': { status: 201, body: created },
      'POST /api/claims/42/attachments': {
        status: 201,
        body: [{ id: 7, fileName: 'facture.pdf', contentType: 'application/pdf', size: 9, uploadedById: CLIENT.id,
          uploadedAt: '2026-11-10T09:01:00Z' }],
      },
      'GET /api/claims/42': {
        status: 200,
        body: { ...created, attachments: [{ id: 7, fileName: 'facture.pdf', contentType: 'application/pdf', size: 9,
          uploadedById: CLIENT.id, uploadedAt: '2026-11-10T09:01:00Z' }] },
      },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/nouvelle');

    await user.type(await screen.findByLabelText('Titre'), 'Facture erronée');
    await user.type(screen.getByLabelText('Description'), 'Montant facturé en double.');
    const pdf = new File(['%PDF-1.4'], 'facture.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText('Pièces jointes (facultatives)'), pdf);
    expect(screen.getByText(/facture\.pdf/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Soumettre la réclamation' }));

    expect(await screen.findByRole('heading', { name: 'Facture erronée' })).toBeInTheDocument();
    expect(screen.getByText('Réclamation n° 42 enregistrée avec 1 pièce(s) jointe(s).')).toBeInTheDocument();

    const [, createInit] = callsTo(fetchMock, 'POST', '/api/claims')[0];
    expect(JSON.parse(createInit.body)).toEqual({ title: 'Facture erronée', description: 'Montant facturé en double.' });
    const [, uploadInit] = callsTo(fetchMock, 'POST', '/api/claims/42/attachments')[0];
    expect(uploadInit.body).toBeInstanceOf(FormData);
    expect(uploadInit.body.getAll('files').map((f) => f.name)).toEqual(['facture.pdf']);
    // Multipart : le navigateur pose le Content-Type (boundary) ; le jeton est dans l'en-tête.
    expect(uploadInit.headers['Content-Type']).toBeUndefined();
    expect(uploadInit.headers.Authorization).toBe('Bearer jeton-client');
  });

  it('refuse un type de fichier non autorisé ou trop volumineux sans appeler le serveur', async () => {
    tokenStorage.set('jeton-client');
    const fetchMock = mockApi(me(CLIENT));
    const user = userEvent.setup({ applyAccept: false });
    renderApp('/reclamations/nouvelle');

    const input = await screen.findByLabelText('Pièces jointes (facultatives)');
    const big = new File(['x'], 'gros.png', { type: 'image/png' });
    Object.defineProperty(big, 'size', { value: 5 * 1024 * 1024 + 1 });
    await user.upload(input, [new File(['texte'], 'notes.txt', { type: 'text/plain' }), big]);

    expect(screen.getByText('notes.txt : type non accepté (PDF, PNG ou JPG uniquement).')).toBeInTheDocument();
    expect(screen.getByText('gros.png : fichier trop volumineux (5 Mo maximum).')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Soumettre la réclamation' })).toBeDisabled();
    expect(callsTo(fetchMock, 'POST', '/api/claims')).toHaveLength(0);
  });

  it('affiche les erreurs de validation par champ sans appeler le serveur', async () => {
    tokenStorage.set('jeton-client');
    const fetchMock = mockApi(me(CLIENT));
    const user = userEvent.setup();
    renderApp('/reclamations/nouvelle');

    await user.click(await screen.findByRole('button', { name: 'Soumettre la réclamation' }));

    expect(screen.getByText('Le titre est obligatoire')).toBeInTheDocument();
    expect(screen.getByText('La description est obligatoire')).toBeInTheDocument();
    expect(callsTo(fetchMock, 'POST', '/api/claims')).toHaveLength(0);
  });
});

describe('Détail selon le rôle', () => {
  const comments = [
    { id: 1, content: 'Nous vérifions votre facture.', internal: false, authorId: AGENT.id, authorRole: 'AGENT',
      createdAt: '2026-11-10T10:00:00Z' },
    { id: 2, content: 'NOTE INTERNE : erreur du batch.', internal: true, authorId: AGENT.id, authorRole: 'AGENT',
      createdAt: '2026-11-10T10:05:00Z' },
  ];

  it("un client ne voit jamais de commentaire interne ni d'action réservée aux agents", async () => {
    tokenStorage.set('jeton-client');
    // Même si le serveur renvoyait un commentaire interne, l'interface ne l'afficherait pas.
    mockApi({ ...me(CLIENT), 'GET /api/claims/42': { status: 200, body: assignedClaim('IN_PROGRESS', { comments }) } });
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Facture erronée' })).toBeInTheDocument();
    expect(screen.getByText('Nous vérifions votre facture.')).toBeInTheDocument();
    expect(screen.queryByText('NOTE INTERNE : erreur du batch.')).not.toBeInTheDocument();
    expect(screen.queryByText('Interne')).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/Commentaire interne/)).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /classification|Classer/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /affecter/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Clôturer' })).not.toBeInTheDocument();
    // Le client ne lit jamais les référentiels (la gateway répondrait 403).
    expect(screen.getByRole('list', { name: 'Historique des statuts' })).toBeInTheDocument();
  });

  it("l'agent voit les commentaires internes distingués et les actions de son rôle", async () => {
    tokenStorage.set('jeton-agent');
    const fetchMock = mockApi({
      ...me(AGENT),
      ...REFERENCE_HANDLERS,
      'GET /api/claims/42': { status: 200, body: assignedClaim('ASSIGNED', { comments }) },
    });
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Facture erronée' })).toBeInTheDocument();
    const internal = screen.getByTestId('commentaire-interne');
    expect(internal).toHaveTextContent('NOTE INTERNE : erreur du batch.');
    expect(within(internal).getByText('Interne')).toBeInTheDocument();
    expect(screen.getByTestId('commentaire-public')).toHaveTextContent('Nous vérifions votre facture.');
    expect(screen.getByLabelText('Commentaire interne (invisible pour le client)')).toBeInTheDocument();

    // Transitions de l'agent depuis ASSIGNED : seulement « Prendre en charge ».
    expect(screen.getByRole('button', { name: 'Prendre en charge' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marquer comme résolue' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Clôturer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rejeter' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Corriger la classification' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Réaffecter' })).toBeInTheDocument();
    await waitFor(() => expect(callsTo(fetchMock, 'GET', '/api/claims/services/1/agents')).toHaveLength(1));
  });

  it("l'agent envoie un commentaire interne", async () => {
    tokenStorage.set('jeton-agent');
    const fetchMock = mockApi({
      ...me(AGENT),
      ...REFERENCE_HANDLERS,
      'GET /api/claims/42': { status: 200, body: assignedClaim('IN_PROGRESS') },
      'POST /api/claims/42/comments': { status: 201, body: { id: 3 } },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.type(await screen.findByLabelText('Nouveau commentaire'), 'Vérifier le prélèvement');
    await user.click(screen.getByLabelText('Commentaire interne (invisible pour le client)'));
    await user.click(screen.getByRole('button', { name: 'Envoyer le commentaire' }));

    expect(await screen.findByText('Commentaire interne ajouté.')).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'POST', '/api/claims/42/comments')[0];
    expect(JSON.parse(init.body)).toEqual({ content: 'Vérifier le prélèvement', internal: true });
  });

  it("l'admin peut classer et rejeter une réclamation nouvelle, mais pas la prendre en charge", async () => {
    tokenStorage.set('jeton-admin');
    const fetchMock = mockApi({
      ...me(ADMIN),
      ...REFERENCE_HANDLERS,
      'GET /api/claims/42': { status: 200, body: claimDetail() },
      'PUT /api/claims/42/classification': {
        status: 200,
        body: claimDetail({ status: 'CLASSIFIED', category: FACTURATION, service: FACTURATION, classificationSource: 'MANUAL' }),
      },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    expect(await screen.findByRole('heading', { name: 'Classer la réclamation' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rejeter' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Prendre en charge' })).not.toBeInTheDocument();
    // Pas d'affectation tant que la réclamation n'est pas classée.
    expect(screen.queryByRole('heading', { name: 'Affecter à un agent' })).not.toBeInTheDocument();

    const categorySelect = screen.getByLabelText('Catégorie');
    await within(categorySelect).findByRole('option', { name: 'Facturation' });
    await user.selectOptions(categorySelect, '1');
    await user.click(screen.getByRole('button', { name: 'Classer' }));

    expect(await screen.findByText('Réclamation classée.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Affecter à un agent' })).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'PUT', '/api/claims/42/classification')[0];
    expect(JSON.parse(init.body)).toEqual({ categoryId: 1 });
  });
});

describe('Boutons de transition', () => {
  it('le client clôture une réclamation résolue (Clôturer et Rouvrir proposés)', async () => {
    tokenStorage.set('jeton-client');
    const fetchMock = mockApi({
      ...me(CLIENT),
      'GET /api/claims/42': { status: 200, body: assignedClaim('RESOLVED') },
      'PATCH /api/claims/42/status': { status: 200, body: assignedClaim('CLOSED') },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    expect(await screen.findByRole('button', { name: 'Clôturer' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rouvrir' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Clôturer' }));

    expect(await screen.findByText('Statut passé à « Clôturée ».')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Clôturer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Envoyer le commentaire' })).not.toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'PATCH', '/api/claims/42/status')[0];
    expect(JSON.parse(init.body)).toEqual({ status: 'CLOSED' });
  });

  it("aucun bouton de transition pour le client tant que la réclamation n'est pas résolue", async () => {
    tokenStorage.set('jeton-client');
    mockApi({ ...me(CLIENT), 'GET /api/claims/42': { status: 200, body: assignedClaim('IN_PROGRESS') } });
    renderApp('/reclamations/42');

    expect(await screen.findByText(/Vous pourrez la clôturer ou la rouvrir une fois résolue/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Clôturer' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rouvrir' })).not.toBeInTheDocument();
  });

  it("un 409 (transition interdite) affiche le message du serveur et recharge la réclamation", async () => {
    tokenStorage.set('jeton-agent');
    let reads = 0;
    const fetchMock = mockApi({
      ...me(AGENT),
      ...REFERENCE_HANDLERS,
      // Entre-temps, quelqu'un a déjà résolu la réclamation : le second chargement renvoie RESOLVED.
      'GET /api/claims/42': () => {
        reads += 1;
        return { status: 200, body: assignedClaim(reads === 1 ? 'IN_PROGRESS' : 'RESOLVED') };
      },
      'PATCH /api/claims/42/status': {
        status: 409,
        body: { status: 409, error: 'Conflict', message: 'Transition interdite : RESOLVED → RESOLVED' },
      },
    });
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.click(await screen.findByRole('button', { name: 'Marquer comme résolue' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Action impossible : Transition interdite : RESOLVED → RESOLVED');
    await waitFor(() => expect(callsTo(fetchMock, 'GET', '/api/claims/42')).toHaveLength(2));
    // L'état rechargé ne propose plus l'action.
    expect(screen.queryByRole('button', { name: 'Marquer comme résolue' })).not.toBeInTheDocument();
    expect(screen.getAllByText('Résolue').length).toBeGreaterThan(0);
  });

  it('une réclamation non visible affiche « introuvable » (404)', async () => {
    tokenStorage.set('jeton-client');
    mockApi({
      ...me(CLIENT),
      'GET /api/claims/99': { status: 404, body: { status: 404, message: 'Réclamation introuvable : 99' } },
    });
    renderApp('/reclamations/99');

    expect(await screen.findByRole('heading', { name: 'Réclamation introuvable' })).toBeInTheDocument();
  });
});

describe('Pièces jointes', () => {
  it('téléchargement par appel authentifié : le jeton reste dans l’en-tête, jamais dans l’URL', async () => {
    tokenStorage.set('jeton-client');
    const attachment = { id: 7, fileName: 'facture.pdf', contentType: 'application/pdf', size: 9,
      uploadedById: CLIENT.id, uploadedAt: '2026-11-10T09:01:00Z' };
    const fetchMock = mockApi({
      ...me(CLIENT),
      'GET /api/claims/42': { status: 200, body: assignedClaim('IN_PROGRESS', { attachments: [attachment] }) },
      'GET /api/claims/42/attachments/7': { status: 200, body: new Blob(['%PDF-1.4']) },
    });
    const createObjectURL = vi.fn(() => 'blob:fichier');
    URL.createObjectURL = createObjectURL;
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const user = userEvent.setup();
    renderApp('/reclamations/42');

    await user.click(await screen.findByRole('button', { name: 'Télécharger facture.pdf' }));

    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
    const [url, init] = callsTo(fetchMock, 'GET', '/api/claims/42/attachments/7')[0];
    expect(url).not.toContain('jeton');
    expect(init.headers.Authorization).toBe('Bearer jeton-client');
  });

  it("n'autorise plus d'ajout au-delà de 5 pièces jointes", async () => {
    tokenStorage.set('jeton-client');
    const attachments = [1, 2, 3, 4, 5].map((id) => ({ id, fileName: `f${id}.pdf`, contentType: 'application/pdf',
      size: 10, uploadedById: CLIENT.id, uploadedAt: '2026-11-10T09:01:00Z' }));
    mockApi({ ...me(CLIENT), 'GET /api/claims/42': { status: 200, body: assignedClaim('IN_PROGRESS', { attachments }) } });
    renderApp('/reclamations/42');

    expect(await screen.findByText('Nombre maximal de pièces jointes atteint (5).')).toBeInTheDocument();
    expect(screen.queryByLabelText('Ajouter des pièces jointes')).not.toBeInTheDocument();
  });
});

describe('Référentiels (admin)', () => {
  it('propose uniquement les comptes AGENT actifs non rattachés et rattache celui choisi', async () => {
    tokenStorage.set('jeton-admin');
    const other = { ...AGENT, id: 4, name: 'Bea Agent', email: 'bea@test.local' };
    const inactive = { ...AGENT, id: 5, name: 'Ancien Agent', email: 'ancien@test.local', active: false };
    let members = [{ agentId: AGENT.id, agentName: AGENT.name, serviceId: 1 }];
    const fetchMock = mockApi({
      ...me(ADMIN),
      ...REFERENCE_HANDLERS,
      'GET /api/auth/admin/users': { status: 200, body: [ADMIN, CLIENT, AGENT, other, inactive] },
      'GET /api/claims/services/1/agents': () => ({ status: 200, body: members }),
      'POST /api/claims/admin/services/1/agents': (init) => {
        const { agentId, agentName } = JSON.parse(init.body);
        members = [...members, { agentId, agentName, serviceId: 1 }];
        return { status: 201, body: { agentId, agentName, serviceId: 1 } };
      },
    });
    const user = userEvent.setup();
    renderApp('/admin/referentiels');

    expect(await screen.findByRole('heading', { name: 'Référentiels' })).toBeInTheDocument();
    await screen.findByRole('list', { name: 'Agents du service Facturation' });
    const select = screen.getByLabelText('Agent à rattacher');
    const options = within(select).getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['— Choisir un agent —', 'Bea Agent (bea@test.local)']);

    await user.selectOptions(select, '4');
    await user.click(screen.getByRole('button', { name: 'Rattacher' }));

    expect(await screen.findByText('Bea Agent rattaché(e) au service « Facturation ».')).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'POST', '/api/claims/admin/services/1/agents')[0];
    expect(JSON.parse(init.body)).toEqual({ agentId: 4, agentName: 'Bea Agent' });
  });

  it('affiche le message 409 du serveur lors de la suppression d’un service utilisé', async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      ...me(ADMIN),
      ...REFERENCE_HANDLERS,
      'GET /api/auth/admin/users': { status: 200, body: [ADMIN] },
      'DELETE /api/claims/admin/services/1': {
        status: 409,
        body: { status: 409, message: "Ce service traite encore des catégories : réaffectez-les d'abord" },
      },
    });
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderApp('/admin/referentiels');

    await user.click(await screen.findByRole('button', { name: 'Supprimer le service Facturation' }));

    expect(await screen.findByText(
      "Action impossible : Ce service traite encore des catégories : réaffectez-les d'abord",
    )).toBeInTheDocument();
  });
});
