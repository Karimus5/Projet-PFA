"""Modèles candidats et grilles d'hyperparamètres.

Tous partagent le même prétraitement (classifier.text.normalize_batch) et une représentation
TF-IDF qui combine mots (1-2 grammes) et n-grammes de caractères (robustes aux fautes de frappe,
au style SMS et aux mots collés). Seul le classifieur change.
"""
from sklearn.dummy import DummyClassifier
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.naive_bayes import ComplementNB
from sklearn.pipeline import FeatureUnion, Pipeline
from sklearn.preprocessing import FunctionTransformer
from sklearn.svm import LinearSVC

from . import config
from .text import normalize_batch


def _features():
    return FeatureUnion([
        ("words", TfidfVectorizer(analyzer="word", ngram_range=(1, 2), min_df=1,
                                  sublinear_tf=True, token_pattern=r"(?u)\b\w\w+\b")),
        ("chars", TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), min_df=2,
                                  sublinear_tf=True)),
    ])


def _pipeline(classifier):
    return Pipeline([
        ("normalize", FunctionTransformer(normalize_batch)),
        ("features", _features()),
        ("clf", classifier),
    ])


def baseline():
    """Base de référence naïve : prédit toujours la classe majoritaire de l'entraînement."""
    return DummyClassifier(strategy="most_frequent")


# Nom → (description, pipeline, grille). Les grilles restent petites : 367 exemples.
CANDIDATES = {
    "tfidf_logreg": (
        "TF-IDF mots + caractères, régression logistique",
        lambda: _pipeline(LogisticRegression(max_iter=5000, random_state=config.SEED)),
        {
            "features__words__ngram_range": [(1, 1), (1, 2)],
            "features__chars__ngram_range": [(2, 5), (3, 5)],
            "clf__C": [1.0, 4.0, 16.0, 64.0, 256.0],
        },
    ),
    "tfidf_linearsvc": (
        "TF-IDF mots + caractères, SVM linéaire (LinearSVC)",
        lambda: _pipeline(LinearSVC(random_state=config.SEED)),
        {
            "features__words__ngram_range": [(1, 1), (1, 2)],
            "features__chars__ngram_range": [(2, 5), (3, 5)],
            "clf__C": [0.1, 0.3, 1.0, 3.0, 10.0, 30.0],
        },
    ),
    "tfidf_complementnb": (
        "TF-IDF mots + caractères, Naive Bayes complémentaire",
        lambda: _pipeline(ComplementNB()),
        {
            "features__words__ngram_range": [(1, 1), (1, 2)],
            "features__chars__ngram_range": [(2, 5), (3, 5)],
            "clf__alpha": [0.03, 0.1, 0.3, 1.0],
        },
    ),
}
