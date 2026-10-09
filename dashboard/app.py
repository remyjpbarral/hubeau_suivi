"""Appli web de suivi des cours d'eau — accessible depuis n'importe quel appareil.

Interroge directement les API Hub'Eau (OFB, Agences de l'eau, SCHAPI) :
- Débits (Hydrométrie)
- Poissons (État piscicole / ASPE)
- Qualité des eaux (Naïades)
"""

import sys
from datetime import date, timedelta
from pathlib import Path

import pandas as pd
import requests
import streamlit as st

RACINE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RACINE))

from suivi_eau.collecteurs import SOURCES  # noqa: E402

st.set_page_config(page_title="Suivi des cours d'eau",
                   page_icon="🐟", layout="wide")

TITRES = {c: cls.libelle for c, cls in SOURCES.items()}
DEFAUTS = {"nom_commune": "Saint-Étienne", "cours_eau": "la loire"}


@st.cache_data(ttl=86400, show_spinner=False)
def cherche_communes(nom: str) -> list[tuple[str, str, str]]:
    """Autocomplétion des communes via GéoAPI (API officielle de l'État).
    Retourne [(code INSEE, nom, département), ...]."""
    if len(nom.strip()) < 2:
        return []
    try:
        rep = requests.get(
            "https://geo.api.gouv.fr/communes",
            params={"nom": nom.strip(), "limit": 10,
                    "fields": "codeDepartement"},
            timeout=10)
        rep.raise_for_status()
        return [(c["code"], c["nom"],
                 f"{c.get('codeDepartement', '')} — {c['nom']} ({c['code']})")
                for c in rep.json()]
    except requests.RequestException:
        return []


@st.cache_data(ttl=3600, show_spinner=False)
def collecte(source: str, filtres: dict) -> tuple[pd.DataFrame, pd.DataFrame]:
    return SOURCES[source]().collecte(**filtres)


def filtres_communs() -> dict:
    """Barre latérale : choix du lieu et de la période."""
    st.header("🔍 Rechercher")

    lieu = st.radio("Rechercher par", ["Commune", "Cours d'eau"],
                    label_visibility="collapsed")
    filtres = {}
    if lieu == "Commune":
        saisie = st.text_input("Nom de la commune",
                               value=DEFAUTS["nom_commune"])
        communes = cherche_communes(saisie)
        if not communes:
            if len(saisie.strip()) >= 2:
                st.warning("Commune introuvable — vérifiez l'orthographe.")
            return None
        _, _, etiquette = st.radio(
            "Communes trouvées", communes,
            format_func=lambda c: c[2], label_visibility="collapsed")
        filtres["code_commune"] = etiquette[0]
        filtres["nom_commune"] = etiquette[1]
        st.caption(f"✓ Commune sélectionnée : {etiquette[1]}")
    else:
        filtres["code_cours_eau"] = st.text_input(
            "Nom du cours d'eau", value=DEFAUTS["cours_eau"],
            help="Ex. « la loire », « la crusnes »")

    annees = st.slider("Période (années en arrière)", 1, 30, 5)
    filtres["date_debut"] = (date.today() - timedelta(days=365 * annees)
                             ).strftime("%Y-%m-%d")

    st.divider()
    st.caption("Données officielles : [Hub'Eau](https://hubeau.eaufrance.fr) — "
               "OFB, Agences de l'eau, SCHAPI.")
    return filtres


def main() -> None:
    st.title("🐟 Suivi des cours d'eau")
    st.caption("Débits, poissons et qualité des eaux — données officielles "
                "françaises, accessibles partout.")

    with st.sidebar:
        filtres = filtres_communs()

    if not filtres:
        st.info("Tapez le nom d'une commune ou d'un cours d'eau dans la "
                "barre latérale pour commencer.")
        return

    sources_ok = []
    echecs = []
    progres = st.status("Interrogation des API Hub'Eau…")
    for cle, cls in SOURCES.items():
        try:
            mesures, stations = collecte(cle, filtres.copy())
            if not stations.empty:
                sources_ok.append((cle, mesures, stations))
        except Exception as exc:
            echecs.append(f"{cls.libelle} : {exc}")
    progres.update(label="Données chargées", state="complete")

    if echecs:
        with st.expander(f"⚠️ {len(echecs)} source(s) indisponible(s)"):
            for e in echecs:
                st.write(f"- {e}")

    if not sources_ok:
        st.warning("Aucune donnée trouvée pour cette recherche. Vérifiez le "
                   "code INSEE ou le nom du cours d'eau.")
        return

    onglets = st.tabs([TITRES[cle] for cle, _, _ in sources_ok])
    for (cle, mesures, stations), onglet in zip(sources_ok, onglets):
        with onglet:
            if mesures.empty:
                st.info(f"{len(stations)} station(s) trouvée(s), mais aucune "
                        "mesure sur la période choisie. Augmentez la période "
                        "dans la barre latérale.")
                continue
            options = stations["code_station"].tolist()
            etiquette = {r["code_station"]:
                         f"{r.get('libelle') or r['code_station']}"
                         + (f" — {r['commune']}" if r.get("commune") else "")
                         for _, r in stations.iterrows()}
            code = st.selectbox("Station", options,
                                format_func=etiquette.get)
            df = mesures[mesures["code_station"] == code].copy()
            if df.empty:
                st.info("Aucune mesure sur cette station.")
                continue
            df["date"] = pd.to_datetime(df["date"], errors="coerce")
            df = df.dropna(subset=["date"]).sort_values("date")

            c1, c2, c3 = st.columns(3)
            with c1:
                st.metric("Mesures", len(df))
            with c2:
                st.metric("Depuis", str(df["date"].min().date()))
            with c3:
                st.metric("Jusqu'à", str(df["date"].max().date()))

            parametres = sorted(df["parametre"].dropna().unique())
            if cle == "poissons":
                sel = st.multiselect("Espèces", parametres,
                                     default=parametres[:5])
                if sel:
                    st.subheader("Taille moyenne (mm)")
                    st.line_chart(df[df["parametre"].isin(sel)]
                                  .pivot_table(index="date",
                                               columns="parametre",
                                               values="valeur",
                                               aggfunc="mean"))
                st.subheader("Nombre d'individus par espèce")
                ab = (df.groupby("parametre").size()
                        .rename("individus").reset_index()
                        .sort_values("individus", ascending=False))
                st.bar_chart(ab.set_index("parametre")["individus"])
            else:
                sel = st.multiselect("Paramètres", parametres,
                                     default=parametres[:3])
                if sel:
                    st.line_chart(df[df["parametre"].isin(sel)]
                                  .pivot_table(index="date",
                                               columns="parametre",
                                               values="valeur",
                                               aggfunc="mean"))
            with st.expander("Données brutes"):
                st.dataframe(df, use_container_width=True)


if __name__ == "__main__":
    main()
