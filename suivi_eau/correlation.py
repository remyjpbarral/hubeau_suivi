"""Corrélation : trouver la station hydrométrique la plus proche d'un point.

Utilise l'API Hydrométrie (référentiel des stations, recherche par bbox)
puis trie les résultats par distance réelle.
"""

import pandas as pd
import requests

from . import geo

BASE_HUBEAU = "https://hubeau.eaufrance.fr/api"
URL_STATIONS = f"{BASE_HUBEAU}/v2/hydrometrie/referentiel/stations"


def stations_hydro_autour(lat: float, lon: float,
                          rayon_km: float = 30.0) -> pd.DataFrame:
    """Stations hydrométriques dans un rayon donné, triées par distance."""
    bbox = geo.bbox_autour(lat, lon, rayon_km)
    try:
        rep = requests.get(
            URL_STATIONS,
            params={"bbox": bbox, "size": 200,
                    "fields": ("code_station,libelle_station,cours_eau_eu,"
                               "code_commune,libelle_commune,"
                               "longitude,latitude,uri_station")},
            timeout=30)
        rep.raise_for_status()
        data = rep.json().get("data", [])
    except (requests.RequestException, ValueError):
        return pd.DataFrame()
    rows = []
    for d in data:
        lat_s, lon_s = d.get("latitude"), d.get("longitude")
        rows.append({
            "code_station": d.get("code_station"),
            "libelle": d.get("libelle_station"),
            "cours_eau": d.get("cours_eau_eu"),
            "commune": d.get("libelle_commune"),
            "latitude": lat_s,
            "longitude": lon_s,
            "distance_km": geo.distance_km(lat, lon, lat_s, lon_s),
        })
    df = pd.DataFrame(rows)
    if not df.empty:
        df = df[df["distance_km"] <= rayon_km].sort_values("distance_km")
    return df.reset_index(drop=True)
