"""Registre des collecteurs disponibles (point d'extension du projet)."""

from .base import CollecteurBase
from .debits import CollecteurDebits
from .poissons import CollecteurPoissons
from .qualite import CollecteurQualite

SOURCES = {
    "debits": CollecteurDebits,
    "poissons": CollecteurPoissons,
    "qualite": CollecteurQualite,
}

__all__ = ["SOURCES", "CollecteurBase"]
