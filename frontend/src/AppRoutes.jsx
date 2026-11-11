import { Route, Routes } from 'react-router';
import { GuestOnlyRoute, HomeRedirect, ProtectedRoute } from './auth/RouteGuards';
import Layout from './components/Layout';
import AdminUsersPage from './pages/AdminUsersPage';
import { ForbiddenPage, NotFoundPage } from './pages/ErrorPages';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';
import RegisterPage from './pages/RegisterPage';

/**
 * Plan du site. Les futures pages de réclamations se brancheront ici
 * (voir les emplacements « Bientôt » dans navigation.js).
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
        </Route>

        <Route element={<ProtectedRoute roles={['ADMIN']} />}>
          <Route path="admin/utilisateurs" element={<AdminUsersPage />} />
        </Route>

        <Route path="403" element={<ForbiddenPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
