"""Stockage SQLite des données collectées."""

import sqlite3
from pathlib import Path

import pandas as pd

from . import config

SCHEMA = """
CREATE TABLE IF NOT EXISTS stations (
    code_station TEXT PRIMARY KEY,
    type TEXT NOT NULL,          -- 'debits' | 'poissons' | 'qualite'
    libelle TEXT,
    cours_eau TEXT,
    commune TEXT,
    code_commune TEXT,
    latitude REAL,
    longitude REAL,
    uri TEXT
);

CREATE TABLE IF NOT EXISTS mesures (
    source TEXT NOT NULL,        -- 'debits' | 'poissons' | 'qualite'
    code_station TEXT NOT NULL,
    date TEXT NOT NULL,
    parametre TEXT,              -- débit (Q), taxon, ou paramètre physico-chimique
    valeur REAL,
    unite TEXT,
    meta TEXT,                   -- JSON libre (seuil, méthode, remarques…)
    PRIMARY KEY (source, code_station, date, parametre)
);
CREATE INDEX IF NOT EXISTS idx_mesures_station ON mesures(source, code_station);
CREATE INDEX IF NOT EXISTS idx_mesures_date ON mesures(source, date);

CREATE TABLE IF NOT EXISTS journal (
    horodatage TEXT DEFAULT (datetime('now')),
    source TEXT,
    message TEXT
);
"""


def connexion() -> sqlite3.Connection:
    config.CHEMIN_DB.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(config.CHEMIN_DB)
    con.executescript(SCHEMA)
    return con


def journaliser(con: sqlite3.Connection, source: str, message: str) -> None:
    con.execute("INSERT INTO journal (source, message) VALUES (?, ?)", (source, message))


def upsert_stations(con: sqlite3.Connection, df: pd.DataFrame, type_: str) -> int:
    """Insère/met à jour des stations. Retourne le nombre de lignes."""
    if df.empty:
        return 0
    lignes = []
    for _, r in df.iterrows():
        lignes.append((
            str(r.get("code_station") or ""),
            type_,
            r.get("libelle") or r.get("libelle_station") or None,
            r.get("cours_eau") or r.get("nom_cours_eau") or None,
            r.get("commune") or r.get("libelle_commune") or None,
            str(r.get("code_commune") or "") or None,
            r.get("latitude"),
            r.get("longitude"),
            r.get("uri") or r.get("uri_station") or None,
        ))
    con.executemany(
        """
        INSERT INTO stations (code_station, type, libelle, cours_eau, commune,
                              code_commune, latitude, longitude, uri)
        VALUES (?,?,?,?,?,?,?,?,?)
        ON CONFLICT(code_station) DO UPDATE SET
            type=excluded.type,
            libelle=COALESCE(excluded.libelle, stations.libelle),
            cours_eau=COALESCE(excluded.cours_eau, stations.cours_eau),
            commune=COALESCE(excluded.commune, stations.commune),
            code_commune=COALESCE(excluded.code_commune, stations.code_commune),
            latitude=COALESCE(excluded.latitude, stations.latitude),
            longitude=COALESCE(excluded.longitude, stations.longitude),
            uri=COALESCE(excluded.uri, stations.uri)
        """,
        lignes,
    )
    return len(lignes)


def insert_mesures(con: sqlite3.Connection, df: pd.DataFrame, source: str) -> int:
    """Insère des mesures (insert or ignore : les doublons sont silencieux)."""
    if df.empty:
        return 0
    lignes = []
    for _, r in df.iterrows():
        code = str(r.get("code_station") or "")
        if not code:
            continue
        lignes.append((
            source,
            code,
            str(r["date"]),
            str(r.get("parametre") or ""),
            _to_float(r.get("valeur")),
            r.get("unite") or None,
            r.get("meta") or None,
        ))
    cur = con.executemany(
        """
        INSERT OR IGNORE INTO mesures (source, code_station, date, parametre,
                                       valeur, unite, meta)
        VALUES (?,?,?,?,?,?,?)
        """,
        lignes,
    )
    return cur.rowcount


def _to_float(v):
    if v is None:
        return None
    try:
        return float(v)
    except (TypeError, ValueError):
        return None
