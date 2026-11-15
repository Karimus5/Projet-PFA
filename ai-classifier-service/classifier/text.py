"""Prétraitement du texte d'une réclamation.

Ces fonctions font partie du pipeline sauvegardé (FunctionTransformer) : le modèle chargé par
predict.py, puis par le futur service FastAPI, applique exactement le même nettoyage qu'à
l'entraînement. Ne pas les renommer ni les déplacer sans réentraîner le modèle.
"""
import re
import unicodedata

_URL = re.compile(r"(https?://\S+|www\.\S+)")
_EMAIL = re.compile(r"\S+@\S+\.\S+")
_DIGITS = re.compile(r"\d")
_REPEATED = re.compile(r"(.)\1{2,}")
_NON_WORD = re.compile(r"[^a-z0-9\s]")
_SPACES = re.compile(r"\s+")


def strip_accents(text: str) -> str:
    decomposed = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in decomposed if not unicodedata.combining(ch))


def normalize_text(text) -> str:
    """Minuscules, sans accents, URL/e-mails remplacés, chiffres ramenés à 0, ponctuation retirée.

    Les accents sont supprimés car une partie des clients écrit sans accents : « facture » et
    « facturé » doivent partager les mêmes n-grammes. Les chiffres sont ramenés à 0 pour que les
    montants et numéros de commande ne deviennent pas des mots rares mémorisés par le modèle.
    """
    if text is None:
        return ""
    text = str(text).lower()
    text = _URL.sub(" url ", text)
    text = _EMAIL.sub(" email ", text)
    text = strip_accents(text)
    text = text.replace("'", " ").replace("’", " ")
    text = _DIGITS.sub("0", text)
    # « !!!! », « nooooon » → 2 caractères. S'applique aussi aux chiffres déjà ramenés à 0 :
    # « 100 » et « 2 000 » donnent « 00 » et « 0 00 ». Sans effet utile sur la classification
    # (la valeur des montants est volontairement ignorée) ; conservé tel quel car le modèle v1.0.0
    # a été entraîné et évalué avec ce comportement.
    text = _REPEATED.sub(r"\1\1", text)
    text = _NON_WORD.sub(" ", text)
    return _SPACES.sub(" ", text).strip()


def combine(title, description) -> str:
    """Texte classé : titre puis description (le titre est souvent peu informatif : « URGENT »)."""
    parts = [str(p) for p in (title, description) if p is not None and str(p).strip()]
    return " . ".join(parts)


def normalize_batch(texts):
    """Version liste pour FunctionTransformer (doit rester une fonction de module, picklable)."""
    return [normalize_text(t) for t in texts]


def alnum_count(text) -> int:
    return sum(ch.isalnum() for ch in normalize_text(text))
