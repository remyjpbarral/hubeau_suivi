"""Paramètres globaux de l'outil de suivi des cours d'eau."""

from pathlib import Path

# Racine du projet
RACINE = Path(__file__).resolve().parent.parent

# Base de données SQLite
CHEMIN_DB = RACINE / "data" / "suivi_eau.sqlite"

# Base des API Hub'Eau
BASE_HUBEAU = "https://hubeau.eaufrance.fr/api"

# Pagination
TAILLE_PAGE = 2000         # éléments par requête (les API acceptent de grandes pages)
MAX_PAGES = 50             # garde-fou anti-boucle
RETRIES = 3                # tentatives par requête
PAUSE_RETRIE_S = 2         # délai entre tentatives
PAUSE_PAGE_S = 0.2         # délai entre pages (politesse envers l'API)

# Période par défaut pour la collecte (années en arrière)
ANNEES_DEFAUT = 10
