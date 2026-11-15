"""Jeu de données : colonnes, catégories, doublons, découpage reproductible, absence de fuite."""
import pandas as pd
import pytest

from classifier import config
from classifier.dataset import (COLUMNS, deduplicate, load_dataset, load_raw, load_split,
                                near_duplicate_pairs, split_dataset)
from classifier.text import combine, normalize_text


@pytest.fixture(scope="module")
def dataset():
    return load_dataset()


@pytest.fixture(scope="module")
def splits():
    return {name: load_split(name) for name in ("train", "val", "test")}


def normalized(df):
    return [normalize_text(combine(t, d)) for t, d in zip(df.title, df.description)]


def test_colonnes_et_valeurs(dataset):
    assert list(dataset.columns) == COLUMNS
    assert dataset["id"].is_unique
    assert set(dataset["category"]) == set(config.CATEGORIES)
    assert (dataset["description"].str.strip() != "").all()
    assert (dataset["title"].str.strip() != "").all()


def test_au_moins_150_reclamations_par_categorie(dataset):
    counts = dataset["category"].value_counts()
    for category in config.CATEGORIES:
        assert counts[category] >= 150, category


def test_environ_10_pourcent_de_cas_ambigus_justifies(dataset):
    share = (dataset["note"] != "").mean()
    assert 0.07 <= share <= 0.13


def test_aucun_doublon_exact_ni_quasi_doublon(dataset):
    texts = normalized(dataset)
    assert len(set(texts)) == len(texts)
    assert near_duplicate_pairs(texts) == []


def test_le_fichier_correspond_aux_sources(dataset):
    """data/claims.csv doit être régénéré si data/raw change (python -m classifier.dataset)."""
    deduped, _ = deduplicate(load_raw())
    assert sorted(normalized(deduped)) == sorted(normalized(dataset))


def test_dedoublonnage_retire_copies_et_reformulations():
    raw = pd.DataFrame({
        "title": ["Colis perdu", "colis perdu", "Colis perdu !", "Facture fausse"],
        "description": ["Mon colis n'est jamais arrivé depuis deux semaines.",
                        "mon colis n est jamais arrive depuis deux semaines",
                        "Mon colis n'est jamais arrivé depuis deux semaines, merci.",
                        "Le montant de la facture est faux."],
        "category": ["Livraison", "Livraison", "Livraison", "Facturation"],
        "note": ["", "", "", ""],
    })
    kept, removed = deduplicate(raw)
    assert len(kept) == 2
    assert len(removed) == 2


def test_decoupage_70_15_15_stratifie(dataset, splits):
    total = len(dataset)
    assert sum(len(s) for s in splits.values()) == total
    assert abs(len(splits["train"]) / total - 0.70) < 0.01
    assert abs(len(splits["val"]) / total - 0.15) < 0.01
    for part in splits.values():
        shares = part["category"].value_counts(normalize=True)
        for category in config.CATEGORIES:
            assert abs(shares[category] - 1 / 3) < 0.03


def test_decoupage_reproductible(dataset, splits):
    """Même graine → mêmes ensembles, identiques aux fichiers data/splits/*.csv."""
    first = split_dataset(dataset)
    second = split_dataset(dataset)
    for a, b, name in zip(first, second, ("train", "val", "test")):
        assert a["id"].tolist() == b["id"].tolist()
        assert a["id"].tolist() == splits[name]["id"].tolist()


def test_une_autre_graine_donne_un_autre_decoupage(dataset):
    assert split_dataset(dataset, seed=1)[2]["id"].tolist() != split_dataset(dataset)[2]["id"].tolist()


def test_aucune_fuite_entre_ensembles(splits):
    ids = {name: set(df["id"]) for name, df in splits.items()}
    assert not ids["train"] & ids["val"]
    assert not ids["train"] & ids["test"]
    assert not ids["val"] & ids["test"]
    texts = {name: set(normalized(df)) for name, df in splits.items()}
    assert not texts["train"] & texts["test"]
    assert not texts["train"] & texts["val"]
    assert not texts["val"] & texts["test"]


def test_aucun_quasi_doublon_entre_entrainement_et_test(splits):
    train, test = normalized(splits["train"]), normalized(splits["test"])
    pairs = near_duplicate_pairs(train + test)
    cross = [(i, j) for i, j, _ in pairs if (i < len(train)) != (j < len(train))]
    assert cross == []
