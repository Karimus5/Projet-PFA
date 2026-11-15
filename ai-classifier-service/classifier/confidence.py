"""Score de confiance et choix du seuil de repli vers le classement manuel (BF18, BF19).

Confiance = probabilité calibrée de la classe prédite (CalibratedClassifierCV, méthode sigmoïde).
Pour un seuil t, une prédiction est « acceptée » si confiance ≥ t :
  - couverture(t) = part des réclamations acceptées (classées automatiquement) ;
  - précision(t)  = part de bonnes prédictions parmi les acceptées.
Le seuil retenu maximise la couverture sous la contrainte précision ≥ TARGET_PRECISION, avec au
moins MIN_COVERED prédictions acceptées. Il est calculé sur la VALIDATION uniquement.
"""
import numpy as np

from . import config


def precision_coverage_curve(confidences, correct):
    """Renvoie la liste des points (seuil, couverture, précision, nb acceptés) par seuil croissant."""
    confidences = np.asarray(confidences, dtype=float)
    correct = np.asarray(correct, dtype=bool)
    points = []
    for t in np.unique(confidences):
        accepted = confidences >= t
        n = int(accepted.sum())
        points.append({
            "threshold": float(t),
            "coverage": n / len(confidences),
            "precision": float(correct[accepted].mean()),
            "accepted": n,
        })
    return points


def choose_threshold(confidences, correct, target=config.TARGET_PRECISION,
                     min_covered=config.MIN_COVERED):
    """Plus petit seuil (donc couverture maximale) dont la précision atteint la cible.

    Le seuil est arrondi vers le bas au centième : une valeur lisible, et toute prédiction de
    la validation au-dessus du point retenu reste acceptée.
    """
    points = precision_coverage_curve(confidences, correct)
    eligible = [p for p in points if p["precision"] >= target and p["accepted"] >= min_covered]
    if not eligible:
        # Cible jamais atteinte : seuil le plus strict possible, tout part en classement manuel.
        best = max(points, key=lambda p: (p["precision"], p["threshold"]))
        return {"threshold": 1.0, "reached_target": False, "point": best, "curve": points}
    best = min(eligible, key=lambda p: p["threshold"])
    threshold = float(np.floor(best["threshold"] * 100) / 100)
    return {"threshold": threshold, "reached_target": True, "point": best, "curve": points}


def coverage_at(confidences, correct, threshold):
    confidences = np.asarray(confidences, dtype=float)
    correct = np.asarray(correct, dtype=bool)
    accepted = confidences >= threshold
    n = int(accepted.sum())
    return {
        "threshold": threshold,
        "coverage": n / len(confidences),
        "precision": float(correct[accepted].mean()) if n else float("nan"),
        "accepted": n,
        "manual": int(len(confidences) - n),
        "errors_accepted": int((~correct[accepted]).sum()),
        "errors_sent_to_manual": int((~correct[~accepted]).sum()),
    }
