import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { authApi } from '../api/authApi';
import { setUnauthorizedHandler } from '../api/client';
import { tokenStorage } from './tokenStorage';

const AuthContext = createContext(null);

/**
 * État d'authentification de l'application.
 * L'utilisateur (et donc son RÔLE) vient toujours de GET /api/auth/me : on ne décode pas le JWT
 * côté client. Le rôle sert à l'affichage et au routage ; la vraie protection est faite par la
 * gateway et l'auth-service.
 */
export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  // Si un jeton existe déjà (rechargement de page), on charge le profil avant d'afficher les routes.
  const [loading, setLoading] = useState(() => Boolean(tokenStorage.get()));
  // Message à afficher sur la page de connexion (déconnexion, session expirée).
  const [notice, setNotice] = useState(null);

  const logout = useCallback(
    (message = 'Vous avez été déconnecté.') => {
      tokenStorage.clear();
      setUser(null);
      setNotice(message);
      navigate('/connexion', { replace: true });
    },
    [navigate],
  );

  // Tout 401 reçu avec un jeton (expiré, falsifié...) → déconnexion + retour à la connexion.
  useEffect(() => {
    setUnauthorizedHandler(() => logout('Votre session a expiré. Veuillez vous reconnecter.'));
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  useEffect(() => {
    if (!tokenStorage.get()) return undefined;
    let cancelled = false;
    authApi
      .me()
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        // Jeton invalide ou compte désactivé (403) : on repart de zéro.
        if (!cancelled) {
          tokenStorage.clear();
          setUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const { accessToken } = await authApi.login({ email, password });
    tokenStorage.set(accessToken);
    try {
      const profile = await authApi.me();
      setUser(profile);
      setNotice(null);
      return profile;
    } catch (error) {
      tokenStorage.clear();
      throw error;
    }
  }, []);

  /** Inscription puis connexion automatique. */
  const register = useCallback(
    async ({ name, email, password }) => {
      await authApi.register({ name, email, password });
      return login(email, password);
    },
    [login],
  );

  const value = useMemo(
    () => ({
      user,
      loading,
      notice,
      login,
      register,
      logout,
      updateUser: setUser,
      clearNotice: () => setNotice(null),
    }),
    [user, loading, notice, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth doit être utilisé à l’intérieur de <AuthProvider>.');
  return context;
}
