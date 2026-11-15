"""Entraînement, comparaison des modèles et choix du seuil — SANS jamais lire le jeu de test.

Usage : python -m classifier.train
  1. réglage des hyperparamètres de chaque candidat par validation croisée (5 plis, F1 macro)
     sur l'ENTRAÎNEMENT uniquement ;
  2. comparaison des candidats (et de la base naïve) sur la VALIDATION ; le meilleur F1 macro
     est retenu (départage : accuracy, puis score de validation croisée) ;
  3. calibration des probabilités du modèle retenu (CalibratedClassifierCV sigmoïde, sur
     l'entraînement) ;
  4. seuil de confiance choisi sur la courbe précision-couverture de la VALIDATION ;
  5. sauvegarde : model/classifier.joblib (modèle + métadonnées), model/metadata.json,
     reports/validation_results.json, figures de validation.
Le test n'est utilisé que par classifier.evaluate, une seule fois, avec le modèle déjà figé.
"""
import hashlib
import json
import platform
import time
from datetime import datetime, timezone

import joblib
import numpy as np
from sklearn.base import clone
from sklearn.calibration import CalibratedClassifierCV
from sklearn.model_selection import GridSearchCV, StratifiedKFold

from . import config
from .confidence import choose_threshold, coverage_at
from .dataset import load_split, texts_and_labels
from .metrics import classification_metrics, plot_confusion_matrix, plot_precision_coverage
from .models import CANDIDATES, baseline


def library_versions():
    import matplotlib
    import pandas
    import scipy
    import sklearn
    return {
        "python": platform.python_version(),
        "scikit-learn": sklearn.__version__,
        "numpy": np.__version__,
        "scipy": scipy.__version__,
        "pandas": pandas.__version__,
        "joblib": joblib.__version__,
        "matplotlib": matplotlib.__version__,
    }


def file_sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def cv_splitter():
    return StratifiedKFold(n_splits=config.CV_FOLDS, shuffle=True, random_state=config.SEED)


def tune(name, x_train, y_train):
    description, factory, grid = CANDIDATES[name]
    search = GridSearchCV(factory(), grid, scoring="f1_macro", cv=cv_splitter(), n_jobs=-1)
    start = time.perf_counter()
    search.fit(x_train, y_train)
    elapsed = time.perf_counter() - start
    best = search.best_index_
    return search, {
        "name": name,
        "description": description,
        "best_params": {k: (list(v) if isinstance(v, tuple) else v)
                        for k, v in search.best_params_.items()},
        "cv_f1_macro_mean": float(search.cv_results_["mean_test_score"][best]),
        "cv_f1_macro_std": float(search.cv_results_["std_test_score"][best]),
        "grid_size": len(search.cv_results_["params"]),
        "tuning_seconds": round(elapsed, 1),
    }


def main():
    train = load_split("train")
    val = load_split("val")
    x_train, y_train = texts_and_labels(train)
    x_val, y_val = texts_and_labels(val)
    print(f"Entraînement : {len(x_train)} | Validation : {len(x_val)} (test non chargé)")

    results = {}

    naive = baseline().fit(x_train, y_train)
    results["baseline_majority"] = {
        "name": "baseline_majority",
        "description": "Base naïve : classe majoritaire de l'entraînement",
        "validation": classification_metrics(y_val, naive.predict(x_val)),
    }
    print(f"- base naïve : F1 macro val = {results['baseline_majority']['validation']['macro']['f1']:.3f}")

    searches = {}
    for name in CANDIDATES:
        search, info = tune(name, x_train, y_train)
        info["validation"] = classification_metrics(y_val, search.predict(x_val))
        searches[name] = search
        results[name] = info
        print(f"- {name} : CV F1 macro = {info['cv_f1_macro_mean']:.3f} ± {info['cv_f1_macro_std']:.3f}"
              f" | val F1 macro = {info['validation']['macro']['f1']:.3f}"
              f" | val accuracy = {info['validation']['accuracy']:.3f} | {info['best_params']}")

    chosen = max(CANDIDATES, key=lambda n: (results[n]["validation"]["macro"]["f1"],
                                            results[n]["validation"]["accuracy"],
                                            results[n]["cv_f1_macro_mean"]))
    print(f"Modèle retenu : {chosen}")

    # Calibration des probabilités (sur l'entraînement, par validation croisée interne).
    best_pipeline = clone(searches[chosen].best_estimator_)
    model = CalibratedClassifierCV(best_pipeline, method="sigmoid", cv=cv_splitter())
    model.fit(x_train, y_train)

    proba_val = model.predict_proba(x_val)
    classes = list(model.classes_)
    pred_val = [classes[i] for i in proba_val.argmax(axis=1)]
    conf_val = proba_val.max(axis=1)
    correct_val = np.array(pred_val) == np.array(y_val)
    final_val = classification_metrics(y_val, pred_val)
    selection = choose_threshold(conf_val, correct_val)
    threshold = selection["threshold"]
    operating_point = coverage_at(conf_val, correct_val, threshold)
    print(f"Modèle calibré : val accuracy = {final_val['accuracy']:.3f}, "
          f"F1 macro = {final_val['macro']['f1']:.3f}")
    print(f"Seuil de confiance : {threshold:.2f} -> couverture {operating_point['coverage']:.1%}, "
          f"précision {operating_point['precision']:.1%} sur la validation")

    config.FIGURES_DIR.mkdir(parents=True, exist_ok=True)
    plot_confusion_matrix(final_val["confusion_matrix"], final_val["labels"],
                          "Matrice de confusion — validation", config.FIGURES_DIR / "confusion_matrix_val.png")
    plot_precision_coverage(selection["curve"], threshold, config.TARGET_PRECISION,
                            config.FIGURES_DIR / "precision_coverage_val.png")

    metadata = {
        "model_version": config.MODEL_VERSION,
        "trained_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "model_name": chosen,
        "model_description": results[chosen]["description"],
        "hyperparameters": results[chosen]["best_params"],
        "calibration": "CalibratedClassifierCV(method='sigmoid', cv=5) sur l'entraînement",
        "confidence": "probabilité calibrée de la classe prédite",
        "threshold": threshold,
        "threshold_rule": (f"plus petit seuil (couverture maximale) tel que la précision des "
                           f"prédictions acceptées sur la validation soit ≥ {config.TARGET_PRECISION:.0%} "
                           f"(au moins {config.MIN_COVERED} acceptées)"),
        "threshold_reached_target": selection["reached_target"],
        "min_alnum_chars": config.MIN_ALNUM_CHARS,
        "categories": classes,
        "category_to_service": config.CATEGORY_TO_SERVICE,
        "seed": config.SEED,
        "dataset": {
            "file": "data/claims.csv",
            "sha256": file_sha256(config.DATASET_PATH),
            "train": len(x_train), "validation": len(x_val),
            "test": sum(1 for _ in open(config.SPLITS_DIR / "test.csv", encoding="utf-8")) - 1,
        },
        "validation_metrics": final_val,
        "validation_operating_point": operating_point,
        "test_metrics": None,  # rempli une seule fois par classifier.evaluate
        "libraries": library_versions(),
    }

    config.MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": model, "metadata": metadata}, config.MODEL_PATH, compress=3)
    config.METADATA_PATH.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

    config.REPORTS_DIR.mkdir(parents=True, exist_ok=True)
    config.VALIDATION_RESULTS_PATH.write_text(json.dumps({
        "candidates": results,
        "chosen": chosen,
        "calibrated_validation": final_val,
        "threshold": {
            "value": threshold,
            "reached_target": selection["reached_target"],
            "target_precision": config.TARGET_PRECISION,
            "operating_point": operating_point,
            "curve": selection["curve"],
        },
        "validation_predictions": [
            {"id": int(i), "true": t, "pred": p, "confidence": float(c)}
            for i, t, p, c in zip(val["id"], y_val, pred_val, conf_val)
        ],
    }, ensure_ascii=False, indent=2), encoding="utf-8")

    size_kb = config.MODEL_PATH.stat().st_size / 1024
    print(f"Modèle sauvegardé : {config.MODEL_PATH.relative_to(config.ROOT)} ({size_kb:.0f} Ko)")


if __name__ == "__main__":
    main()
