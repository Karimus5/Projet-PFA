import { apiRequest } from './client';

/**
 * Endpoints du claim-service, via la gateway. Chemins et corps alignés sur claim-service
 * (ClaimController, ReferenceDataController, web/dto/Dtos.java).
 *
 * ClaimSummary : { id, title, status, category, service, assignedAgent, classificationSource,
 *                  confidenceScore, createdAt, updatedAt }  (category/service = { id, name } | null,
 *                  assignedAgent = { id, name } | null)
 * ClaimDetail  : ClaimSummary + { description, clientId, clientEmail, history[], comments[], attachments[],
 *                aiPrediction }  (aiPrediction : agents et admins seulement, null pour un client ;
 *                format dans claims/aiRules.js)
 * PageResponse : { content, page, size, totalElements, totalPages }
 */
export const claimsApi = {
  /** Réclamations visibles par l'appelant (client : les siennes ; agent : ses services ou affectées ; admin : toutes). */
  list: ({ status, page = 0, size = 10 } = {}) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return apiRequest(`/api/claims?${params}`);
  },

  /** CLIENT : { title, description } → ClaimDetail (201). */
  create: (data) => apiRequest('/api/claims', { method: 'POST', body: data }),

  get: (id) => apiRequest(`/api/claims/${id}`),

  /** CLIENT, réclamation ouverte : { text } → ClaimDetail. */
  addComplement: (id, text) => apiRequest(`/api/claims/${id}/complements`, { method: 'POST', body: { text } }),

  /** { content, internal? } → CommentResponse (201). internal réservé aux agents et admins. */
  addComment: (id, content, internal = false) =>
    apiRequest(`/api/claims/${id}/comments`, { method: 'POST', body: { content, internal } }),

  /** { status, note? } → ClaimDetail. CLASSIFIED et ASSIGNED passent par les endpoints dédiés. */
  changeStatus: (id, status, note) =>
    apiRequest(`/api/claims/${id}/status`, { method: 'PATCH', body: { status, note: note || undefined } }),

  /** AGENT/ADMIN : { categoryId, serviceId? } → ClaimDetail (NEW → CLASSIFIED ou correction). */
  classify: (id, categoryId, serviceId) =>
    apiRequest(`/api/claims/${id}/classification`, {
      method: 'PUT',
      body: { categoryId, serviceId: serviceId ?? undefined },
    }),

  /**
   * AGENT/ADMIN : confirme la catégorie et le service proposés par l'IA (BF14) → ClaimDetail.
   * 409 si la classification ne vient pas de l'IA, est déjà confirmée / corrigée, ou statut terminé.
   */
  confirmClassification: (id) =>
    apiRequest(`/api/claims/${id}/classification/confirmation`, { method: 'POST' }),

  /** AGENT/ADMIN : { serviceId, agentId? } → ClaimDetail (affectation ou réaffectation). */
  assign: (id, serviceId, agentId) =>
    apiRequest(`/api/claims/${id}/assignment`, {
      method: 'PUT',
      body: { serviceId, agentId: agentId ?? undefined },
    }),

  /** Multipart, partie « files » → AttachmentResponse[] (201). */
  uploadAttachments: (id, files) => {
    const form = new FormData();
    files.forEach((file) => form.append('files', file, file.name));
    return apiRequest(`/api/claims/${id}/attachments`, { method: 'POST', body: form });
  },

  /** → Blob (appel authentifié ; mêmes règles d'accès que la réclamation). */
  downloadAttachment: (id, attachmentId) =>
    apiRequest(`/api/claims/${id}/attachments/${attachmentId}`, { responseType: 'blob' }),

  // --- Référentiels : lecture AGENT et ADMIN (403 pour un client) ---

  /** → CategoryResponse[] : { id, name, description, service: { id, name } } */
  listCategories: () => apiRequest('/api/claims/categories'),
  /** → ServiceResponse[] : { id, name, description } */
  listServices: () => apiRequest('/api/claims/services'),
  /** → ServiceAgentResponse[] : { agentId, agentName, serviceId } */
  listServiceAgents: (serviceId) => apiRequest(`/api/claims/services/${serviceId}/agents`),

  // --- Classement automatique : administration (ADMIN, BF20, BF25) ---

  /**
   * → { predictions, autoClassified, confirmed, corrected, awaitingReview, sentToManual,
   *     lowConfidence, unknownCategory, unavailable, autoClassificationRate, correctionRate }
   * (taux entre 0 et 1, null tant qu'il n'y a pas de donnée).
   */
  aiStats: () => apiRequest('/api/claims/admin/ai-stats'),

  /** → Blob CSV des confirmations et corrections (appel authentifié, jeton jamais dans l'URL). */
  exportAiFeedback: () => apiRequest('/api/claims/admin/ai-feedback/export', { responseType: 'blob' }),

  // --- Référentiels : écriture ADMIN ---

  createService: (data) => apiRequest('/api/claims/admin/services', { method: 'POST', body: data }),
  updateService: (id, data) => apiRequest(`/api/claims/admin/services/${id}`, { method: 'PUT', body: data }),
  deleteService: (id) => apiRequest(`/api/claims/admin/services/${id}`, { method: 'DELETE' }),

  /** { name, description?, serviceId } */
  createCategory: (data) => apiRequest('/api/claims/admin/categories', { method: 'POST', body: data }),
  updateCategory: (id, data) => apiRequest(`/api/claims/admin/categories/${id}`, { method: 'PUT', body: data }),
  deleteCategory: (id) => apiRequest(`/api/claims/admin/categories/${id}`, { method: 'DELETE' }),

  /** { agentId, agentName } : agentId = id du compte AGENT dans auth-service. */
  addServiceAgent: (serviceId, agentId, agentName) =>
    apiRequest(`/api/claims/admin/services/${serviceId}/agents`, { method: 'POST', body: { agentId, agentName } }),
  removeServiceAgent: (serviceId, agentId) =>
    apiRequest(`/api/claims/admin/services/${serviceId}/agents/${agentId}`, { method: 'DELETE' }),
};
