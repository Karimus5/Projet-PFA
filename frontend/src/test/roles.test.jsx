import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { tokenStorage } from '../auth/tokenStorage';
import { ADMIN, CLIENT, mockApi, renderApp } from './utils';

describe('Affichage selon le rôle', () => {
  it("un client ne voit pas l'administration et est refoulé s'il tape l'URL", async () => {
    tokenStorage.set('jeton-client');
    const fetchMock = mockApi({ 'GET /api/auth/me': { status: 200, body: CLIENT } });
    renderApp('/admin/utilisateurs');

    expect(await screen.findByRole('heading', { name: 'Accès refusé' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Navigation principale' });
    expect(within(nav).queryByRole('link', { name: 'Utilisateurs' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: 'Mes réclamations' })).toHaveAttribute('href', '/reclamations');
    expect(within(nav).queryByRole('link', { name: 'Référentiels' })).not.toBeInTheDocument();
    // La liste des utilisateurs n'a jamais été demandée.
    expect(fetchMock.mock.calls.some(([url]) => url.includes('/admin/'))).toBe(false);
  });

  it("l'admin voit le lien Utilisateurs et la liste des comptes", async () => {
    tokenStorage.set('jeton-admin');
    mockApi({
      'GET /api/auth/me': { status: 200, body: ADMIN },
      'GET /api/auth/admin/users': { status: 200, body: [ADMIN, CLIENT] },
    });
    renderApp('/admin/utilisateurs');

    expect(await screen.findByRole('heading', { name: 'Gestion des utilisateurs' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Navigation principale' });
    expect(within(nav).getByRole('link', { name: 'Utilisateurs' })).toBeInTheDocument();
    expect(await screen.findByText('camille@test.local')).toBeInTheDocument();
    // L'admin ne peut pas se désactiver lui-même.
    expect(screen.getByLabelText('Rôle de Alice Admin')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Désactiver le compte de Camille Client' })).toBeEnabled();
  });

  it("le rôle affiché vient du profil serveur, pas d'une valeur locale", async () => {
    // Jeton bidon : seul GET /api/auth/me décide du rôle.
    tokenStorage.set('jeton-quelconque');
    mockApi({ 'GET /api/auth/me': { status: 200, body: CLIENT } });
    renderApp('/profil');

    expect(await screen.findByRole('heading', { name: 'Mon profil' })).toBeInTheDocument();
    expect(screen.getAllByText('Client').length).toBeGreaterThan(0);
  });

  it('déconnexion : efface le jeton et revient à la connexion', async () => {
    tokenStorage.set('jeton-client');
    mockApi({ 'GET /api/auth/me': { status: 200, body: CLIENT } });
    const user = userEvent.setup();
    renderApp('/profil');

    await screen.findByRole('heading', { name: 'Mon profil' });
    await user.click(screen.getByRole('button', { name: 'Déconnexion' }));

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument();
    expect(screen.getByText('Vous avez été déconnecté.')).toBeInTheDocument();
    expect(tokenStorage.get()).toBeNull();
  });
});
