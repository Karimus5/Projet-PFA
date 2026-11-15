"""Constantes partagées : chemins, graine, catégories, paramètres du découpage et du seuil."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

DATA_DIR = ROOT / "data"
RAW_DIR = DATA_DIR / "raw"
DATASET_PATH = DATA_DIR / "claims.csv"
SPLITS_DIR = DATA_DIR / "splits"

MODEL_DIR = ROOT / "model"
MODEL_PATH = MODEL_DIR / "classifier.joblib"
METADATA_PATH = MODEL_DIR / "metadata.json"

REPORTS_DIR = ROOT / "reports"
FIGURES_DIR = REPORTS_DIR / "figures"
VALIDATION_RESULTS_PATH = REPORTS_DIR / "validation_results.json"
REPORT_PATH = REPORTS_DIR / "evaluation.md"

MODEL_VERSION = "1.0.0"
SEED = 42

# Catégories du claim-service (Flyway V2). Chaque catégorie est liée au service du même nom :
# le service se déduit de la catégorie (BF17).
CATEGORIES = ["Facturation", "Support technique", "Livraison"]
CATEGORY_TO_SERVICE = {c: c for c in CATEGORIES}

# Découpage stratifié 70 / 15 / 15.
TRAIN_SIZE = 0.70
VAL_SIZE = 0.15
TEST_SIZE = 0.15

# Deux textes dont la similarité cosinus (n-grammes de caractères) dépasse ce seuil sont
# considérés comme des quasi-doublons : un seul est conservé.
NEAR_DUPLICATE_THRESHOLD = 0.80

# Validation croisée pour le réglage des hyperparamètres (sur l'entraînement uniquement).
CV_FOLDS = 5

# Seuil de confiance : précision minimale visée sur les prédictions acceptées (validation),
# et nombre minimal de prédictions acceptées pour qu'un seuil soit considéré.
TARGET_PRECISION = 0.95
MIN_COVERED = 20

# En dessous de ce nombre de caractères alphanumériques, le texte n'est pas classé.
MIN_ALNUM_CHARS = 3
