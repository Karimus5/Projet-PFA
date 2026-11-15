"""Démarre le service IA : python -m api (depuis ai-classifier-service/, avec le venv).

Hôte et port : AI_SERVICE_HOST (défaut 127.0.0.1) et AI_SERVICE_PORT (défaut 8000).
"""
import logging
import sys

import uvicorn

from .settings import load_settings


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [%(name)s] %(message)s")
    try:
        settings = load_settings()
    except RuntimeError as exc:
        print(f"Démarrage impossible : {exc}", file=sys.stderr)
        return 1
    uvicorn.run("api.main:create_app", factory=True, host=settings.host, port=settings.port,
                log_level="info")
    return 0


if __name__ == "__main__":
    sys.exit(main())
