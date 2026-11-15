"""Construction du jeu de données (fusion, dédoublonnage) et découpage stratifié.

Usage : python -m classifier.dataset
  1. lit data/raw/*.csv (rédigés à la main) ;
  2. supprime les doublons exacts (après normalisation) puis les quasi-doublons ;
  3. écrit data/claims.csv (title, description, category, note) ;
  4. découpe en 70/15/15 stratifié (graine fixe) dans data/splits/{train,val,test}.csv.
Le dédoublonnage a lieu AVANT le découpage : deux quasi-doublons ne peuvent pas se retrouver
dans deux ensembles différents.
"""
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split

from . import config
from .text import combine, normalize_text

COLUMNS = ["id", "title", "description", "category", "note"]

# Fichiers source : un par catégorie (catégorie implicite) + fichiers avec colonne category.
_SINGLE_CATEGORY_FILES = {
    "facturation.csv": "Facturation",
    "support_technique.csv": "Support technique",
    "livraison.csv": "Livraison",
}
_MIXED_FILES = ["complements.csv", "ambigus.csv"]


def load_raw() -> pd.DataFrame:
    frames = []
    for name, category in _SINGLE_CATEGORY_FILES.items():
        df = pd.read_csv(config.RAW_DIR / name, encoding="utf-8", dtype=str)
        df["category"] = category
        df["source"] = name
        frames.append(df)
    for name in _MIXED_FILES:
        df = pd.read_csv(config.RAW_DIR / name, encoding="utf-8", dtype=str)
        df["source"] = name
        frames.append(df)
    raw = pd.concat(frames, ignore_index=True)
    if "note" not in raw:
        raw["note"] = ""
    raw["note"] = raw["note"].fillna("")
    raw["title"] = raw["title"].fillna("").str.strip()
    raw["description"] = raw["description"].fillna("").str.strip()
    unknown = set(raw["category"]) - set(config.CATEGORIES)
    if unknown:
        raise ValueError(f"Catégories inconnues dans data/raw : {unknown}")
    return raw


def char_vectors(texts):
    """N-grammes de caractères utilisés pour détecter les quasi-doublons."""
    vectorizer = TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True)
    return vectorizer, vectorizer.fit_transform(texts)


def near_duplicate_pairs(texts, threshold=config.NEAR_DUPLICATE_THRESHOLD):
    """Paires (i, j, similarité) avec i < j et similarité cosinus ≥ seuil."""
    _, matrix = char_vectors(texts)
    sims = (matrix @ matrix.T).toarray()
    np.fill_diagonal(sims, 0.0)
    i_idx, j_idx = np.where(np.triu(sims) >= threshold)
    return [(int(i), int(j), float(sims[i, j])) for i, j in zip(i_idx, j_idx)]


def deduplicate(raw: pd.DataFrame):
    """Retire doublons exacts puis quasi-doublons ; renvoie (données, liste des lignes retirées)."""
    df = raw.copy()
    df["normalized"] = [normalize_text(combine(t, d)) for t, d in zip(df.title, df.description)]
    removed = []

    exact = df.duplicated("normalized", keep="first")
    for _, row in df[exact].iterrows():
        removed.append({"title": row.title, "reason": "doublon exact"})
    df = df[~exact].reset_index(drop=True)

    # Quasi-doublons : on garde la première occurrence (ordre des fichiers source).
    to_drop = set()
    for i, j, sim in near_duplicate_pairs(df["normalized"].tolist()):
        if i in to_drop or j in to_drop:
            continue
        to_drop.add(j)
        removed.append({
            "title": df.at[j, "title"],
            "reason": f"quasi-doublon de « {df.at[i, 'title']} » (similarité {sim:.2f})",
        })
    df = df.drop(index=sorted(to_drop)).reset_index(drop=True)
    return df, removed


def build_dataset():
    raw = load_raw()
    df, removed = deduplicate(raw)
    df = df.sort_values(["category", "source"], kind="stable").reset_index(drop=True)
    df.insert(0, "id", range(1, len(df) + 1))
    df[COLUMNS].to_csv(config.DATASET_PATH, index=False, encoding="utf-8")
    return raw, df[COLUMNS], removed


def load_dataset() -> pd.DataFrame:
    df = pd.read_csv(config.DATASET_PATH, encoding="utf-8", dtype={"id": int}, keep_default_na=False)
    return df


def split_dataset(df: pd.DataFrame, seed: int = config.SEED):
    """Découpage stratifié 70/15/15, déterministe pour une graine donnée."""
    train, rest = train_test_split(
        df, train_size=config.TRAIN_SIZE, stratify=df["category"], random_state=seed)
    val_share = config.VAL_SIZE / (config.VAL_SIZE + config.TEST_SIZE)
    val, test = train_test_split(
        rest, train_size=val_share, stratify=rest["category"], random_state=seed)
    return (train.sort_values("id").reset_index(drop=True),
            val.sort_values("id").reset_index(drop=True),
            test.sort_values("id").reset_index(drop=True))


def write_splits(train, val, test):
    config.SPLITS_DIR.mkdir(parents=True, exist_ok=True)
    for name, part in (("train", train), ("val", val), ("test", test)):
        part.to_csv(config.SPLITS_DIR / f"{name}.csv", index=False, encoding="utf-8")


def load_split(name: str) -> pd.DataFrame:
    if name not in ("train", "val", "test"):
        raise ValueError(name)
    return pd.read_csv(config.SPLITS_DIR / f"{name}.csv", encoding="utf-8",
                       dtype={"id": int}, keep_default_na=False)


def texts_and_labels(df: pd.DataFrame):
    texts = [combine(t, d) for t, d in zip(df.title, df.description)]
    return texts, df["category"].tolist()


def main():
    raw, df, removed = build_dataset()
    print(f"Lignes rédigées : {len(raw)}")
    print(f"Lignes retirées : {len(removed)}")
    for r in removed:
        print(f"  - {r['title']} : {r['reason']}")
    print(f"Jeu final : {len(df)} réclamations -> {config.DATASET_PATH.relative_to(config.ROOT)}")
    print(df["category"].value_counts().to_string())
    print(f"Cas ambigus annotés : {(df.note != '').sum()} ({(df.note != '').mean():.1%})")
    train, val, test = split_dataset(df)
    write_splits(train, val, test)
    print(f"Découpage (graine {config.SEED}) : train {len(train)}, val {len(val)}, test {len(test)}")


if __name__ == "__main__":
    main()
