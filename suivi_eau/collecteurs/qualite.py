"""Collecteur Qualité des eaux — API Qualité des cours d'eau (Naïades).

Endpoints :
- /v2/qualite_rivieres/station_pc : stations physico-chimiques
- /v2/qualite_rivieres/analyse_pc : résultats d'analyses (nitrates, métaux…)
"""

import json

import pandas as pd

from .base import CollecteurBase

# Paramètres physico-chimiques les plus suivis (codes SANDRE)
PARAMETRES_SURVEILLES = {
    "1340": "Nitrates",
    "1335": "Nitrites",
    "1350": "Ammonium",
    "1365": "Phosphore total",
    "1382": "PO4",
    "1301": "O2 dissous",
    "1302": "DCO",
    "1303": "DBO5",
    "1720": "Conductivité",
    "1362": "Orthophosphates",
}


class CollecteurQualite(CollecteurBase):
    type_ = "qualite"
    libelle = "Qualité des eaux (Naïades)"

    def endpoints(self, code_cours_eau=None, code_commune=None,
                  code_station=None, date_debut=None, date_fin=None, **_):
        base_stations = {"fields": "code_station,libelle_station,nom_cours_eau,"
                                   "code_commune,libelle_commune,longitude,latitude,uri_station"}
        if code_commune:
            base_stations["code_commune"] = code_commune
        if code_station:
            base_stations["code_station"] = code_station

        base_analyses = {
            "fields": ("code_station,date_prelevement,code_parametre,"
                       "libelle_parametre,resultat,valeur_equiv_parametre,"
                       "symbole_unite,code_qualification,libelle_qualification")
        }
        if code_station:
            base_analyses["code_station"] = code_station
        if date_debut:
            base_analyses["date_debut_prelevement"] = date_debut
        if date_fin:
            base_analyses["date_fin_prelevement"] = date_fin

        endpoints = [("/v2/qualite_rivieres/station_pc", base_stations)]
        if code_station:
            endpoints.append(("/v2/qualite_rivieres/analyse_pc", base_analyses))
        return endpoints

    def collecte(self, code_cours_eau=None, code_commune=None, **filtres):
        # 1) stations filtrées par commune/cours d'eau
        chemin_st, params_st = self.endpoints(code_cours_eau=code_cours_eau,
                                              code_commune=code_commune,
                                              **filtres)[0]
        stations = []
        for page in self._pages(chemin_st, params_st):
            df = self._stations_df(page)
            if not df.empty:
                stations.append(df)
        df_stations = (pd.concat(stations, ignore_index=True)
                       .drop_duplicates("code_station") if stations else pd.DataFrame())

        # 2) analyses station par station
        analyses = []
        chemin_an = "/v2/qualite_rivieres/analyse_pc"
        codes = (df_stations["code_station"].tolist()[:50]
                 if not df_stations.empty else
                 ([filtres["code_station"]] if filtres.get("code_station") else []))
        for code in codes:
            params = {"code_station": code,
                      "fields": ("code_station,date_prelevement,code_parametre,"
                                 "libelle_parametre,resultat,valeur_equiv_parametre,"
                                 "symbole_unite,code_qualification,libelle_qualification")}
            if filtres.get("date_debut"):
                params["date_debut_prelevement"] = filtres["date_debut"]
            if filtres.get("date_fin"):
                params["date_fin_prelevement"] = filtres["date_fin"]
            for page in self._pages(chemin_an, params):
                df = self._analyses_df(page)
                if not df.empty:
                    analyses.append(df)
        df_analyses = (pd.concat(analyses, ignore_index=True) if analyses
                       else pd.DataFrame())
        return df_analyses, df_stations

    def transforme(self, brut: dict) -> pd.DataFrame:
        return pd.DataFrame()

    def _stations_df(self, brut: dict) -> pd.DataFrame:
        rows = []
        for d in brut.get("data", []):
            rows.append({
                "code_station": d.get("code_station"),
                "libelle": d.get("libelle_station"),
                "cours_eau": d.get("nom_cours_eau"),
                "commune": d.get("libelle_commune"),
                "code_commune": d.get("code_commune"),
                "latitude": d.get("latitude"),
                "longitude": d.get("longitude"),
                "uri": d.get("uri_station"),
            })
        return pd.DataFrame(rows)

    def _analyses_df(self, brut: dict) -> pd.DataFrame:
        rows = []
        for d in brut.get("data", []):
            if not d.get("date_prelevement"):
                continue
            valeur = d.get("valeur_equiv_parametre") or d.get("resultat")
            if valeur is None:
                continue
            meta = json.dumps({
                "code_parametre": d.get("code_parametre"),
                "code_qualification": d.get("code_qualification"),
            }, ensure_ascii=False)
            rows.append({
                "code_station": d.get("code_station"),
                "date": d.get("date_prelevement"),
                "parametre": d.get("libelle_parametre") or d.get("code_parametre"),
                "valeur": valeur,
                "unite": d.get("symbole_unite"),
                "meta": meta,
            })
        return pd.DataFrame(rows)
