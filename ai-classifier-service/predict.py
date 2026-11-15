"""Classe une réclamation en ligne de commande.

Exemples (depuis ai-classifier-service/, venv activé ou via venv\\Scripts\\python.exe) :
  python predict.py "Mon colis n'est jamais arrivé"
  python predict.py --title "Double prélèvement" --description "J'ai été débité deux fois"
  python predict.py --json "appli plante au demarrage"
"""
import argparse
import json
import sys

from classifier.predictor import ClaimClassifier


def main(argv=None):
    parser = argparse.ArgumentParser(description="Classification d'une réclamation client.")
    parser.add_argument("text", nargs="?", default=None, help="texte de la réclamation (description)")
    parser.add_argument("--title", default=None, help="titre de la réclamation")
    parser.add_argument("--description", default=None, help="description (sinon : argument text)")
    parser.add_argument("--json", action="store_true", help="sortie JSON")
    args = parser.parse_args(argv)

    description = args.description if args.description is not None else args.text
    if args.title is None and description is None:
        parser.error("donnez un texte, ou --title / --description")

    try:
        classifier = ClaimClassifier()
    except FileNotFoundError as exc:
        print(exc, file=sys.stderr)
        return 1
    result = classifier.classify(args.title, description)

    if args.json:
        print(json.dumps(result.to_dict(), ensure_ascii=False, indent=2))
        return 0

    if result.category is None:
        print("Catégorie       : non classée")
    else:
        print(f"Catégorie       : {result.category}")
        print(f"Service proposé : {result.service}")
    print(f"Confiance       : {result.confidence:.1%}")
    print(f"Seuil           : {result.threshold:.0%} -> "
          f"{'atteint' if result.reliable else 'NON atteint'}")
    print(f"Décision        : {result.reason}")
    if result.probabilities:
        details = ", ".join(f"{c} {p:.1%}" for c, p in
                            sorted(result.probabilities.items(), key=lambda kv: -kv[1]))
        print(f"Probabilités    : {details}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
