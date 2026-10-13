# 📱 Débits Rivières — Application Android

Application Android (Java, natif, zéro dépendance réseau externe) de visualisation
des **débits des cours d'eau français** sur des pas de temps choisis.

Données : API [Hub'Eau / Hydrométrie](https://hubeau.eaufrance.fr/page/api-hydrometrie)
(SANDRE — Ministère en charge de l'écologie, Eau France).
Conditions d'utilisation : https://hubeau.eaufrance.fr/page/conditions-dutilisation

## Fonctionnalités

- 🔍 **Recherche de stations hydrométriques** (nom de station, cours d'eau, code)
- ⏱️ **Pas de temps au choix** : 6 h, 24 h, 7 jours, 30 jours, 12 mois
  (temps réel via `observations_tr` pour les pas courts, données journalisées `observations` pour les pas longs)
- 📈 **Graphique natif** du débit (m³/s) : courbe + aire, grille, axes datés (heure locale)
- 🔄 Bouton **Actualiser**
- ⚖️ Mention légale de la source des données affichée à l'écran

## Compiler

Prérequis : JDK 17, Android SDK (platform 34, build-tools 34.0.0).

```bash
# local.properties :
# sdk.dir=/chemin/vers/android-sdk
./gradlew assembleDebug       # APK debug
./gradlew assembleRelease     # APK release signé
```

APK générés dans `app/build/outputs/apk/`.

Pour la signature release, créez un keystore puis renseignez
`storePassword` / `keyPassword` dans `app/build.gradle`
(ou via les variables d'environnement `KSTORE_PWD` / `KEY_PWD`).

## Structure

```
android/
├── app/
│   ├── src/main/java/fr/mondossier/debitsrivieres/
│   │   ├── MainActivity.java    # UI, recherche stations, sélection période
│   │   ├── HubEauClient.java    # Client API Hub'Eau (stations + débits Q)
│   │   └── ChartView.java      # Graphique personnalisé (Canvas)
│   ├── src/main/res/           # Layouts, chaînes FR, icônes
│   └── build.gradle
├── gradle/wrapper/             # Gradle 8.7
└── settings.gradle
```

## Installation sur Android

1. Télécharger l'APK (voir les [releases](../../releases)) ou le compiler
2. Ouvrir le fichier `.apk` sur le téléphone
3. Autoriser « Installer des applications inconnues » quand Android le demande
4. Lancer **« Débits Rivières »**

MinSdk 24 (Android 7.0+), targetSdk 34.
