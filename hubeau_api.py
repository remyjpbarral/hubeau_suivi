import json
import time
import urllib.parse
import urllib.request

import pandas as pd
import streamlit as st

BASE_URL = "https://hubeau.eaufrance.fr/api/v2"


@st.cache_data(show_spinner=False, ttl=3600)
def api_get(endpoint, params, size=200, max_records=50000, timeout=30):
    """Interroge l'API Hub'Eau avec pagination automatique."""
    records = []
    page = 1
    size = min(size, 200)
    while len(records) < max_records:
        query = dict(params)
        query.update({"format": "json", "size": size, "page": page})
        url = f"{BASE_URL}/{endpoint}?{urllib.parse.urlencode(query)}"
        for attempt in range(3):
            try:
                with urllib.request.urlopen(url, timeout=timeout) as resp:
                    payload = json.load(resp)
                break
            except Exception as exc:
                if attempt == 2:
                    raise RuntimeError(f"Échec de la requête API : {exc}") from exc
                time.sleep(2 * (attempt + 1))
        data = payload.get("data", [])
        records.extend(data)
        count = payload.get("count", len(records))
        if page * size >= count or not data:
            break
        page += 1
    return records


@st.cache_data(show_spinner=False, ttl=3600)
def search_stations(commune=None, code_departement=None, nom_cours_eau=None,
                   longitude=None, latitude=None, distance=10):
    filters = {}
    if commune:
        filters["libelle_commune"] = commune
    if code_departement:
        filters["code_departement"] = code_departement
    if nom_cours_eau:
        filters["nom_cours_eau"] = nom_cours_eau
    if longitude:
        filters.update({"longitude": longitude, "latitude": latitude, "distance": distance})
    if not filters:
        return []
    return api_get("qualite_rivieres/station_pc", filters)


@st.cache_data(show_spinner=False, ttl=600)
def get_analyses(code_station, libelle_parametre=None, date_debut=None, date_fin=None):
    params = {"code_station": code_station}
    if libelle_parametre:
        params["libelle_parametre"] = libelle_parametre
    if date_debut:
        params["date_debut_prelevement"] = str(date_debut)
    if date_fin:
        params["date_fin_prelevement"] = str(date_fin)
    return api_get("qualite_rivieres/analyse_pc", params)


def analyses_to_df(records):
    if not records:
        return pd.DataFrame()
    df = pd.DataFrame([{
        "station": r.get("code_station"),
        "libelle_station": r.get("libelle_station"),
        "date": r.get("date_prelevement"),
        "parametre": r.get("libelle_parametre"),
        "resultat": r.get("resultat"),
        "unite": r.get("symbole_unite"),
        "qualification": r.get("libelle_qualification"),
    } for r in records])
    df["date"] = pd.to_datetime(df["date"], errors="coerce")
    return df.dropna(subset=["date", "resultat"])
