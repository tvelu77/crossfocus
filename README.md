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
- `docs/` — rapports d'audit caméra (#3) et de performance (#6), à créer au fil du projet
