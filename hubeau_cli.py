#!/usr/bin/env python3
"""Outil d'extraction Hub'Eau (portail des données sur l'eau de l'État français).

APIs utilisées (ouvertes, sans clé) :
  - Qualité des cours d'eau (Naïades) : /api/v2/qualite_rivieres/{station_pc,analyse_pc}
  - Hydrométrie (HOOM) : /api/v2/hydrometrie/observations_tr

Exemples :
  # Chercher des stations
  python hubeau_cli.py stations --commune Longuyon
  # Exporter les analyses de nitrates d'une station en CSV
  python hubeau_cli.py analyses --station 02115725 --parametre Nitrates --depuis 2018-01-01 --out nitrates.csv
  # Générer tableau (CSV) + graphique (PNG)
  python hubeau_cli.py rapport --station 02115725 --parametres Nitrates,"Phosphore total" --depuis 2018-01-01
"""
import argparse
import json
import sys
import time
import urllib.parse
import urllib.request

BASE_URL = "https://hubeau.eaufrance.fr/api/v2"


def get(endpoint, params, size=200, max_records=50000, timeout=30):
    """Interroge l'API Hub'Eau avec pagination automatique. Retourne une liste de dictionnaires."""
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
                    raise RuntimeError(f"Échec de la requête {url} : {exc}") from exc
                time.sleep(2 * (attempt + 1))
        data = payload.get("data", [])
        records.extend(data)
        count = payload.get("count", len(records))
        if page * size >= count or not data:
            break
        page += 1
    return records


def search_stations(**filters):
    return get("qualite_rivieres/station_pc", filters)


def get_analyses(code_station, libelle_parametre=None, date_debut=None, date_fin=None):
    params = {"code_station": code_station}
    if libelle_parametre:
        params["libelle_parametre"] = libelle_parametre
    if date_debut:
        params["date_debut_prelevement"] = date_debut
    if date_fin:
        params["date_fin_prelevement"] = date_fin
    return get("qualite_rivieres/analyse_pc", params)


def to_rows(records):
    rows = []
    for rec in records:
        rows.append({
            "station": rec.get("code_station"),
            "libelle_station": rec.get("libelle_station"),
            "date": rec.get("date_prelevement"),
            "parametre": rec.get("libelle_parametre"),
            "resultat": rec.get("resultat"),
            "unite": rec.get("symbole_unite"),
            "qualification": rec.get("libelle_qualification"),
            "latitude": rec.get("latitude"),
            "longitude": rec.get("longitude"),
        })
    return rows


def write_csv(rows, path):
    if not rows:
        print("Aucune donnée à écrire.")
        return
    import csv
    with open(path, "w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=rows[0].keys(), delimiter=";")
        writer.writeheader()
        writer.writerows(rows)
    print(f"{len(rows)} lignes écrites -> {path}")


def plot_series(df, title, out_path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.dates as mdates
    import matplotlib.pyplot as plt

    fig, ax = plt.subplots(figsize=(12, 6))
    for (param, sub) in df.groupby("parametre"):
        sub = sub.sort_values("date")
        unite = sub["unite"].dropna().iloc[0] if not sub["unite"].dropna().empty else ""
        ax.plot(sub["date"], sub["resultat"], marker="o", ms=3, lw=1, label=f"{param} ({unite})")
    ax.set_title(title)
    ax.set_xlabel("Date de prélèvement")
    ax.legend()
    ax.grid(True, alpha=0.3)
    ax.xaxis.set_major_formatter(mdates.DateFormatter("%m/%Y"))
    fig.autofmt_xdate()
    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    print(f"Graphique -> {out_path}")


def cmd_stations(args):
    filters = {}
    for key in ("commune", "departement", "cours_eau", "bassin"):
        val = getattr(args, key)
        if val:
            filters[{"commune": "libelle_commune", "departement": "code_departement",
                     "cours_eau": "nom_cours_eau", "bassin": "code_bassin"}[key]] = val
    if args.lon:
        filters.update({"longitude": args.lon, "latitude": args.lat, "distance": args.distance})
    records = search_stations(**filters)
    for rec in records:
        print(f"{rec['code_station']}  {rec['libelle_station']}  "
              f"({rec.get('libelle_commune', '?')} / {rec.get('nom_cours_eau', '?')})")
    print(f"\n{len(records)} station(s)")


def cmd_analyses(args):
    records = get_analyses(args.station, args.parametre, args.depuis, args.jusqua)
    rows = to_rows(records)
    if args.out:
        write_csv(rows, args.out)
    else:
        for r in rows[:20]:
            print(f"{r['date']}  {r['parametre']}: {r['resultat']} {r['unite']}")
        print(f"\n{len(rows)} analyse(s)")


def cmd_rapport(args):
    import pandas as pd
    frames = []
    for param in [p.strip() for p in args.parametres.split(",") if p.strip()]:
        recs = get_analyses(args.station, param, args.depuis, args.jusqua)
        if not recs:
            print(f"Aucune donnée pour '{param}'")
        frames.extend(to_rows(recs))
    if not frames:
        sys.exit("Aucune donnée récupérée.")
    df = pd.DataFrame(frames)
    df["date"] = pd.to_datetime(df["date"], errors="coerce")
    df = df.dropna(subset=["date", "resultat"])
    df = df[df["qualification"].isin(args.qualif.split(","))] if args.qualif else df

    station_label = df["libelle_station"].dropna().iloc[0] if not df["libelle_station"].dropna().empty else args.station
    csv_path = f"{args.prefix}_{args.station}.csv"
    png_path = f"{args.prefix}_{args.station}.png"
    write_csv(df.drop(columns=["date"]).assign(date=df["date"].dt.strftime("%Y-%m-%d")).to_dict("records"), csv_path)
    plot_series(df, f"Station {station_label} ({args.station})", png_path)

    print("\nStatistiques par paramètre :")
    print(df.groupby("parametre")["resultat"].describe()[["count", "mean", "min", "max"]].round(2))


def main():
    parser = argparse.ArgumentParser(description="Extraction automatisée de données Hub'Eau")
    sub = parser.add_subparsers(dest="command", required=True)

    p_st = sub.add_parser("stations", help="Rechercher des stations de suivi")
    p_st.add_argument("--commune")
    p_st.add_argument("--departement")
    p_st.add_argument("--cours-eau")
    p_st.add_argument("--bassin")
    p_st.add_argument("--lon", type=float)
    p_st.add_argument("--lat", type=float)
    p_st.add_argument("--distance", type=float, default=10)
    p_st.set_defaults(func=cmd_stations)

    p_an = sub.add_parser("analyses", help="Récupérer les analyses d'une station")
    p_an.add_argument("--station", required=True)
    p_an.add_argument("--parametre")
    p_an.add_argument("--depuis")
    p_an.add_argument("--jusqua")
    p_an.add_argument("--out", help="Fichier CSV de sortie")
    p_an.set_defaults(func=cmd_analyses)

    p_rp = sub.add_parser("rapport", help="Tableau (CSV) + graphique (PNG) pour une station")
    p_rp.add_argument("--station", required=True)
    p_rp.add_argument("--parametres", required=True, help="Paramètres séparés par des virgules")
    p_rp.add_argument("--depuis")
    p_rp.add_argument("--jusqua")
    p_rp.add_argument("--qualif", help="Codes de qualification à conserver, séparés par virgules")
    p_rp.add_argument("--prefix", default="suivi")
    p_rp.set_defaults(func=cmd_rapport)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
