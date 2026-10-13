"""Collecteur Débits — API Hydrométrie v2 (HYDRO / Vigicrues).

Enseignements (mesurés + documentation officielle) :
- le paramètre de filtrage des observations est code_entite (et non
  code_station, qui est ignoré) ;
- observations_tr ne garde qu'un mois d'historique ; pour de vraies
  séries on utilise obs_elab (grandeurs élaborées : QmJ = débit moyen
  journalier, historique complet depuis 1900) ;
- les filtres code_commune / code_entity_hydro du référentiel sont
  ignorés ; la recherche de stations se fait par bbox (fiable) ;
- l'API est lente (~10-20 s/requête) : on limite strictement le nombre
  d'appels (une station de débit, celle la plus proche du point cherché).
"""

import pandas as pd
import requests

from .. import geo
from .base import CollecteurBase


class CollecteurDebits(CollecteurBase):
    type_ = "debits"
    libelle = "Débits (Hydrométrie)"

    def endpoints(self, **_):
        # définis dynamiquement dans collecte()
        return []

    def collecte(self, code_cours_eau=None, code_commune=None,
                 code_station=None, date_debut=None, date_fin=None, **_):
        # 1) point de recherche
        if code_station:
            centre = self._centre_depuis_station(code_station)
        else:
            centre = None
            if code_commune:
                centre = self._centre_depuis_commune(code_commune)
            elif code_cours_eau:
                centre = self._centre_depuis_cours_eau(code_cours_eau)
            if centre is None:
                return pd.DataFrame(), pd.DataFrame()

        # 2) stations hydrométriques par bbox autour du point
        lat, lon = centre
        bbox = geo.bbox_autour(lat, lon, rayon_km=15)
        stations = []
        for page in self._pages("/v2/hydrometrie/referentiel/stations",
                               {"bbox": bbox, "size": 100}):
            df = self._stations_df(page)
            if not df.empty:
                stations.append(df)
        if not stations:
            return pd.DataFrame(), pd.DataFrame()
        df_stations = (pd.concat(stations, ignore_index=True)
                       .drop_duplicates("code_station").reset_index(drop=True))
        df_stations["distance_km"] = df_stations.apply(
            lambda r: geo.distance_km(lat, lon, r["latitude"], r["longitude"]),
            axis=1)
        df_stations = df_stations.sort_values("distance_km").reset_index(drop=True)

        # 3) débits journaliers (obs_elab, QmJ) pour la station la plus proche
        code_obs = df_stations.iloc[0]["code_station"]
        params_obs = {"code_entite": code_obs, "grandeur_hydro": "QmJ",
                      "size": 2000,
                      "fields": ("code_station,grandeur_hydro,date_obs_elab,"
                                 "resultat_obs_elab")}
        if date_debut:
            params_obs["date_debut_obs"] = date_debut
        if date_fin:
            params_obs["date_fin_obs"] = date_fin
        mesures = []
        for page in self._pages("/v2/hydrometrie/obs_elab", params_obs):
            df = self._mesures_df(page)
            if not df.empty:
                mesures.append(df)
        df_mesures = (pd.concat(mesures, ignore_index=True) if mesures
                      else pd.DataFrame())
        return df_mesures, df_stations

    # --- localisation -------------------------------------------------------

    def _centre_depuis_station(self, code_station: str):
        for page in self._pages("/v2/hydrometrie/referentiel/stations",
                                {"code_station": code_station, "size": 1}):
            for d in page.get("data", []):
                return d.get("latitude"), d.get("longitude")
        return None

    def _centre_depuis_commune(self, code_commune: str):
        try:
            rep = requests.get(
                "https://geo.api.gouv.fr/communes",
                params={"code": code_commune, "fields": "centre"},
                timeout=10)
            data = rep.json()
            if data:
                c = data[0].get("centre") or {}
                return c.get("y"), c.get("x")   # GéoAPI : y = lat, x = lon
        except (requests.RequestException, ValueError, IndexError):
            pass
        return None

    def _centre_depuis_cours_eau(self, nom_cours_eau: str):
        """Localise un cours d'eau via une station de suivi poissons dont
        le libellé contient le nom du cours d'eau."""
        try:
            rep = self.session.get(
                "https://hubeau.eaufrance.fr/api/v1/etat_piscicole/stations",
                params={"libelle_station": nom_cours_eau, "size": 1,
                        "fields": ("coordonnee_x_point_prelevement,"
                                   "coordonnee_y_point_prelevement")},
                timeout=15)
            data = rep.json().get("data", [])
            if data:
                x = data[0].get("coordonnee_x_point_prelevement")
                y = data[0].get("coordonnee_y_point_prelevement")
                return geo.lambert93_vers_wgs84(x, y)
        except (requests.RequestException, ValueError, IndexError):
            pass
        return None

    # --- transformations ----------------------------------------------------

    def _stations_df(self, brut: dict) -> pd.DataFrame:
        rows = []
        for d in brut.get("data", []):
            rows.append({
                "code_station": d.get("code_station"),
                "libelle": d.get("libelle_station"),
                "cours_eau": (d.get("cours_eau_eu")
                              or d.get("libelle_cours_eau")),
                "commune": d.get("libelle_commune"),
                "code_commune": d.get("code_commune"),
                "latitude": d.get("latitude"),
                "longitude": d.get("longitude"),
                "uri": d.get("uri_station"),
            })
        return pd.DataFrame(rows)

    def _mesures_df(self, brut: dict) -> pd.DataFrame:
        rows = []
        for d in brut.get("data", []):
            rows.append({
                "code_station": d.get("code_station"),
                "date": d.get("date_obs_elab") or d.get("date_obs"),
                "parametre": "Débit moyen journalier",
                "valeur": d.get("resultat_obs_elab") or d.get("resultat_obs"),
                "unite": "l/s",
                "meta": d.get("grandeur_hydro"),
            })
        return pd.DataFrame(rows)

    def transforme(self, brut: dict) -> pd.DataFrame:
        return pd.DataFrame()
