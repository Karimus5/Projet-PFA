"""API FastAPI : réponse nominale, texte vide, texte trop long, clé API, /health, configuration."""
import pytest
from fastapi.testclient import TestClient

from api.main import MAX_DESCRIPTION_LENGTH, MAX_TITLE_LENGTH, create_app
from api.settings import load_settings, Settings
from classifier import config

pytestmark = pytest.mark.skipif(
    not config.MODEL_PATH.exists(),
    reason="modèle absent : lancer python -m classifier.train")

TEST_KEY = "cle-de-test-0123456789"  # valeur fictive, propre aux tests


@pytest.fixture(scope="module")
def client():
    app = create_app(Settings(api_key=TEST_KEY, host="127.0.0.1", port=8000))
    with TestClient(app) as test_client:  # « with » déclenche le chargement du modèle (lifespan)
        yield test_client


def classify(client, payload, key=TEST_KEY):
    headers = {"X-API-Key": key} if key is not None else {}
    return client.post("/classify", json=payload, headers=headers)


def test_classification_nominale(client):
    response = classify(client, {"title": "Colis", "description": "Mon colis n'est jamais arrivé"})
    assert response.status_code == 200
    body = response.json()
    assert body["category"] == "Livraison"
    assert body["service"] == "Livraison"
    assert body["thresholdReached"] is True
    assert body["confidence"] >= body["threshold"]
    assert body["modelVersion"] == "1.0.0"
    assert body["processingTimeMs"] >= 0
    assert set(body["probabilities"]) == set(config.CATEGORIES)


def test_titre_facultatif(client):
    response = classify(client, {"description": "On m'a facturé deux fois la même commande"})
    assert response.status_code == 200
    assert response.json()["category"] == "Facturation"


def test_confiance_basse_seuil_non_atteint(client):
    body = classify(client, {"description": "bonjour"}).json()
    assert body["thresholdReached"] is False
    assert body["confidence"] < body["threshold"]


@pytest.mark.parametrize("payload", [
    {"title": "", "description": ""},
    {"description": "   "},
    {"title": "?!", "description": "..."},
])
def test_texte_vide(client, payload):
    response = classify(client, payload)
    assert response.status_code == 200
    body = response.json()
    assert body["category"] is None
    assert body["service"] is None
    assert body["confidence"] == 0.0
    assert body["thresholdReached"] is False


def test_description_obligatoire(client):
    assert classify(client, {"title": "Colis perdu"}).status_code == 422


@pytest.mark.parametrize("payload", [
    {"title": "a" * (MAX_TITLE_LENGTH + 1), "description": "colis perdu"},
    {"title": "Colis", "description": "a" * (MAX_DESCRIPTION_LENGTH + 1)},
])
def test_texte_trop_long(client, payload):
    assert classify(client, payload).status_code == 422


def test_texte_a_la_limite_accepte(client):
    payload = {"title": "t" * MAX_TITLE_LENGTH, "description": "colis perdu " * (MAX_DESCRIPTION_LENGTH // 12)}
    assert classify(client, payload).status_code == 200


def test_corps_invalide(client):
    response = client.post("/classify", content="pas du json",
                           headers={"X-API-Key": TEST_KEY, "Content-Type": "application/json"})
    assert response.status_code == 422


@pytest.mark.parametrize("key", [None, "", "mauvaise-cle-0123456789", TEST_KEY + "x"])
def test_cle_api_absente_ou_fausse(client, key):
    response = classify(client, {"description": "Mon colis n'est jamais arrivé"}, key=key)
    assert response.status_code == 401
    assert response.json()["detail"] == "Clé API absente ou invalide"
    assert "category" not in response.json()


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["modelVersion"] == "1.0.0"
    assert 0 < body["threshold"] < 1


def test_get_classify_non_autorise(client):
    assert client.get("/classify", headers={"X-API-Key": TEST_KEY}).status_code == 405


def test_cle_absente_refuse_le_demarrage(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_SERVICE_API_KEY", "")
    monkeypatch.setattr("api.settings.SERVICE_DIR", tmp_path / "service")  # aucun .env lu
    with pytest.raises(RuntimeError, match="AI_SERVICE_API_KEY"):
        load_settings()


def test_cle_trop_courte_refusee(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_SERVICE_API_KEY", "court")
    monkeypatch.setattr("api.settings.SERVICE_DIR", tmp_path / "service")
    with pytest.raises(RuntimeError):
        load_settings()


def test_configuration_par_defaut_localhost(monkeypatch, tmp_path):
    monkeypatch.setenv("AI_SERVICE_API_KEY", TEST_KEY)
    monkeypatch.delenv("AI_SERVICE_HOST", raising=False)
    monkeypatch.delenv("AI_SERVICE_PORT", raising=False)
    monkeypatch.setattr("api.settings.SERVICE_DIR", tmp_path / "service")
    settings = load_settings()
    assert settings.host == "127.0.0.1"
    assert settings.port == 8000
