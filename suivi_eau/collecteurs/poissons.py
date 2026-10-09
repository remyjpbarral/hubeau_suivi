"""Collecteur Poissons — API Poisson / État piscicole (base ASPE, OFB).

Endpoint principal : /v1/etat_piscicole/observations
Chaque observation = un individu (taxon, taille, poids) lors d'une pêche
électrique sur une station, à une date.
"""

import json

import pandas as pd

from .base import CollecteurBase


class CollecteurPoissons(CollecteurBase):
    type_ = "poissons"
    libelle = "Poissons (État piscicole / ASPE)"

    def endpoints(self, code_cours_eau=None, code_commune=None,
                  code_station=None, date_debut=None, date_fin=None, **_):
        params = {
            "fields": ("code_station,libelle_station,coordonnee_x_point_prelevement,"
                       "coordonnee_y_point_prelevement,code_commune,libelle_commune,"
                       "nom_commun_taxon,nom_scientifique_taxon,date_operation,"
                       "taille_individu,poids_individu,code_alternatif_taxon,"
                       "numero_individu,uri_station")
        }
        if code_station:
            params["code_station"] = code_station
        else:
            if code_cours_eau:
                # nom libre du cours d'eau ou code entité hydro
                params["libelle_entite_hydro"] = code_cours_eau
            if code_commune:
                params["code_commune"] = code_commune
        if date_debut:
            params["date_operation_min"] = date_debut
        if date_fin:
            params["date_operation_max"] = date_fin
        return [("/v1/etat_piscicole/observations", params)]

    def transforme(self, brut: dict) -> pd.DataFrame:
        rows, stations = [], []
        for d in brut.get("data", []):
            code = d.get("code_station")
            if code:
                stations.append({
                    "code_station": code,
                    "libelle": d.get("libelle_station"),
                    "code_commune": d.get("code_commune"),
                    "commune": d.get("libelle_commune"),
                    "uri": d.get("uri_station"),
                })
            if not d.get("date_operation"):
                continue
            meta = json.dumps({
                "nom_scientifique": d.get("nom_scientifique_taxon"),
                "code_taxon": d.get("code_alternatif_taxon"),
                "numero_individu": d.get("numero_individu"),
                "poids_g": d.get("poids_individu"),
            }, ensure_ascii=False)
            rows.append({
                "code_station": code,
                "date": d.get("date_operation"),
                "parametre": d.get("nom_commun_taxon") or d.get("nom_scientifique_taxon"),
                "valeur": d.get("taille_individu"),
                "unite": "mm",
                "meta": meta,
            })
        if not rows:
            return pd.DataFrame(stations).drop_duplicates("code_station") if stations else pd.DataFrame()
        return pd.DataFrame(rows)

    def collecte(self, **filtres):
        # Récupère observations + stations dédoublonnées
        chemin, params = self.endpoints(**filtres)[0]
        obs, stations = [], []
        for page in self._pages(chemin, params):
            for d in page.get("data", []):
                if d.get("code_station"):
                    stations.append({
                        "code_station": d.get("code_station"),
                        "libelle": d.get("libelle_station"),
                        "code_commune": d.get("code_commune"),
                        "commune": d.get("libelle_commune"),
                        "uri": d.get("uri_station"),
                    })
                if d.get("date_operation"):
                    obs.append({
                        "code_station": d.get("code_station"),
                        "date": d.get("date_operation"),
                        "parametre": d.get("nom_commun_taxon") or d.get("nom_scientifique_taxon"),
                        "valeur": d.get("taille_individu"),
                        "unite": "mm",
                        "meta": json.dumps({
                            "nom_scientifique": d.get("nom_scientifique_taxon"),
                            "code_taxon": d.get("code_alternatif_taxon"),
                            "numero_individu": d.get("numero_individu"),
                            "poids_g": d.get("poids_individu"),
                        }, ensure_ascii=False),
                    })
        df_obs = pd.DataFrame(obs) if obs else pd.DataFrame()
        df_st = (pd.DataFrame(stations).drop_duplicates("code_station")
                 if stations else pd.DataFrame())
        return df_obs, df_st
