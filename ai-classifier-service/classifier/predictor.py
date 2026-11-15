"""Chargement du modèle sauvegardé et classification d'une réclamation.

Réutilisable tel quel par le futur service FastAPI (une seule instance, chargée au démarrage).
"""
from dataclasses import asdict, dataclass
from pathlib import Path

import joblib

from . import config
from .text import alnum_count, combine


@dataclass
class Prediction:
    category: str | None        # None : texte inexploitable, classement manuel
    service: str | None
    confidence: float           # probabilité calibrée de la catégorie prédite (0 si non classé)
    threshold: float
    reliable: bool              # confiance ≥ seuil → classement automatique accepté
    probabilities: dict
    reason: str                 # explication lisible de la décision

    def to_dict(self):
        return asdict(self)


class ClaimClassifier:
    def __init__(self, model_path: Path = config.MODEL_PATH):
        if not Path(model_path).exists():
            raise FileNotFoundError(
                f"Modèle introuvable : {model_path}. Lancez d'abord : python -m classifier.train")
        bundle = joblib.load(model_path)
        self.model = bundle["model"]
        self.metadata = bundle["metadata"]
        self.threshold = float(self.metadata["threshold"])
        self.min_alnum = int(self.metadata.get("min_alnum_chars", config.MIN_ALNUM_CHARS))
        self.category_to_service = self.metadata["category_to_service"]

    def classify(self, title: str | None = None, description: str | None = None) -> Prediction:
        text = combine(title, description)
        if alnum_count(text) < self.min_alnum:
            return Prediction(None, None, 0.0, self.threshold, False, {},
                              "texte vide ou trop court : classement manuel")
        proba = self.model.predict_proba([text])[0]
        classes = list(self.model.classes_)
        best = int(proba.argmax())
        category = classes[best]
        confidence = float(proba[best])
        reliable = confidence >= self.threshold
        reason = ("confiance suffisante : classement automatique" if reliable
                  else "confiance inférieure au seuil : classement manuel recommandé")
        return Prediction(
            category=category,
            service=self.category_to_service.get(category),
            confidence=confidence,
            threshold=self.threshold,
            reliable=reliable,
            probabilities={c: float(p) for c, p in zip(classes, proba)},
            reason=reason,
        )
