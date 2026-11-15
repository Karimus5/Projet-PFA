"""Calcul des métriques et figures (matrice de confusion, courbe précision-couverture)."""
import matplotlib

matplotlib.use("Agg")  # pas d'affichage : figures enregistrées en PNG
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
from sklearn.metrics import (accuracy_score, confusion_matrix,  # noqa: E402
                             precision_recall_fscore_support)

from . import config  # noqa: E402


def classification_metrics(y_true, y_pred, labels=config.CATEGORIES):
    precision, recall, f1, support = precision_recall_fscore_support(
        y_true, y_pred, labels=labels, zero_division=0)
    macro = precision_recall_fscore_support(
        y_true, y_pred, labels=labels, average="macro", zero_division=0)
    return {
        "accuracy": float(accuracy_score(y_true, y_pred)),
        "macro": {"precision": float(macro[0]), "recall": float(macro[1]), "f1": float(macro[2])},
        "per_class": {
            label: {"precision": float(p), "recall": float(r), "f1": float(f), "support": int(s)}
            for label, p, r, f, s in zip(labels, precision, recall, f1, support)
        },
        "confusion_matrix": confusion_matrix(y_true, y_pred, labels=labels).tolist(),
        "labels": list(labels),
    }


def plot_confusion_matrix(matrix, labels, title, path):
    matrix = np.asarray(matrix)
    fig, ax = plt.subplots(figsize=(5.6, 4.8), dpi=150)
    image = ax.imshow(matrix, cmap="Blues")
    ax.set_xticks(range(len(labels)), labels=labels, rotation=20, ha="right")
    ax.set_yticks(range(len(labels)), labels=labels)
    ax.set_xlabel("Catégorie prédite")
    ax.set_ylabel("Catégorie réelle")
    ax.set_title(title)
    threshold = matrix.max() / 2 if matrix.max() else 0
    for i in range(matrix.shape[0]):
        for j in range(matrix.shape[1]):
            ax.text(j, i, str(matrix[i, j]), ha="center", va="center",
                    color="white" if matrix[i, j] > threshold else "black", fontsize=12)
    fig.colorbar(image, ax=ax, fraction=0.046, pad=0.04)
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, bbox_inches="tight", pad_inches=0.15)
    plt.close(fig)


def plot_precision_coverage(curve, threshold, target, path):
    thresholds = [p["threshold"] for p in curve]
    coverage = [p["coverage"] for p in curve]
    precision = [p["precision"] for p in curve]
    fig, ax = plt.subplots(figsize=(6.4, 4.2), dpi=150)
    ax.plot(thresholds, precision, label="Précision des prédictions acceptées", color="#1f5fa8")
    ax.plot(thresholds, coverage, label="Couverture (part classée automatiquement)",
            color="#c0602a", linestyle="--")
    ax.axhline(target, color="grey", linewidth=0.8, linestyle=":")
    ax.text(thresholds[0], target + 0.005, f"précision visée {target:.0%}", fontsize=8, color="grey")
    ax.axvline(threshold, color="black", linewidth=1)
    ax.text(threshold + 0.008, 0.55, f"seuil retenu {threshold:.2f}", fontsize=8,
            bbox={"facecolor": "white", "edgecolor": "none", "pad": 1})
    ax.set_xlabel("Seuil de confiance")
    ax.set_ylabel("Proportion")
    ax.set_ylim(0, 1.03)
    ax.set_title("Précision et couverture selon le seuil (validation)")
    ax.legend(loc="lower left", fontsize=8)
    ax.grid(alpha=0.3)
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, bbox_inches="tight", pad_inches=0.15)
    plt.close(fig)
