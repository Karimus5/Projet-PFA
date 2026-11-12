import { Route, Routes } from 'react-router';
import { GuestOnlyRoute, HomeRedirect, ProtectedRoute } from './auth/RouteGuards';
import Layout from './components/Layout';
import AdminReferencePage from './pages/AdminReferencePage';
import AdminUsersPage from './pages/AdminUsersPage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import ClaimsListPage from './pages/ClaimsListPage';
import { ForbiddenPage, NotFoundPage } from './pages/ErrorPages';
import LoginPage from './pages/LoginPage';
import NewClaimPage from './pages/NewClaimPage';
import ProfilePage from './pages/ProfilePage';
import RegisterPage from './pages/RegisterPage';

/**
 * Plan du site. Les entrées de menu correspondantes sont dans navigation.js.
 * /reclamations et /reclamations/:id servent les trois rôles : le contenu s'adapte au rôle
 * (voir claims/claimRules.js) et le serveur filtre ce que chacun peut voir.
 */
export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomeRedirect />} />

        <Route element={<GuestOnlyRoute />}>
          <Route path="connexion" element={<LoginPage />} />
          <Route path="inscription" element={<RegisterPage />} />
        </Route>

        <Route element={<ProtectedRoute />}>
          <Route path="profil" element={<ProfilePage />} />
          <Route path="reclamations" element={<ClaimsListPage />} />
          <Route path="reclamations/:id" element={<ClaimDetailPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['CLIENT']} />}>
          <Route path="reclamations/nouvelle" element={<NewClaimPage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN']} />}>
          <Route path="admin/utilisateurs" element={<AdminUsersPage />} />
          <Route path="admin/referentiels" element={<AdminReferencePage />} />
        </Route>

        <Route path="403" element={<ForbiddenPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
