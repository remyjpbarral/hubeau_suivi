"""CLI de collecte : interroge les API et alimente la base SQLite.

Exemples :
    python -m suivi_eau.collect --cours-eau "la loire" --all
    python -m suivi_eau.collect --commune 44055 --debits --qualite
    python -m suivi_eau.collect --station 05001800 --poissons
"""

import argparse
import sys
from datetime import date

from . import config, db
from .collecteurs import SOURCES


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="suivi_eau.collect",
                                description="Collecte des données cours d'eau "
                                            "(Hub'Eau) vers SQLite.")
    filtres = p.add_argument_group("Filtres géographiques / temporels")
    filtres.add_argument("--cours-eau", help="nom ou code du cours d'eau (ex: 'la loire')")
    filtres.add_argument("--commune", help="code INSEE de la commune (ex: 44055)")
    filtres.add_argument("--station", help="code d'une station précise")
    filtres.add_argument("--depuis", type=int, default=config.ANNEES_DEFAUT,
                         help="années en arrière (défaut : %(default)s)")

    sources = p.add_argument_group("Sources à collecter")
    for cle in SOURCES:
        sources.add_argument(f"--{cle}", action="store_true",
                              help=f"collecter {SOURCES[cle].libelle}")
    sources.add_argument("--all", action="store_true", help="toutes les sources")
    return p


def resoudre_code_commune(commune: str) -> str:
    """Si l'utilisateur fournit un nom, on le laisse tel quel : les API
    acceptent aussi libelle_commune. Un code INSEE numérique passe tel quel."""
    return commune


def principal(argv=None) -> int:
    args = parser().parse_args(argv)
    if not (args.all or any(getattr(args, c) for c in SOURCES)):
        parser().error("précisez au moins une source (--debits, --poissons, "
                       "--qualite) ou --all")

    actives = [c for c in SOURCES if args.all or getattr(args, c)]
    date_debut = f"{date.today().replace(year=date.today().year - args.depuis):%Y-%m-%d}"

    filtres = {}
    if args.cours_eau:
        filtres["code_cours_eau"] = args.cours_eau
    if args.commune:
        filtres["code_commune"] = resoudre_code_commune(args.commune)
    if args.station:
        filtres["code_station"] = args.station
    filtres["date_debut"] = date_debut

    con = db.connexion()
    code_retour = 0
    try:
        for cle in actives:
            cls = SOURCES[cle]
            print(f"[{cls.libelle}] collecte en cours…")
            try:
                mesures, stations = cls().collecte(**filtres)
            except Exception as exc:
                print(f"  ✗ erreur : {exc}", file=sys.stderr)
                db.journaliser(con, cle, f"erreur : {exc}")
                con.commit()
                code_retour = 1
                continue
            nb_st = db.upsert_stations(con, stations, cls.type_)
            nb_me = db.insert_mesures(con, mesures, cls.type_)
            con.commit()
            db.journaliser(con, cle, f"OK : {nb_me} mesures, {nb_st} stations")
            print(f"  ✓ {nb_me} mesures, {nb_st} stations")
    finally:
        con.close()
    return code_retour


if __name__ == "__main__":
    sys.exit(principal())
