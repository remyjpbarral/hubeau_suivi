# Suivi des cours d'eau 🐟

Appli web de suivi environnemental basée sur les données officielles françaises
([Hub'Eau](https://hubeau.eaufrance.fr)) :

- **Débits** — API Hydrométrie (HYDRO / Vigicrues)
- **Poissons** — API Poisson / État piscicole (base ASPE, OFB)
- **Qualité des eaux** — API Qualité des cours d'eau (Naïades : nitrates, phosphore, métaux…)

Accessible depuis n'importe quel appareil (PC, tablette, smartphone) via un
navigateur — aucune installation nécessaire.

## Déploiement en ligne (Streamlit Cloud)

1. Créer un compte sur [share.streamlit.io](https://share.streamlit.io)
   avec votre compte GitHub
2. « New app » → dépôt `hubeau_suivi`, branche `main`,
   fichier `dashboard/app.py`
3. L'appli est publiée sur `https://<votre-app>.streamlit.app`

## Utilisation locale (optionnelle)

```bash
pip install -r requirements.txt
streamlit run dashboard/app.py
```

Collecte programmée vers SQLite (pour historisation locale) :

```bash
python -m suivi_eau.collect --commune 42218 --all
python -m suivi_eau.collect --cours-eau "la loire" --poissons --qualite
```

## Structure

```
├── dashboard/app.py         # appli web Streamlit
├── suivi_eau/
│   ├── collect.py          # CLI de collecte
│   ├── collecteurs/        # débits, poissons, qualité (+ base extensible)
│   ├── db.py               # stockage SQLite
│   └── config.py
└── tests/
```

## Ajouter une source de données

Créer `suivi_eau/collecteurs/<nom>.py` héritant de `CollecteurBase`
(`endpoints()` + `transforme()`), puis l'enregistrer dans
`suivi_eau/collecteurs/__init__.py`.
