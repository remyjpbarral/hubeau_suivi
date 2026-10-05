# Suivi environnemental — Hub'Eau

Application de suivi environnemental des stations de mesure françaises, interrogeant les bases de données de l'État via le portail [Hub'Eau](https://hubeau.eaufrance.fr) (APIs publiques, sans clé).

Fonctionne à l'identique sur **Windows** et **Linux**.

## Fonctionnalités

- 🔍 Recherche de stations de suivi (par commune, département, cours d'eau ou autour d'un point GPS)
- 📊 Graphique temporel interactif multi-paramètres (nitrates, chlorures, MES, etc.)
- 📋 Statistiques descriptives par paramètre (nombre, moyenne, min, max)
- 🗂️ Tableau de données brutes avec export CSV (compatible Excel)
- ⚙️ CLI pour l'automatisation (cron / Planificateur de tâches Windows)

## Installation (Windows et Linux)

Prérequis : [Python 3.10+](https://www.python.org/downloads/) (sous Windows, cocher « Add Python to PATH » à l'installation).

```bash
# Dans le dossier du projet
pip install -r requirements.txt
```

## Utilisation — application web locale

```bash
streamlit run app.py
```

L'interface s'ouvre automatiquement dans le navigateur à l'adresse `http://localhost:8501`.

1. Panneau de gauche : chercher une station (ex. commune « Longuyon »)
2. Choisir la station, la période et les paramètres
3. Cliquer « 🚀 Générer l'analyse » → graphique, statistiques et exports CSV

## Utilisation — ligne de commande (automatisation)

```bash
# Chercher des stations
python hubeau_cli.py stations --commune Longuyon

# Rapport CSV + PNG pour une station
python hubeau_cli.py rapport --station 01057000 --parametres "Nitrates,Chlorures" --depuis 2020-01-01
```

Automatisation :
- **Windows** : Planificateur de tâches → `python hubeau_cli.py rapport --station ...`
- **Linux** : cron → `0 6 * * 1 cd /chemin/hubeau-suivi && python3 hubeau_cli.py rapport ...`

## Structure du projet

- `app.py` — application web Streamlit (recherche stations, graphique Plotly, exports)
- `hubeau_api.py` — client Hub'Eau avec pagination, reprise sur erreur et cache
- `hubeau_cli.py` — outil en ligne de commande (stations, analyses, rapport CSV+PNG)
- `requirements.txt` — dépendances

## Données

- Source : base Naïades (qualité physico-chimique des cours d'eau), Agences de l'Eau
- API : `GET https://hubeau.eaufrance.fr/api/v2/qualite_rivieres/{station_pc,analyse_pc}`
- Documentation : <https://hubeau.eaufrance.fr/page/api-qualite-cours-deau>
- Licence : données publiques, réutilisation libre (licence Etalab)
