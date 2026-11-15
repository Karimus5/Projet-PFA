"""Évaluation finale sur le jeu de TEST (une seule fois, modèle figé) et rapport Markdown.

Usage : python -m classifier.evaluate   (après python -m classifier.train)
Le modèle, ses hyperparamètres et le seuil viennent de model/classifier.joblib : rien n'est
réglé ici. Relancer la commande recalcule les mêmes chiffres (seuls les temps varient) ; le
résultat ne doit jamais servir à modifier le modèle, sinon le test n'est plus un test.
"""
import json
import time
from datetime import datetime, timezone

import joblib
import numpy as np
from sklearn.base import clone

from . import config
from .confidence import coverage_at
from .dataset import load_dataset, load_split, texts_and_labels
from .metrics import classification_metrics, plot_confusion_matrix
from .models import CANDIDATES, baseline
from .predictor import ClaimClassifier
from .text import combine, normalize_text


# --------------------------------------------------------------------------- mesures

def measure_inference(classifier, texts, repeats=3):
    """Temps de classification d'UNE réclamation à la fois (cas réel du service), en ms."""
    classifier.classify(description=texts[0])  # préchauffage
    timings = []
    for _ in range(repeats):
        for text in texts:
            start = time.perf_counter()
            classifier.classify(description=text)
            timings.append((time.perf_counter() - start) * 1000)
    timings = np.array(timings)
    start = time.perf_counter()
    classifier.model.predict_proba(texts)
    batch_ms = (time.perf_counter() - start) * 1000
    return {
        "mean_ms": float(timings.mean()),
        "median_ms": float(np.median(timings)),
        "p95_ms": float(np.percentile(timings, 95)),
        "max_ms": float(timings.max()),
        "calls": int(len(timings)),
        "batch_ms_per_claim": float(batch_ms / len(texts)),
    }


def explanation_model(model_name, params, x_train, y_train):
    """Copie NON calibrée du modèle retenu, entraînée sur l'entraînement, pour lire ses poids."""
    pipeline = CANDIDATES[model_name][1]()
    pipeline.set_params(**{k: (tuple(v) if isinstance(v, list) else v) for k, v in params.items()})
    return clone(pipeline).fit(x_train, y_train)


def top_terms(pipeline, text, predicted, n=6):
    """Termes (mots ou n-grammes de caractères) qui poussent le plus vers la classe prédite."""
    clf = pipeline.named_steps["clf"]
    if not hasattr(clf, "coef_"):
        return []
    features = pipeline.named_steps["features"]
    vector = features.transform(pipeline.named_steps["normalize"].transform([text]))
    classes = list(clf.classes_)
    weights = clf.coef_[classes.index(predicted)]
    contributions = vector.multiply(weights).toarray()[0]
    names = features.get_feature_names_out()
    order = np.argsort(contributions)[::-1]
    terms, seen = [], set()
    for idx in order:
        if contributions[idx] <= 0 or len(terms) >= n:
            break
        kind, term = names[idx].split("__", 1)
        term = term.strip()
        if len(term) < 3 or term in seen:
            continue
        seen.add(term)
        terms.append(term if kind == "words" else f"«{term}»")
    return terms


def error_rows(df, predictions, explainer):
    rows = []
    for (_, row), pred in zip(df.iterrows(), predictions):
        if pred.category == row.category:
            continue
        text = combine(row.title, row.description)
        hints = []
        if row.note:
            hints.append(f"cas ambigu annoté — {row.note}")
        if len(normalize_text(text)) < 40:
            hints.append("texte très court, peu d'indices")
        terms = top_terms(explainer, text, pred.category) if pred.category else []
        if terms:
            hints.append(f"termes tirant vers « {pred.category} » : {', '.join(terms)}")
        rows.append({
            "id": int(row.id), "title": row.title, "description": row.description,
            "true": row.category, "pred": pred.category, "confidence": pred.confidence,
            "reliable": pred.reliable, "ambiguous": bool(row.note), "hints": hints,
        })
    return rows


# --------------------------------------------------------------------------- rapport

def pct(x):
    return f"{x * 100:.1f} %"


def metrics_table(metrics):
    lines = ["| Classe | Précision | Rappel | F1-score | Support |",
             "|---|---:|---:|---:|---:|"]
    for label in metrics["labels"]:
        m = metrics["per_class"][label]
        lines.append(f"| {label} | {pct(m['precision'])} | {pct(m['recall'])} | {pct(m['f1'])} | {m['support']} |")
    macro = metrics["macro"]
    total = sum(m["support"] for m in metrics["per_class"].values())
    lines.append(f"| **Macro** | **{pct(macro['precision'])}** | **{pct(macro['recall'])}** | "
                 f"**{pct(macro['f1'])}** | {total} |")
    lines.append(f"| **Accuracy** | | | **{pct(metrics['accuracy'])}** | {total} |")
    return "\n".join(lines)


def confusion_table(metrics):
    labels = metrics["labels"]
    lines = ["| Réel \\ Prédit | " + " | ".join(labels) + " |",
             "|---|" + "---:|" * len(labels)]
    for label, row in zip(labels, metrics["confusion_matrix"]):
        lines.append(f"| {label} | " + " | ".join(str(v) for v in row) + " |")
    return "\n".join(lines)


def short(text, n=160):
    text = " ".join(str(text).split()).replace("|", "/")
    return text if len(text) <= n else text[: n - 1] + "…"


def errors_table(rows):
    if not rows:
        return "_Aucune erreur._"
    lines = ["| # | Réclamation (titre — description) | Réelle | Prédite | Confiance | Seuil atteint | Indices |",
             "|---|---|---|---|---:|---|---|"]
    for r in rows:
        lines.append(
            f"| {r['id']} | **{short(r['title'], 60)}** — {short(r['description'])} | {r['true']} | "
            f"{r['pred']} | {pct(r['confidence'])} | {'oui' if r['reliable'] else 'non (manuel)'} | "
            f"{short('; '.join(r['hints']), 260)} |")
    return "\n".join(lines)


def hypotheses(errors):
    """Hypothèses d'erreur rédigées à partir des erreurs réellement observées (val + test)."""
    if not errors:
        return ["- Aucune erreur sur la validation ni sur le test."]
    n = len(errors)
    ambiguous = sum(r["ambiguous"] for r in errors)
    short_texts = sum(any("très court" in h for h in r["hints"]) for r in errors)
    accepted = sum(r["reliable"] for r in errors)
    pairs = {}
    for r in errors:
        pairs[(r["true"], r["pred"])] = pairs.get((r["true"], r["pred"]), 0) + 1
    top_pair, top_count = max(pairs.items(), key=lambda kv: kv[1])
    plain = [r for r in errors if not r["ambiguous"]]
    lines = [
        f"- **Cas à cheval entre deux services** ({ambiguous}/{n} erreurs) : ces réclamations citent le "
        "vocabulaire de deux catégories (un suivi de colis cassé sur le site, une commande bloquée par un "
        "paiement en vérification). Le modèle s'appuie sur les mots, pas sur la règle « qui doit agir » ; "
        "ces cas relèvent de la validation humaine.",
        f"- **Confusion la plus fréquente** : « {top_pair[0]} » prédite « {top_pair[1]} » "
        f"({top_count}/{n} erreurs).",
    ]
    if plain:
        titles = ", ".join(f"« {r['title']} »" for r in plain)
        lines.append(
            f"- **Mots-clés trompeurs, sujet peu représenté** ({len(plain)}/{n} erreurs, non annotées "
            f"ambiguës : {titles}) : le sujet réel s'exprime avec un vocabulaire rare dans "
            "l'entraînement, et des mots ou fragments associés à une autre catégorie l'emportent "
            "(voir la colonne Indices).")
    lines.append(
        f"- **Textes très courts** : {short_texts}/{n} erreur(s). "
        + ("Les textes courts du jeu contiennent en général un mot-clé décisif (« colis », « facture », "
           "« bug ») ; sur des données réelles, des messages vides de sens comme « problème » seraient "
           "plus fréquents." if short_texts == 0 else
           "Quelques mots n'apportent pas assez d'indices au modèle."))
    lines.append(
        f"- **Rôle du seuil** : {n - accepted}/{n} erreur(s) passent sous le seuil et partent en "
        f"classement manuel ; les {accepted} autres seraient proposées à tort, puis corrigées par "
        "l'agent (BF14).")
    lines.append(
        "- **Pistes** : plus de données réelles (corrections des agents, BF20), exemples ciblés sur les "
        "couples de catégories confondus, et éventuellement un encodeur préentraîné si les données "
        "réelles montrent des formulations que TF-IDF ne capte pas.")
    return lines


LIMITS = """\
1. **Jeu de données entièrement rédigé par un modèle de langage** (à la demande de l'équipe ;
   le cahier des charges §10 prévoyait un jeu public adapté complété par des exemples rédigés).
   Aucune réclamation réelle n'a été utilisée.
2. **Distribution plus « propre » que le monde réel** : chaque texte a été écrit pour illustrer
   une catégorie, avec un vocabulaire souvent caractéristique (colis, livreur, facture,
   remboursement, application…). Les vraies réclamations mélangent plusieurs sujets, contiennent
   des informations inutiles, des messages copiés-collés, des pièces jointes sans texte, etc.
3. **Scores probablement optimistes** : les mêmes « habitudes d'écriture » du rédacteur se
   retrouvent dans l'entraînement et dans le test (même auteur, mêmes tournures). Le modèle peut
   apprendre ce style autant que le sujet. Les métriques obtenues ici sont un **plafond
   indicatif**, pas une estimation des performances en production ; il faudra les mesurer à
   nouveau sur des réclamations réelles (corrections des agents, BF20).
4. **Classes parfaitement équilibrées (175 par catégorie)** : en production, une catégorie sera
   plus fréquente que les autres ; la précision par classe changera.
5. **Variété limitée** : 525 textes, 3 catégories, un contexte fictif (commerce en ligne
   marocain). Les registres familiers, SMS, sans accents et les quelques phrases en darija ou en
   anglais sont présents mais peu nombreux ; ils restent sous-représentés par rapport à la
   réalité.
6. **Ambiguïté tranchée par une règle unique** (« le service qui doit agir sur la cause
   principale »), choisie par le rédacteur : un autre annotateur aurait pu étiqueter autrement
   certains cas. Aucun accord inter-annotateurs n'a été mesuré.
7. **Petits ensembles de validation et de test (79 réclamations chacun)** : une erreur de plus
   ou de moins fait varier l'accuracy d'environ 1,3 point ; l'intervalle de confiance à 95 %
   autour des scores est de l'ordre de ±4 à ±5 points. Le seuil de confiance, choisi sur 79
   exemples, est lui aussi approximatif et devra être réajusté sur données réelles.
8. **Dédoublonnage par similarité de surface** (n-grammes de caractères) : il élimine les
   copies et reformulations proches, pas deux textes différents décrivant la même situation —
   ce qui est voulu, car c'est aussi le cas des vraies réclamations.
"""

CHOICES = """\
- **Représentation** : TF-IDF sur mots (1-2 grammes) **et** sur n-grammes de caractères (2-5),
  après normalisation (minuscules, accents retirés, chiffres ramenés à 0, ponctuation retirée).
  Les n-grammes de caractères rendent le modèle tolérant aux fautes de frappe, au style SMS
  (« jai », « recu », « svp ») et aux textes sans accents.
- **Modèles comparés** : régression logistique, SVM linéaire (LinearSVC) et Naive Bayes
  complémentaire, plus une base naïve (classe majoritaire). Hyperparamètres réglés par
  GridSearchCV (5 plis stratifiés, F1 macro) sur l'entraînement uniquement ; sélection sur la
  validation (F1 macro, puis accuracy, puis score de validation croisée en cas d'égalité).
- **Modèle d'embeddings préentraîné non retenu** : un encodeur multilingue (type
  sentence-transformers) impose PyTorch et le téléchargement du modèle (environ 1 Go au total),
  pour un gain incertain sur 525 textes, avec un temps d'inférence plus élevé sur CPU. Le cahier
  des charges (§9) le cite comme une possibilité, pas comme une obligation ; il pourra être
  testé plus tard sur des données réelles.
- **Confiance** : probabilité calibrée (CalibratedClassifierCV, sigmoïde, 5 plis sur
  l'entraînement) de la catégorie prédite. Un LinearSVC ne fournit pas de probabilités ; la
  calibration les rend comparables à un seuil.
- **Texte vide ou trop court** (moins de {min_alnum} caractères alphanumériques) : pas de
  prédiction, classement manuel.
- **Le service se déduit de la catégorie** (correspondance 1-1 du claim-service : Facturation,
  Support technique, Livraison).
"""


def build_report(meta, validation, test_metrics, baseline_test, operating_test, timing,
                 val_errors, test_errors, dataset_info, conf_stats):
    chosen = validation["chosen"]
    cands = validation["candidates"]
    thr = validation["threshold"]
    op_val = thr["operating_point"]
    target_ok = test_metrics["accuracy"] >= 0.85 and test_metrics["macro"]["f1"] >= 0.85

    comparison = ["| Modèle | CV F1 macro (entraînement) | Accuracy validation | F1 macro validation |",
                  "|---|---:|---:|---:|"]
    for name, info in cands.items():
        cv = (f"{pct(info['cv_f1_macro_mean'])} ± {pct(info['cv_f1_macro_std'])}"
              if "cv_f1_macro_mean" in info else "—")
        mark = " **(retenu)**" if name == chosen else ""
        comparison.append(f"| {info['description']}{mark} | {cv} | {pct(info['validation']['accuracy'])} | "
                          f"{pct(info['validation']['macro']['f1'])} |")

    params = ", ".join(f"`{k.split('__')[-1] if k.startswith('clf') else k}` = {v}"
                       for k, v in meta["hyperparameters"].items())
    n_err_test = len(test_errors)
    n_amb_test = sum(r["ambiguous"] for r in test_errors)
    n_err_all = len(val_errors) + n_err_test
    n_amb_all = sum(r["ambiguous"] for r in val_errors) + n_amb_test

    lines = [
        "# Évaluation du modèle de classification des réclamations",
        "",
        f"_Rapport généré automatiquement par `python -m classifier.evaluate` le "
        f"{datetime.now().strftime('%d/%m/%Y à %H:%M')} — modèle v{meta['model_version']} "
        f"entraîné le {meta['trained_at']}._",
        "",
        "## 1. Synthèse",
        "",
        f"- Modèle retenu : **{meta['model_description']}**, probabilités calibrées.",
        f"- Jeu de test (utilisé une seule fois, {sum(test_metrics['per_class'][c]['support'] for c in test_metrics['labels'])} réclamations) : "
        f"**accuracy {pct(test_metrics['accuracy'])}**, **F1 macro {pct(test_metrics['macro']['f1'])}** "
        f"(base naïve : accuracy {pct(baseline_test['accuracy'])}, F1 macro {pct(baseline_test['macro']['f1'])}).",
        f"- Objectif du cahier des charges (≥ 85 % d'accuracy et de F1 macro sur le test) : "
        f"**{'atteint' if target_ok else 'non atteint'}** — sur un jeu rédigé, voir les limites (§8).",
        f"- Seuil de confiance retenu : **{meta['threshold']:.2f}**. Sur le test : "
        f"{pct(operating_test['coverage'])} des réclamations classées automatiquement avec une précision de "
        f"{pct(operating_test['precision'])} ; {operating_test['manual']} réclamation(s) envoyée(s) en classement manuel.",
        f"- Temps d'inférence moyen : **{timing['mean_ms']:.1f} ms** par réclamation "
        f"(p95 {timing['p95_ms']:.1f} ms), très en dessous de l'exigence de 2 s.",
        "",
        "## 2. Jeu de données",
        "",
        f"- {dataset_info['total']} réclamations en français rédigées pour le projet "
        f"(`data/claims.csv`), {dataset_info['per_class']}.",
        f"- {dataset_info['ambiguous']} cas difficiles ou ambigus ({pct(dataset_info['ambiguous_share'])}), "
        "à cheval entre deux catégories ; l'étiquette suit la règle « le service qui doit agir sur la "
        "cause principale » et est justifiée dans la colonne `note`.",
        "- Variété : registres formel, familier, SMS, sans accents, fautes de frappe, quelques phrases en "
        "darija et en anglais ; longueurs de 3 mots à plus de 100 mots ; titres parfois non "
        "informatifs (« URGENT », « Réclamation », « bonjour »).",
        f"- Dédoublonnage avant découpage : doublons exacts après normalisation, puis quasi-doublons "
        f"(similarité cosinus des n-grammes de caractères ≥ {config.NEAR_DUPLICATE_THRESHOLD:.2f}). "
        f"Lignes retirées : {dataset_info['removed']} ; similarité maximale restante entre deux textes : "
        f"{dataset_info['max_similarity']:.2f}.",
        f"- Découpage stratifié, graine {config.SEED} : entraînement {meta['dataset']['train']} (70 %), "
        f"validation {meta['dataset']['validation']} (15 %), test {meta['dataset']['test']} (15 %).",
        "",
        "## 3. Méthode",
        "",
        CHOICES.format(min_alnum=meta["min_alnum_chars"]),
        "## 4. Comparaison des modèles (validation)",
        "",
        "\n".join(comparison),
        "",
        f"Base naïve (classe majoritaire) sur la validation : accuracy "
        f"{pct(cands['baseline_majority']['validation']['accuracy'])}, F1 macro "
        f"{pct(cands['baseline_majority']['validation']['macro']['f1'])}.",
        "",
        f"Hyperparamètres retenus : {params}. Après calibration, le modèle obtient sur la validation "
        f"une accuracy de {pct(validation['calibrated_validation']['accuracy'])} et un F1 macro de "
        f"{pct(validation['calibrated_validation']['macro']['f1'])}.",
        "",
        "Les trois approches sont très proches (écarts inférieurs à une ou deux réclamations sur 79) : "
        "sur ce jeu, le choix du classifieur compte moins que la représentation TF-IDF mots + caractères.",
        "",
        "## 5. Résultats sur le jeu de test (évaluation finale, une seule fois)",
        "",
        "### Modèle retenu",
        "",
        metrics_table(test_metrics),
        "",
        "### Base de référence naïve (classe majoritaire)",
        "",
        metrics_table(baseline_test),
        "",
        "### Matrice de confusion (test)",
        "",
        confusion_table(test_metrics),
        "",
        "![Matrice de confusion — test](figures/confusion_matrix_test.png)",
        "",
        "### Temps d'inférence",
        "",
        "| Mesure | Valeur |",
        "|---|---:|",
        f"| Moyenne par réclamation (appel unitaire) | {timing['mean_ms']:.2f} ms |",
        f"| Médiane | {timing['median_ms']:.2f} ms |",
        f"| 95e centile | {timing['p95_ms']:.2f} ms |",
        f"| Maximum | {timing['max_ms']:.2f} ms |",
        f"| Par réclamation, traitement par lot | {timing['batch_ms_per_claim']:.2f} ms |",
        f"| Chargement du modèle (une fois au démarrage) | {timing['load_ms']:.0f} ms |",
        "",
        f"Mesuré sur {timing['calls']} appels ({timing['repeats']} passages sur le test), CPU, "
        f"Python {meta['libraries']['python']}, scikit-learn {meta['libraries']['scikit-learn']}.",
        "",
        "## 6. Score de confiance et seuil de repli (BF18, BF19)",
        "",
        f"Règle : {meta['threshold_rule']}. Le seuil est calculé sur la validation, puis vérifié sur le test.",
        "",
        "![Précision et couverture selon le seuil](figures/precision_coverage_val.png)",
        "",
        "| Ensemble | Seuil | Couverture (automatique) | Précision des acceptées | Envoyées en manuel | Erreurs acceptées | Erreurs interceptées |",
        "|---|---:|---:|---:|---:|---:|---:|",
        f"| Validation | {meta['threshold']:.2f} | {pct(op_val['coverage'])} | {pct(op_val['precision'])} | "
        f"{op_val['manual']} | {op_val['errors_accepted']} | {op_val['errors_sent_to_manual']} |",
        f"| Test | {meta['threshold']:.2f} | {pct(operating_test['coverage'])} | {pct(operating_test['precision'])} | "
        f"{operating_test['manual']} | {operating_test['errors_accepted']} | {operating_test['errors_sent_to_manual']} |",
        "",
        f"Confiance moyenne sur le test : {pct(conf_stats['correct'])} pour les bonnes prédictions, "
        f"{pct(conf_stats['wrong'])} pour les erreurs.",
        "",
        "Interprétation : en dessous du seuil, la réclamation est enregistrée sans catégorie et passe en "
        "classement manuel par l'administrateur (statut NEW). Au-dessus, la catégorie et le service proposés "
        "restent modifiables par l'agent (BF14), ce qui rattrape les erreurs acceptées. Relever le seuil "
        "intercepte plus d'erreurs mais envoie plus de réclamations correctes en manuel ; la courbe montre ce "
        "compromis. Le seuil devra être recalculé sur des réclamations réelles.",
        "",
        "## 7. Analyse des erreurs",
        "",
        f"Sur l'ensemble validation + test : {n_err_all} erreur(s), dont {n_amb_all} sur des cas annotés "
        f"comme ambigus (ceux-ci ne représentent que {pct(dataset_info['ambiguous_share'])} des données).",
        "",
        "### Erreurs sur le test",
        "",
        errors_table(test_errors),
        "",
        "### Erreurs sur la validation",
        "",
        errors_table(val_errors),
        "",
        "Les « indices » sont calculés automatiquement : note d'ambiguïté, longueur du texte et termes "
        "(mots ou «n-grammes» de caractères) dont le poids pousse le plus vers la catégorie prédite, lus "
        "sur une copie non calibrée du modèle entraînée sur le même jeu d'entraînement.",
        "",
        "Hypothèses :",
        "",
        *hypotheses(val_errors + test_errors),
        "",
        "## 8. Limites",
        "",
        LIMITS,
        "## 9. Reproductibilité",
        "",
        "| Élément | Valeur |",
        "|---|---|",
        f"| Version du modèle | {meta['model_version']} |",
        f"| Graine | {meta['seed']} |",
        f"| Empreinte SHA-256 de `data/claims.csv` | `{meta['dataset']['sha256'][:16]}…` |",
    ]
    for lib, version in meta["libraries"].items():
        lines.append(f"| {lib} | {version} |")
    lines += [
        "",
        "Commandes (depuis `ai-classifier-service/`) : `python -m classifier.dataset`, "
        "`python -m classifier.train`, `python -m classifier.evaluate`.",
        "",
    ]
    return "\n".join(lines)


# --------------------------------------------------------------------------- main

def main():
    start = time.perf_counter()
    classifier = ClaimClassifier()
    load_ms = (time.perf_counter() - start) * 1000
    meta = classifier.metadata
    validation = json.loads(config.VALIDATION_RESULTS_PATH.read_text(encoding="utf-8"))
    if meta.get("test_metrics"):
        print("Note : ce modèle a déjà été évalué sur le test ; les chiffres sont recalculés à "
              "l'identique (rien n'est réajusté).")

    train = load_split("train")
    val = load_split("val")
    test = load_split("test")
    x_train, y_train = texts_and_labels(train)
    x_test, y_test = texts_and_labels(test)

    # ---- évaluation finale sur le test
    test_preds = [classifier.classify(t, d) for t, d in zip(test.title, test.description)]
    pred_test = [p.category for p in test_preds]
    test_metrics = classification_metrics(y_test, pred_test)
    conf_test = np.array([p.confidence for p in test_preds])
    correct_test = np.array(pred_test) == np.array(y_test)
    operating_test = coverage_at(conf_test, correct_test, classifier.threshold)
    baseline_test = classification_metrics(y_test, baseline().fit(x_train, y_train).predict(x_test))
    timing = measure_inference(classifier, x_test)
    timing.update({"load_ms": load_ms, "repeats": 3})
    conf_stats = {"correct": float(conf_test[correct_test].mean()),
                  "wrong": float(conf_test[~correct_test].mean()) if (~correct_test).any() else float("nan")}

    plot_confusion_matrix(test_metrics["confusion_matrix"], test_metrics["labels"],
                          "Matrice de confusion — test", config.FIGURES_DIR / "confusion_matrix_test.png")

    # ---- analyse des erreurs (validation + test)
    explainer = explanation_model(meta["model_name"], meta["hyperparameters"], x_train, y_train)
    val_preds = [classifier.classify(t, d) for t, d in zip(val.title, val.description)]
    val_errors = error_rows(val, val_preds, explainer)
    test_errors = error_rows(test, test_preds, explainer)

    # ---- informations sur le jeu de données
    from .dataset import deduplicate, load_raw, near_duplicate_pairs
    full = load_dataset()
    raw = load_raw()
    _, removed = deduplicate(raw)
    normalized = [normalize_text(combine(t, d)) for t, d in zip(full.title, full.description)]
    pairs = near_duplicate_pairs(normalized, threshold=0.0)
    counts = full["category"].value_counts()
    dataset_info = {
        "total": len(full),
        "per_class": ", ".join(f"{counts[c]} « {c} »" for c in config.CATEGORIES),
        "ambiguous": int((full.note != "").sum()),
        "ambiguous_share": float((full.note != "").mean()),
        "removed": len(removed),
        "max_similarity": max((s for _, _, s in pairs), default=0.0),
    }

    report = build_report(meta, validation, test_metrics, baseline_test, operating_test, timing,
                          val_errors, test_errors, dataset_info, conf_stats)
    config.REPORT_PATH.write_text(report, encoding="utf-8")

    # ---- métadonnées : résultats du test enregistrés avec le modèle
    meta["test_metrics"] = test_metrics
    meta["test_operating_point"] = operating_test
    meta["inference_ms"] = {k: round(v, 3) if isinstance(v, float) else v for k, v in timing.items()}
    meta["evaluated_at"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
    joblib.dump({"model": classifier.model, "metadata": meta}, config.MODEL_PATH, compress=3)
    config.METADATA_PATH.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Test : accuracy {test_metrics['accuracy']:.3f}, F1 macro {test_metrics['macro']['f1']:.3f} "
          f"(base naïve {baseline_test['accuracy']:.3f} / {baseline_test['macro']['f1']:.3f})")
    for c in test_metrics["labels"]:
        m = test_metrics["per_class"][c]
        print(f"  {c:18s} P {m['precision']:.3f}  R {m['recall']:.3f}  F1 {m['f1']:.3f}  n={m['support']}")
    print(f"Seuil {classifier.threshold:.2f} : couverture {operating_test['coverage']:.1%}, "
          f"précision {operating_test['precision']:.1%}, manuel {operating_test['manual']}")
    print(f"Inférence : {timing['mean_ms']:.2f} ms en moyenne (p95 {timing['p95_ms']:.2f} ms)")
    print(f"Erreurs : test {len(test_errors)}, validation {len(val_errors)}")
    print(f"Rapport : {config.REPORT_PATH.relative_to(config.ROOT)}")


if __name__ == "__main__":
    main()
