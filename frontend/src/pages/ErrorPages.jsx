import { Link } from 'react-router';

function ErrorPage({ code, title, children }) {
  return (
    <div className="mx-auto max-w-lg py-12 text-center">
      <p className="text-5xl font-bold text-indigo-600">{code}</p>
      <h1 className="mt-4 text-2xl font-semibold text-slate-900">{title}</h1>
      <p className="mt-2 text-slate-600">{children}</p>
      <Link to="/" className="btn-primary mt-6">
        Retour à l'accueil
      </Link>
    </div>
  );
}

export function ForbiddenPage() {
  return (
    <ErrorPage code="403" title="Accès refusé">
      Vous n'avez pas les droits nécessaires pour accéder à cette page.
    </ErrorPage>
  );
}

export function NotFoundPage() {
  return (
    <ErrorPage code="404" title="Page introuvable">
      La page demandée n'existe pas ou a été déplacée.
    </ErrorPage>
  );
}
