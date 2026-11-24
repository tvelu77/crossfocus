# CrossFocus

[![CI](https://github.com/tvelu77/crossfocus/actions/workflows/ci.yml/badge.svg)](https://github.com/tvelu77/crossfocus/actions/workflows/ci.yml)

Application caméra Android hautes performances pour le **Crosscall Stellar X5** : latence de déclenchement réduite (ZSL), aperçu fluide, mode rafale — objectif : être plus performante que l'app photo d'origine.

La roadmap est pilotée par les [issues](https://github.com/tvelu77/crossfocus/issues) (vision : #1, règles agents : #2).

## Prérequis

- JDK 17
- Android SDK avec la plateforme `android-34` et les build-tools
- Gradle 8.7 (les propriétés du wrapper sont fournies ; si `gradle/wrapper/gradle-wrapper.jar` est absent, exécuter une fois `gradle wrapper --gradle-version 8.7` ou ouvrir le projet dans Android Studio)

## Build

```sh
./gradlew assembleDebug
```

## Installation sur le Stellar X5 (USB debugging activé)

```sh
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Structure

- `app/` — module unique : CameraX (`Preview` + `ImageCapture`), ViewBinding, caméra arrière
- `CameraAuditActivity` — outil d'audit Camera2 embarqué : dump des `CameraCharacteristics` de toutes les caméras, exportable en `.txt` (issue #3). Sur le X5 : `adb pull <fichier>` depuis `Android/data/com.tvelu77.crossfocus/files/`

## Interface (issues #19, #21)

- Thème sombre propre (fond/statusBar/navigationBar noirs)
- Galerie : bande horizontale des 20 dernières photos, tap → plein écran (`PhotoViewActivity`)
- Slider zoom 1.0–8.0x (`setZoomRatio`) avec affichage « 1.0x »
- Mode **Pro** : ISO 50–3200 + obturation 1/10357s–1/3s via `Camera2Interop` (`AE_MODE_OFF`, `SENSOR_SENSITIVITY`, `SENSOR_EXPOSURE_TIME`), EV ±2 via `setExposureCompensationIndex`
- Preview plein écran + tap-to-focus
- Barre du bas : sélecteur de mode (Simple / Rafale / HDR) + **obturateur rond** custom
- Barre du haut : toggle **flash** (torch) + bouton Pro + bouton audit caméra


- Preview plein écran + tap-to-focus
- Barre du bas : sélecteur de mode (Simple / Rafale / HDR) + **obturateur rond** custom
- Barre du haut : toggle **flash** (torch) + bouton audit caméra

## Fonctions (issue #5)

- Capture simple en `CAPTURE_MODE_MINIMIZE_LATENCY`, JPEG qualité 90, 12,6 MP max
- **Rafale** ×5 en boucle sur executor dédié (bouton « Rafale »)
- **HDR** : bracketing 3 vues à −2/0/+2 EV (`exposureCompensationIndex` ±12, pas de 1/6 EV) puis fusion par pondération d'exposition sur la luminance (`HdrMerger`)
- Tap-to-focus via `FocusMeteringAction` (1 région AF dispo)
- `ImageAnalysis` 640×480 en `STRATEGY_KEEP_ONLY_LATEST` (stats de luminance loguées, aucun backlog)
- ZSL désactivé sur ce modèle (`camera.disable_zsl_mode=true`) → latence mesurée via l'issue #6

Photos enregistrées dans `Android/data/com.tvelu77.crossfocus/files/Pictures/` (`adb pull`).
- `docs/` — rapports d'audit caméra (#3) et de performance (#6), à créer au fil du projet
