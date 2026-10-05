import io

import pandas as pd
import streamlit as st

from hubeau_api import analyses_to_df, get_analyses, search_stations

st.set_page_config(page_title="Suivi environnemental - Hub'Eau", page_icon="💧", layout="wide")
st.title("💧 Suivi environnemental des stations")
st.caption("Données issues du portail public [Hub'Eau](https://hubeau.eaufrance.fr) (base Naïades — qualité des cours d'eau).")

with st.sidebar:
    st.header("🔍 Rechercher une station")
    mode = st.radio("Mode de recherche", ["Par commune", "Par département", "Par cours d'eau", "Autour d'un point"], label_visibility="collapsed")
    if mode == "Par commune":
        commune = st.text_input("Nom de la commune", "Longuyon")
        stations = search_stations(commune=commune)
    elif mode == "Par département":
        code_departement = st.text_input("Code département", "59")
        stations = search_stations(code_departement=code_departement)
    elif mode == "Par cours d'eau":
        nom_cours_eau = st.text_input("Nom du cours d'eau", "La Crusnes")
        stations = search_stations(nom_cours_eau=nom_cours_eau)
    else:
        col1, col2 = st.columns(2)
        lon = col1.number_input("Longitude", value=3.04, step=0.01)
        lat = col2.number_input("Latitude", value=50.78, step=0.01)
        distance = st.number_input("Distance (km)", value=10.0, step=1.0)
        stations = search_stations(longitude=lon, latitude=lat, distance=distance)

if not stations:
    st.info("Aucune station trouvée pour ce critère. Modifiez la recherche dans le panneau de gauche.")
    st.stop()

df_stations = pd.DataFrame([{
    "code_station": s["code_station"],
    "libelle_station": s["libelle_station"],
    "commune": s.get("libelle_commune"),
    "cours_eau": s.get("nom_cours_eau"),
} for s in stations])

with st.expander(f"📍 {len(stations)} station(s) trouvée(s)", expanded=len(stations) <= 10):
    st.dataframe(df_stations, use_container_width=True, hide_index=True)

st.header("📊 Analyser une station")
station_labels = {f"{s['code_station']} — {s['libelle_station']}": s["code_station"] for s in stations}
selected_label = st.selectbox("Station de suivi", list(station_labels.keys()))
code_station = station_labels[selected_label]

col_depuis, col_fin = st.columns(2)
depuis = col_depuis.date_input("Date de début", pd.Timestamp("2020-01-01").date())
jusqua = col_fin.date_input("Date de fin", pd.Timestamp.today().date())

parametres_dispo = ["Nitrates", "Chlorures", "Matières en suspension", "Phosphore total",
                    "Ammonium", "Température de l'eau", "pH", "Oxygène dissous", "Demande chimique en oxygène"]
parametres = st.multiselect("Paramètres à analyser", parametres_dispo, default=["Nitrates"])

if st.button("🚀 Générer l'analyse", type="primary", disabled=not parametres):
    frames = []
    progress = st.progress(0.0, text="Récupération des analyses…")
    for i, param in enumerate(parametres):
        recs = get_analyses(code_station, libelle_parametre=param, date_debut=depuis, date_fin=jusqua)
        frames.append(analyses_to_df(recs))
        progress.progress((i + 1) / len(parametres), text=f"Récupération : {param}")
    progress.empty()
    df = pd.concat([f for f in frames if not f.empty], ignore_index=True) if any(not f.empty for f in frames) else pd.DataFrame()

    if df.empty:
        st.warning("Aucune donnée disponible pour ces paramètres sur la période choisie.")
        st.stop()

    st.subheader(f"📈 Évolution temporelle — {selected_label}")
    import plotly.express as px
    fig = px.line(df, x="date", y="resultat", color="parametre",
                  labels={"resultat": "Résultat", "date": "Date de prélèvement", "parametre": "Paramètre"},
                  markers=True)
    fig.update_layout(height=500, hovermode="x unified")
    st.plotly_chart(fig, use_container_width=True)

    tab_stats, tab_data = st.tabs(["📋 Statistiques", "🗂️ Données brutes"])
    with tab_stats:
        stats = (df.groupby(["parametre", "unite"])["resultat"]
                   .agg(["count", "mean", "min", "max"])
                   .round(2).reset_index())
        stats.columns = ["Paramètre", "Unité", "Nombre", "Moyenne", "Min", "Max"]
        st.dataframe(stats, use_container_width=True, hide_index=True)
        csv_stats = stats.to_csv(index=False).encode("utf-8-sig")
        st.download_button("⬇️ Télécharger les statistiques (CSV)", csv_stats,
                           file_name=f"stats_{code_station}.csv", mime="text/csv")
    with tab_data:
        st.dataframe(df.sort_values("date", ascending=False), use_container_width=True, hide_index=True)
        csv_data = df.to_csv(index=False).encode("utf-8-sig")
        st.download_button("⬇️ Télécharger les données (CSV)", csv_data,
                           file_name=f"donnees_{code_station}.csv", mime="text/csv")
