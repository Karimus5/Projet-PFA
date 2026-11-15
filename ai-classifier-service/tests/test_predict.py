"""Prédiction : cohérence sur des exemples, texte vide ou très court, métadonnées, CLI."""
import json

import pytest

from classifier import config
from classifier.predictor import ClaimClassifier
from classifier.text import normalize_text
import predict

pytestmark = pytest.mark.skipif(
    not config.MODEL_PATH.exists(),
    reason="modèle absent : lancer python -m classifier.train")


@pytest.fixture(scope="module")
def classifier():
    return ClaimClassifier()


# Exemples rédigés pour les tests (absents du jeu de données), sans ambiguïté.
@pytest.mark.parametrize("title, description, expected", [
    ("Prélevé deux fois", "Ma carte bancaire a été débitée deux fois pour la même commande, "
                          "je demande le remboursement du second paiement.", "Facturation"),
    ("Erreur sur ma facture", "Le montant TTC de ma facture ne correspond pas au prix payé.", "Facturation"),
    ("Application bloquée", "L'application plante à chaque ouverture depuis la mise à jour.",
     "Support technique"),
    ("Mot de passe", "Je n'arrive plus à me connecter à mon compte, le mot de passe est refusé.",
     "Support technique"),
    ("Colis en retard", "Mon colis devait arriver lundi, le livreur n'est jamais passé.", "Livraison"),
    ("Carton abîmé", "Le colis est arrivé ouvert et un article manquait.", "Livraison"),
])
def test_predictions_coherentes(classifier, title, description, expected):
    result = classifier.classify(title, description)
    assert result.category == expected
    assert result.service == expected  # service = catégorie (correspondance 1-1)
    assert result.reliable  # exemples nets : confiance au-dessus du seuil


def test_probabilites_valides(classifier):
    result = classifier.classify("Remboursement", "Je n'ai pas reçu mon remboursement.")
    assert set(result.probabilities) == set(config.CATEGORIES)
    assert sum(result.probabilities.values()) == pytest.approx(1.0, abs=1e-6)
    assert result.confidence == max(result.probabilities.values())
    assert 0.0 <= result.confidence <= 1.0
    assert result.reliable == (result.confidence >= result.threshold)


def test_texte_sans_accents_ni_ponctuation(classifier):
    assert classifier.classify(None, "jai pas recu mon colis le livreur est jamais venu").category == "Livraison"


def test_prediction_deterministe(classifier):
    a = classifier.classify("Bug", "La page de paiement affiche une erreur 500.")
    b = classifier.classify("Bug", "La page de paiement affiche une erreur 500.")
    assert a == b


@pytest.mark.parametrize("title, description", [
    (None, None), ("", ""), ("   ", "\n\t"), ("?!", "..."), ("", "ok"), ("👍", "😡"),
])
def test_texte_vide_ou_inexploitable_part_en_manuel(classifier, title, description):
    result = classifier.classify(title, description)
    assert result.category is None
    assert result.service is None
    assert result.confidence == 0.0
    assert result.reliable is False
    assert "manuel" in result.reason


@pytest.mark.parametrize("text", ["aide", "bug", "colis", "facture", "probleme", "svp urgent"])
def test_texte_tres_court_ne_plante_pas(classifier, text):
    result = classifier.classify(None, text)
    assert result.category in config.CATEGORIES
    assert 0.0 <= result.confidence <= 1.0


def test_texte_court_sans_sens_peu_fiable(classifier):
    """Un mot sans indice de catégorie ne doit pas être classé automatiquement."""
    assert classifier.classify(None, "bonjour").reliable is False


def test_texte_tres_long(classifier):
    long_text = "Le livreur n'est jamais venu et mon colis est perdu. " * 300
    assert classifier.classify("Colis", long_text).category == "Livraison"


def test_normalisation():
    assert normalize_text("Remboursé  à 100 % !!!") == "rembourse a 00"  # chiffres → 0, répétitions réduites
    assert normalize_text("Facturé 49 DH") == "facture 00 dh"
    assert normalize_text("Écrivez à test@exemple.ma ou https://site.ma") == "ecrivez a email ou url"
    assert normalize_text(None) == ""


def test_metadonnees_completes(classifier):
    meta = classifier.metadata
    for key in ("model_version", "trained_at", "model_name", "threshold", "validation_metrics",
                "libraries", "categories", "category_to_service", "dataset"):
        assert key in meta, key
    assert 0.0 < meta["threshold"] < 1.0
    on_disk = json.loads(config.METADATA_PATH.read_text(encoding="utf-8"))
    assert on_disk["threshold"] == meta["threshold"]
    assert on_disk["model_version"] == meta["model_version"]


def test_cli(capsys):
    assert predict.main(["--title", "Facture", "--description", "On m'a facturé deux fois", "--json"]) == 0
    output = json.loads(capsys.readouterr().out)
    assert output["category"] == "Facturation"
    assert {"confidence", "threshold", "reliable", "service"} <= set(output)


def test_cli_sans_texte():
    with pytest.raises(SystemExit):
        predict.main([])
