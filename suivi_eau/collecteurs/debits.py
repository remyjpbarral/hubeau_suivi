"""Collecteur Débits — API Hydrométrie (HYDRO / Vigicrues).

Endpoints utilisés :
- /v2/hydrometrie/referentiel/stations : stations au code_entity Hydro
- /v2/hydrometrie/observations_tr : débits temps réel (Q, QmJ)
"""

import pandas as pd

from .base import CollecteurBase

# Voies (ex-code SANDRE) à écouter : Q = débit, QmJ = débit moyen journalier
VOIES = ("Q", "QmJ")


class CollecteurDebits(CollecteurBase):
    type_ = "debits"
    libelle = "Débits (Hydrométrie)"

    def endpoints(self, code_cours_eau=None, code_commune=None,
                  code_station=None, date_debut=None, date_fin=None, **_):
        params_stations = {"fields": "code_station,libelle_station,cours_eau,"
                                     "code_commune,libelle_commune,longitude,latitude,uri_station"}
        if code_cours_eau:
            params_stations["code_entity_hydro"] = code_cours_eau
        if code_commune:
            params_stations["code_commune"] = code_commune
        if code_station:
            params_stations["code_station"] = code_station

        params_obs = {"fields": "code_station,grandeur_hydro,date_obs_elab,"
                                "resultat_obs_elab,code_qualification"}
        if code_station:
            params_obs["code_station"] = code_station
        if date_debut:
            params_obs["date_debut_obs"] = date_debut
        if date_fin:
            params_obs["date_fin_obs"] = date_fin
        if code_cours_eau or code_commune:
            # L'API observations_tr n'accepte pas de filtre cours d'eau/commune :
            # on collecte station par station via _post_collecte.
            params_obs["_stations_a_resoudre"] = True  # marqueur interne

        return [
            ("/v2/hydrometrie/referentiel/stations", params_stations),
            ("/v2/hydrometrie/observations_tr", params_obs),
        ]

    def collecte(self, **filtres):
        # Résolution des stations d'abord, puis observations station par station
        # si filtré par cours d'eau/commune (limitation de l'API).
        if not (filtres.get("code_cours_eau") or filtres.get("code_commune")):
            return super().collecte(**filtres)

        chemin_st, params_st = self.endpoints(**filtres)[0]
        stations = []
        for page in self._pages(chemin_st, params_st):
            df = self._stations_df(page)
            if not df.empty:
                stations.append(df)
        df_stations = (pd.concat(stations, ignore_index=True) if stations
                       else pd.DataFrame())

        mesures = []
        params_obs = {"fields": "code_station,grandeur_hydro,date_obs_elab,"
                                "resultat_obs_elab,code_qualification"}
        if filtres.get("date_debut"):
            params_obs["date_debut_obs"] = filtres["date_debut"]
        if filtres.get("date_fin"):
            params_obs["date_fin_obs"] = filtres["date_fin"]
        for code in df_stations["code_station"].tolist()[:50]:
            chemin_obs = "/v2/hydrometrie/observations_tr"
            for page in self._pages(chemin_obs, {**params_obs,
                                                 "code_station": code}):
                df = self._mesures_df(page)
                if not df.empty:
                    mesures.append(df)
        df_mesures = (pd.concat(mesures, ignore_index=True) if mesures
                      else pd.DataFrame())
        return df_mesures, df_stations

    def transforme(self, brut: dict) -> pd.DataFrame:
        if "data" not in brut:
            return pd.DataFrame()
        if brut["data"] and "resultat_obs_elab" in brut["data"][0]:
            return self._mesures_df(brut)
        return self._stations_df(brut)

    def _mesures_df(self, brut: dict) -> pd.DataFrame:
        rows = []
        for d in brut.get("data", []):
            if d.get("grandeur_hydro") not in VOIES:
                continue
            rows.append({
                "code_station": d.get("code_station"),
                "date": d.get("date_obs_elab") or d.get("date_obs"),
                "parametre": d.get("grandeur_hydro"),
                "valeur": d.get("resultat_obs_elab") or d.get("resultat_obs"),
                "unite": "l/s" if d.get("grandeur_hydro") == "Q" else "m3/s",
                "meta": d.get("code_qualification"),
            })
        return pd.DataFrame(rows)

    def _stations_df(self, brut: dict) -> pd.DataFrame:
        rows = []
        for d in brut.get("data", []):
            rows.append({
                "code_station": d.get("code_station"),
                "libelle": d.get("libelle_station"),
                "cours_eau": (d.get("cours_eau") or {}).get("nom_cours_eau")
                             if isinstance(d.get("cours_eau"), dict)
                             else d.get("nom_cours_eau"),
                "commune": d.get("libelle_commune"),
                "code_commune": d.get("code_commune"),
                "latitude": d.get("latitude"),
                "longitude": d.get("longitude"),
                "uri": d.get("uri_station"),
            })
        return pd.DataFrame(rows)
