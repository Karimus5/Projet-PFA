import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';
import PageLoader from '../components/PageLoader';

/**
 * Route réservée aux utilisateurs connectés, éventuellement à certains rôles.
 * Non connecté → /connexion (puis retour à la page demandée) ; mauvais rôle → /403.
 */
export function ProtectedRoute({ roles }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <PageLoader />;
  if (!user) return <Navigate to="/connexion" replace state={{ from: location.pathname }} />;
  if (roles && !roles.includes(user.role)) return <Navigate to="/403" replace />;
  return <Outlet />;
}

/** Pages de connexion / inscription : un utilisateur déjà connecté est renvoyé vers son profil. */
export function GuestOnlyRoute() {
  const { user, loading } = useAuth();
  if (loading) return <PageLoader />;
  if (user) return <Navigate to="/profil" replace />;
  return <Outlet />;
}

/** Racine du site : profil si connecté, sinon connexion. */
export function HomeRedirect() {
  const { user, loading } = useAuth();
  if (loading) return <PageLoader />;
  return <Navigate to={user ? '/profil' : '/connexion'} replace />;
}
