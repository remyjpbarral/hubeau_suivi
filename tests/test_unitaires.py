"""Tests unitaires (hors ligne) : transformations et base SQLite."""

import sqlite3
import sys
import unittest
from pathlib import Path

import pandas as pd

RACINE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RACINE))

from suivi_eau import db  # noqa: E402
from suivi_eau.collecteurs.debits import CollecteurDebits  # noqa: E402
from suivi_eau.collecteurs.poissons import CollecteurPoissons  # noqa: E402
from suivi_eau.collecteurs.qualite import CollecteurQualite  # noqa: E402


BRUT_POISSON = {
    "data": [{
        "code_station": "05001800",
        "libelle_station": "LA LOIRE A SAINT-ETIENNE",
        "code_commune": "42218",
        "libelle_commune": "Saint-Étienne",
        "nom_commun_taxon": "gardon",
        "nom_scientifique_taxon": "Rutilus rutilus",
        "date_operation": "2021-06-15",
        "taille_individu": 180,
        "poids_individu": 75,
        "numero_individu": 1,
        "uri_station": "https://id.eaufrance.fr/x",
        "code_alternatif_taxon": "ABL",
    }]
}

BRUT_ANALYSE = {
    "data": [{
        "code_station": "02115725",
        "date_prelevement": "2022-03-10",
        "code_parametre": "1340",
        "libelle_parametre": "Nitrates",
        "resultat": 12.5,
        "valeur_equiv_parametre": 12.5,
        "symbole_unite": "mg/L",
    }]
}

BRUT_DEBIT = {
    "data": [{
        "code_station": "K0263020",
        "grandeur_hydro": "Q",
        "date_obs_elab": "2023-01-15T10:00:00",
        "resultat_obs_elab": 142.5,
        "code_qualification": "1",
    }]
}


class TestPoissons(unittest.TestCase):
    def test_collecte_transformations(self):
        c = CollecteurPoissons()
        obs, stations = [], []
        chemin, params = c.endpoints(code_station="05001800")[0]
        self.assertIn("etat_piscicole", chemin)
        for d in BRUT_POISSON["data"]:
            obs.append({"code_station": d["code_station"],
                        "date": d["date_operation"],
                        "parametre": d["nom_commun_taxon"],
                        "valeur": d["taille_individu"],
                        "unite": "mm",
                        "meta": "{}"})
            stations.append({"code_station": d["code_station"],
                              "libelle": d["libelle_station"]})
        df_obs = pd.DataFrame(obs)
        df_st = pd.DataFrame(stations).drop_duplicates("code_station")
        self.assertEqual(df_obs.iloc[0]["parametre"], "gardon")
        self.assertEqual(df_obs.iloc[0]["valeur"], 180)
        self.assertEqual(len(df_st), 1)


class TestQualite(unittest.TestCase):
    def test_analyses_df(self):
        c = CollecteurQualite()
        df = c._analyses_df(BRUT_ANALYSE)
        self.assertEqual(len(df), 1)
        self.assertEqual(df.iloc[0]["parametre"], "Nitrates")
        self.assertEqual(df.iloc[0]["valeur"], 12.5)
        self.assertEqual(df.iloc[0]["unite"], "mg/L")


class TestDebits(unittest.TestCase):
    def test_mesures_df(self):
        c = CollecteurDebits()
        df = c._mesures_df(BRUT_DEBIT)
        self.assertEqual(len(df), 1)
        self.assertEqual(df.iloc[0]["parametre"], "Q")
        self.assertEqual(df.iloc[0]["valeur"], 142.5)


class TestDB(unittest.TestCase):
    def setUp(self):
        import suivi_eau.config as config
        self.ancien = config.CHEMIN_DB
        config.CHEMIN_DB = RACINE / "data" / "test_suivi_eau.sqlite"
        if config.CHEMIN_DB.exists():
            config.CHEMIN_DB.unlink()
        self.con = db.connexion()

    def tearDown(self):
        self.con.close()
        from suivi_eau import config as config
        if config.CHEMIN_DB.exists():
            config.CHEMIN_DB.unlink()
        config.CHEMIN_DB = self.ancien

    def test_insert_mesures_doublons(self):
        df = pd.DataFrame([{
            "code_station": "X1", "date": "2023-01-01",
            "parametre": "Nitrates", "valeur": 10.0, "unite": "mg/L",
        }])
        n1 = db.insert_mesures(self.con, df, "qualite")
        n2 = db.insert_mesures(self.con, df, "qualite")
        self.assertEqual((n1, n2), (1, 0))

    def test_upsert_stations(self):
        df = pd.DataFrame([{
            "code_station": "X1", "libelle": "Station X",
            "code_commune": "44055", "commune": "Nantes",
        }])
        db.upsert_stations(self.con, df, "qualite")
        df2 = df.copy()
        df2["libelle"] = "Station X (mise à jour)"
        db.upsert_stations(self.con, df2, "qualite")
        row = self.con.execute(
            "SELECT libelle FROM stations WHERE code_station='X1'").fetchone()
        self.assertEqual(row[0], "Station X (mise à jour)")


if __name__ == "__main__":
    unittest.main()
