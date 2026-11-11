import { apiRequest } from './client';

/**
 * Endpoints de l'auth-service, via la gateway. Chemins et corps alignés sur
 * auth-service (AuthController, AdminUserController, web/dto/Dtos.java).
 *
 * UserResponse  : { id, name, email, role: 'CLIENT'|'AGENT'|'ADMIN', active, createdAt }
 * TokenResponse : { accessToken, tokenType, expiresIn, user }
 */
export const authApi = {
  /** { name, email, password } → UserResponse (rôle CLIENT imposé par le serveur). */
  register: (data) => apiRequest('/api/auth/register', { method: 'POST', body: data, auth: false }),

  /** { email, password } → TokenResponse. */
  login: (data) => apiRequest('/api/auth/login', { method: 'POST', body: data, auth: false }),

  /** → UserResponse de l'utilisateur connecté. */
  me: () => apiRequest('/api/auth/me'),

  /** { name?, currentPassword?, newPassword? } → UserResponse. */
  updateMe: (data) => apiRequest('/api/auth/me', { method: 'PUT', body: data }),

  // --- Administration (ADMIN) ---

  /** → UserResponse[] */
  listUsers: () => apiRequest('/api/auth/admin/users'),

  /** { name, email, password, role: 'AGENT'|'ADMIN' } → UserResponse. */
  createUser: (data) => apiRequest('/api/auth/admin/users', { method: 'POST', body: data }),

  changeRole: (id, role) => apiRequest(`/api/auth/admin/users/${id}/role`, { method: 'PATCH', body: { role } }),

  changeStatus: (id, active) =>
    apiRequest(`/api/auth/admin/users/${id}/status`, { method: 'PATCH', body: { active } }),
};
