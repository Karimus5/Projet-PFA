import { useState } from 'react';
import { claimsApi } from '../../api/claimsApi';
import { canClassifyNew } from '../../claims/claimRules';
import { Spinner } from '../PageLoader';

/**
 * Classement initial (admin, NEW → CLASSIFIED) ou correction de la catégorie et du service
 * (agent ou admin, statut conservé ; BF14). Service vide = service qui traite la catégorie.
 */
export default function ClassificationPanel({ claim, user, categories, services, onPerform }) {
  const initial = canClassifyNew(claim, user);
  const [categoryId, setCategoryId] = useState(claim.category?.id ? String(claim.category.id) : '');
  const [serviceId, setServiceId] = useState('');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const selectedCategory = categories.find((c) => String(c.id) === categoryId);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!categoryId) {
      setError('La catégorie est obligatoire');
      return;
    }
    setError(null);
    setSaving(true);
    const err = await onPerform(
      () => claimsApi.classify(claim.id, Number(categoryId), serviceId ? Number(serviceId) : null),
      initial ? 'Réclamation classée.' : 'Classification corrigée.',
    );
    setSaving(false);
    if (err) setError(err.fieldErrors?.categoryId ?? null);
    else setServiceId('');
  }

  return (
    <section className="card" aria-labelledby="classement-titre">
      <h2 id="classement-titre" className="text-lg font-medium text-slate-900">
        {initial ? 'Classer la réclamation' : 'Corriger la classification'}
      </h2>
      <p className="mt-1 text-xs text-slate-500">
        {initial
          ? 'Choisissez la catégorie ; le service est celui qui traite la catégorie, sauf choix contraire.'
          : 'Le statut est conservé ; la correction est tracée dans un commentaire interne.'}
      </p>
      <form className="mt-3 space-y-3" onSubmit={handleSubmit} noValidate>
        <div>
          <label htmlFor="classement-categorie" className="mb-1 block text-sm font-medium text-slate-700">Catégorie</label>
          <select
            id="classement-categorie"
            className={`input ${error ? 'input-error' : ''}`}
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={error ? 'classement-erreur' : undefined}
          >
            <option value="">— Choisir une catégorie —</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>{category.name}</option>
            ))}
          </select>
          {error && <p id="classement-erreur" className="mt-1 text-sm text-red-600">{error}</p>}
        </div>
        <div>
          <label htmlFor="classement-service" className="mb-1 block text-sm font-medium text-slate-700">Service</label>
          <select id="classement-service" className="input" value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
            <option value="">
              {selectedCategory ? `Service de la catégorie (${selectedCategory.service?.name})` : 'Service de la catégorie'}
            </option>
            {services.map((service) => (
              <option key={service.id} value={service.id}>{service.name}</option>
            ))}
          </select>
        </div>
        <button type="submit" className="btn-primary" disabled={saving || categories.length === 0}>
          {saving && <Spinner className="h-4 w-4" />}
          {initial ? 'Classer' : 'Enregistrer la correction'}
        </button>
      </form>
    </section>
  );
}
