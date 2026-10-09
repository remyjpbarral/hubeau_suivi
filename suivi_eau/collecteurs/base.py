"""Classe de base des collecteurs : pagination, retries, normalisation."""

import time
from abc import ABC, abstractmethod

import pandas as pd
import requests

from .. import config


class CollecteurBase(ABC):
    """Collecteur générique d'une API Hub'Eau paginée."""

    type_: str = ""          # identifiant de la source (clé 'type' en base)
    libelle: str = ""        # nom lisible

    def __init__(self, session: requests.Session | None = None):
        self.session = session or requests.Session()

    # --- API à implémenter -------------------------------------------------

    @abstractmethod
    def endpoints(self, **filtres) -> list[tuple[str, dict]]:
        """Retourne [(url_sans_base, params), ...] à interroger."""

    @abstractmethod
    def transforme(self, brut: dict) -> pd.DataFrame:
        """Transforme une réponse JSON brute en DataFrame normalisé
        (colonnes : code_station, date, parametre, valeur, unite, meta)
        ou en DataFrame de stations (colonne code_station + libelle…)."""

    # --- Mécanique commune --------------------------------------------------

    def collecte(self, **filtres) -> tuple[pd.DataFrame, pd.DataFrame]:
        """Interroge tous les endpoints (avec pagination) et retourne
        (mesures, stations) normalisés."""
        mesures, stations = [], []
        for chemin, params in self.endpoints(**filtres):
            for page in self._pages(chemin, params):
                df = self.transforme(page)
                if df is None or df.empty:
                    continue
                if "valeur" in df.columns:
                    mesures.append(df)
                else:
                    stations.append(df)
        return (
            pd.concat(mesures, ignore_index=True) if mesures else pd.DataFrame(),
            pd.concat(stations, ignore_index=True) if stations else pd.DataFrame(),
        )

    def _pages(self, chemin: str, params: dict):
        url = f"{config.BASE_HUBEAU}{chemin}"
        for page in range(1, config.MAX_PAGES + 1):
            params_page = {**params, "page": page, "size": config.TAILLE_PAGE}
            data = self._requete(url, params_page)
            if not data or not data.get("data"):
                return
            yield data
            if len(data.get("data", [])) < config.TAILLE_PAGE:
                return
            time.sleep(config.PAUSE_PAGE_S)

    def _requete(self, url: str, params: dict) -> dict | None:
        for tentative in range(1, config.RETRIES + 1):
            try:
                rep = self.session.get(url, params=params, timeout=30)
                if rep.status_code == 200:
                    return rep.json()
                if rep.status_code in (429, 500, 502, 503):
                    time.sleep(config.PAUSE_RETRIE_S * tentative)
                    continue
                rep.raise_for_status()
            except requests.RequestException:
                if tentative == config.RETRIES:
                    return None
                time.sleep(config.PAUSE_RETRIE_S * tentative)
        return None
