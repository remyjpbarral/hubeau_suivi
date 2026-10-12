"""Collecteur Poissons — API Poisson / État piscicole (base ASPE, OFB).

Point d'attention : le filtre libelle_entite_hydro est IGNORÉ par
l'endpoint observations (il renvoie toute la France). Pour une recherche
par cours d'eau, on passe donc par l'endpoint stations (libelle_station)
puis on interroge les observations station par station.
"""

import json

import pandas as pd

from .base import CollecteurBase

TAILLE_PAGE = 2000      # l'API accepte de grandes pages
TAILLE_PAGE_STATIONS = 200


class CollecteurPoissons(CollecteurBase):
    type_ = "poissons"
    libelle = "Poissons (État piscicole / ASPE)"

    def endpoints(self, **_):
        # endpoints définis dynamiquement dans collecte()
        return []

    def collecte(self, code_cours_eau=None, code_commune=None,
                 code_station=None, date_debut=None, date_fin=None, **_):
        # 1) résoudre les stations (endpoint stations, filtres fiables)
        params_st = {"fields": ("code_station,libelle_station,code_commune,"
                                "libelle_commune,coordonnee_x_point_prelevement,"
                                "coordonnee_y_point_prelevement,uri_station"),
                     "size": TAILLE_PAGE_STATIONS}
        if code_station:
            params_st["code_station"] = code_station
        elif code_commune:
            params_st["code_commune"] = code_commune
        elif code_cours_eau:
            # recherche des stations dont le nom contient le nom du cours d'eau
            params_st["libelle_station"] = code_cours_eau

        stations = []
        for page in self._pages("/v1/etat_piscicole/stations", params_st):
            for d in page.get("data", []):
                stations.append(self._station_row(d))

        if not stations:
            return pd.DataFrame(), pd.DataFrame()
        df_stations = (pd.DataFrame(stations)
                       .drop_duplicates(subset=["code_station"])
                       .reset_index(drop=True))
        # garde-fou : ne pas dépasser ~15 stations par requête utilisateur
        df_stations = df_stations.head(15)

        # 2) observations station par station (filtres fiables)
        params_obs = {"size": TAILLE_PAGE}
        if date_debut:
            params_obs["date_operation_min"] = date_debut
        if date_fin:
            params_obs["date_operation_max"] = date_fin

        obs = []
        for code in df_stations["code_station"]:
            for page in self._pages("/v1/etat_piscicole/observations",
                                    {**params_obs, "code_station": code}):
                for d in page.get("data", []):
                    row = self._obs_row(d)
                    if row:
                        obs.append(row)
        df_obs = pd.DataFrame(obs) if obs else pd.DataFrame()
        return df_obs, df_stations

    # --- transformations -------------------------------------------------

    def _station_row(self, d: dict) -> dict:
        from .. import geo
        lat, lon = geo.lambert93_vers_wgs84(
            d.get("coordonnee_x_point_prelevement"),
            d.get("coordonnee_y_point_prelevement"))
        return {
            "code_station": d.get("code_station"),
            "libelle": d.get("libelle_station"),
            "commune": d.get("libelle_commune"),
            "code_commune": d.get("code_commune"),
            "latitude": lat,
            "longitude": lon,
            "uri": d.get("uri_station"),
        }

    def _obs_row(self, d: dict) -> dict | None:
        if not d.get("date_operation"):
            return None
        meta = json.dumps({
            "nom_scientifique": d.get("nom_latin_taxon"),
            "code_taxon": d.get("code_alternatif_taxon"),
            "poids_g": d.get("poids_individu"),
        }, ensure_ascii=False)
        return {
            "code_station": d.get("code_station"),
            "date": d.get("date_operation"),
            "parametre": d.get("nom_commun_taxon") or d.get("nom_latin_taxon"),
            "valeur": d.get("taille_individu"),
            "unite": "mm",
            "meta": meta,
        }

    def transforme(self, brut: dict) -> pd.DataFrame:
        return pd.DataFrame()
