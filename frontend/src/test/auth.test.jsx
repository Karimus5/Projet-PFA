import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { tokenStorage } from '../auth/tokenStorage';
import { CLIENT, mockApi, renderApp } from './utils';

describe('Connexion', () => {
  it('connexion réussie : stocke le jeton, lit le profil et ouvre la page profil', async () => {
    const fetchMock = mockApi({
      'POST /api/auth/login': { status: 200, body: { accessToken: 'jeton-test', tokenType: 'Bearer', expiresIn: 3600, user: CLIENT } },
      'GET /api/auth/me': { status: 200, body: CLIENT },
    });
    const user = userEvent.setup();
    renderApp('/connexion');

    await user.type(screen.getByLabelText('Email'), 'camille@test.local');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse123');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('heading', { name: 'Mon profil' })).toBeInTheDocument();
    expect(screen.getAllByText('Camille Client').length).toBeGreaterThan(0);
    expect(tokenStorage.get()).toBe('jeton-test');

    // Le jeton est ajouté automatiquement à l'appel du profil.
    const meCall = fetchMock.mock.calls.find(([url]) => url.endsWith('/api/auth/me'));
    expect(meCall[1].headers.Authorization).toBe('Bearer jeton-test');
  });

  it('connexion échouée : affiche le message du serveur et reste sur la page', async () => {
    mockApi({
      'POST /api/auth/login': { status: 401, body: { status: 401, message: 'Email ou mot de passe incorrect' } },
    });
    const user = userEvent.setup();
    renderApp('/connexion');

    await user.type(screen.getByLabelText('Email'), 'camille@test.local');
    await user.type(screen.getByLabelText('Mot de passe'), 'mauvais-mdp');
    await user.click(screen.getByRole('button', { name: 'Se connecter' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Email ou mot de passe incorrect');
    expect(screen.getByRole('heading', { name: 'Connexion' })).toBeInTheDocument();
    expect(tokenStorage.get()).toBeNull();
  });

  it("inscription : validation côté client sans appeler l'API", async () => {
    const fetchMock = mockApi({});
    const user = userEvent.setup();
    renderApp('/inscription');

    await user.type(screen.getByLabelText('Email'), 'pas-un-email');
    await user.type(screen.getByLabelText('Mot de passe'), 'court');
    await user.click(screen.getByRole('button', { name: "S'inscrire" }));

    expect(screen.getByText('Le nom est obligatoire')).toBeInTheDocument();
    expect(screen.getByText("L'email n'est pas valide")).toBeInTheDocument();
    expect(screen.getByText('Le mot de passe doit contenir entre 8 et 72 caractères')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('inscription : affiche les erreurs de validation renvoyées par le serveur, champ par champ', async () => {
    mockApi({
      'POST /api/auth/register': { status: 409, body: { status: 409, message: 'Cet email est déjà utilisé' } },
    });
    const user = userEvent.setup();
    renderApp('/inscription');

    await user.type(screen.getByLabelText('Nom complet'), 'Camille');
    await user.type(screen.getByLabelText('Email'), 'camille@test.local');
    await user.type(screen.getByLabelText('Mot de passe'), 'motdepasse123');
    await user.type(screen.getByLabelText('Confirmer le mot de passe'), 'motdepasse123');
    await user.click(screen.getByRole('button', { name: "S'inscrire" }));

    expect(await screen.findByText('Cet email est déjà utilisé')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });
});

describe('Routes protégées', () => {
  it('sans jeton, une page protégée renvoie vers la connexion', async () => {
    mockApi({});
    renderApp('/profil');

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument();
  });

  it('un 401 (jeton expiré) déconnecte et renvoie vers la connexion', async () => {
    tokenStorage.set('jeton-expire');
    mockApi({
      'GET /api/auth/me': { status: 401, body: { status: 401, message: 'Jeton invalide ou expiré' } },
    });
    renderApp('/profil');

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument();
    expect(screen.getByText('Votre session a expiré. Veuillez vous reconnecter.')).toBeInTheDocument();
    expect(tokenStorage.get()).toBeNull();
  });
});
