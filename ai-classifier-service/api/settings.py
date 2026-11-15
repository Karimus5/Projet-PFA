"""Configuration du service IA, lue dans les variables d'environnement puis dans .env.

Comme les services Spring : les variables du système sont prioritaires, puis ../.env (racine du
dépôt, lancement depuis ai-classifier-service/) et ./.env.
"""
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

SERVICE_DIR = Path(__file__).resolve().parent.parent
MIN_API_KEY_LENGTH = 16


@dataclass(frozen=True)
class Settings:
    api_key: str
    host: str
    port: int


def load_settings() -> Settings:
    for env_file in (SERVICE_DIR.parent / ".env", SERVICE_DIR / ".env"):
        if env_file.is_file():
            load_dotenv(env_file, override=False)  # n'écrase pas les variables déjà définies
    api_key = os.environ.get("AI_SERVICE_API_KEY", "").strip()
    if len(api_key) < MIN_API_KEY_LENGTH:
        raise RuntimeError(
            "AI_SERVICE_API_KEY absente ou trop courte (au moins "
            f"{MIN_API_KEY_LENGTH} caractères) : renseignez-la dans le fichier .env à la racine.")
    return Settings(
        api_key=api_key,
        # localhost par défaut : le service n'est appelé que par claim-service, sur la même machine.
        host=os.environ.get("AI_SERVICE_HOST", "127.0.0.1"),
        port=int(os.environ.get("AI_SERVICE_PORT", "8000")),
    )
