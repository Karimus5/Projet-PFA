"""API REST du service IA (BF16-BF18) : POST /classify, GET /health.

- Le modèle est chargé UNE fois, au démarrage (lifespan), via classifier.predictor.ClaimClassifier.
- POST /classify exige l'en-tête X-API-Key (clé partagée avec claim-service, AI_SERVICE_API_KEY).
- GET /health est public : il ne renvoie que l'état et la version du modèle.
- Le service n'est PAS routé par l'api-gateway et écoute sur 127.0.0.1 par défaut.
- Le texte des réclamations n'est jamais journalisé (seulement sa longueur et le résultat).

Lancement : python -m api   (voir api/__main__.py)
"""
import logging
import secrets
import time
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Request, status
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from classifier.predictor import ClaimClassifier

from .settings import Settings, load_settings

log = logging.getLogger("ai-classifier")

# Mêmes limites que claim-service (titre VARCHAR(150), description VARCHAR(10000)).
MAX_TITLE_LENGTH = 150
MAX_DESCRIPTION_LENGTH = 10_000
API_KEY_HEADER = "X-API-Key"


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class ClassifyRequest(CamelModel):
    title: Optional[str] = Field(default=None, max_length=MAX_TITLE_LENGTH,
                                 description="Titre de la réclamation")
    description: str = Field(max_length=MAX_DESCRIPTION_LENGTH,
                             description="Description de la réclamation")


class ClassifyResponse(CamelModel):
    category: Optional[str] = Field(description="Catégorie prédite ; null si le texte est inexploitable")
    service: Optional[str] = Field(description="Service correspondant à la catégorie")
    confidence: float = Field(description="Probabilité calibrée de la catégorie prédite (0..1)")
    threshold: float = Field(description="Seuil de confiance du modèle")
    threshold_reached: bool = Field(description="true : classement automatique ; false : classement manuel")
    reason: str
    model_version: str
    processing_time_ms: float
    probabilities: dict[str, float]


class HealthResponse(CamelModel):
    status: str
    model_version: str
    model_name: str
    threshold: float


def create_app(settings: Optional[Settings] = None,
               classifier: Optional[ClaimClassifier] = None) -> FastAPI:
    """Fabrique de l'application (paramètres injectables pour les tests)."""
    settings = settings or load_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        start = time.perf_counter()
        app.state.classifier = classifier or ClaimClassifier()
        meta = app.state.classifier.metadata
        log.info("Modèle %s v%s chargé en %.0f ms (seuil %.2f)", meta["model_name"],
                 meta["model_version"], (time.perf_counter() - start) * 1000, meta["threshold"])
        yield

    app = FastAPI(
        title="ai-classifier-service",
        version="1.0.0",
        description="Classification automatique des réclamations clients (catégorie, service, confiance).",
        lifespan=lifespan,
    )

    def require_api_key(x_api_key: Optional[str] = Header(default=None, alias=API_KEY_HEADER)):
        # Comparaison en temps constant ; même réponse pour clé absente ou fausse.
        if x_api_key is None or not secrets.compare_digest(x_api_key.encode(), settings.api_key.encode()):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED,
                                detail="Clé API absente ou invalide")

    def get_classifier(request: Request) -> ClaimClassifier:
        return request.app.state.classifier

    @app.post("/classify", response_model=ClassifyResponse, response_model_by_alias=True,
              dependencies=[Depends(require_api_key)],
              summary="Classe une réclamation (titre + description)")
    def classify(body: ClassifyRequest, model: ClaimClassifier = Depends(get_classifier)):
        # Fonction synchrone : FastAPI l'exécute dans un pool de threads (calcul CPU).
        start = time.perf_counter()
        result = model.classify(body.title, body.description)
        elapsed = (time.perf_counter() - start) * 1000
        log.info("classify : %d caractères -> %s (%.2f, seuil %s) en %.1f ms",
                 len(body.title or "") + len(body.description), result.category, result.confidence,
                 "atteint" if result.reliable else "non atteint", elapsed)
        return ClassifyResponse(
            category=result.category,
            service=result.service,
            confidence=round(result.confidence, 4),
            threshold=result.threshold,
            threshold_reached=result.reliable,
            reason=result.reason,
            model_version=model.metadata["model_version"],
            processing_time_ms=round(elapsed, 2),
            probabilities={c: round(p, 4) for c, p in result.probabilities.items()},
        )

    @app.get("/health", response_model=HealthResponse, response_model_by_alias=True,
             summary="État du service et version du modèle")
    def health(model: ClaimClassifier = Depends(get_classifier)):
        meta = model.metadata
        return HealthResponse(status="UP", model_version=meta["model_version"],
                              model_name=meta["model_name"], threshold=meta["threshold"])

    return app
