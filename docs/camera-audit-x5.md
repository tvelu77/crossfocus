# Audit Camera2 — Crosscall Stellar X5

Source : export `CameraAuditActivity` (issue #3, PR #8), généré le 2026-11-24 19:05:45.
Appareil : Crosscall Stellar-X5, Android 15 (API 35).

## Rapport brut

```
CrossFocus - Camera2 audit
generated: 2026-11-24 19:05:45
device: Crosscall Stellar-X5 (Android 15, API 35)

=== camera 0 ===
facing: back
hardware level: LEVEL_3
capabilities: BACKWARD_COMPATIBLE, CONSTRAINED_HIGH_SPEED_VIDEO, RAW, YUV_REPROCESSING, PRIVATE_REPROCESSING, READ_SENSOR_SETTINGS, MANUAL_SENSOR, BURST_CAPTURE, MANUAL_POST_PROCESSING
max digital zoom: 8.0
active array: Rect(0, 0 - 4096, 3072)
pixel array: 4096x3072
physical size: 8.192x6.144
focal lengths (mm): 5.89
min focus distance (diopters): 10.0
apertures: 1.89
ISO range: [50, 3200]
max analog ISO: 3200
exposure time range (ns): [96600, 342589440]
AE compensation range: [-12, 12]
target FPS ranges: [3, 8], [8, 8], [10, 10], [15, 15], [3, 24], [24, 24], [3, 30], [7, 30], [30, 30]
max regions AE/AF/AWB: 1/1/0
flash available: true
max faces: 10
JPEG sizes (17): 4096x3072, 4096x2304, 3072x3072, 3840x2160, 1920x1080, 1440x1080, 1280x960, 1080x1080
YUV_420_888 sizes (17): max=4096x3072

=== camera 1 ===
facing: front
hardware level: LEVEL_3
capabilities: BACKWARD_COMPATIBLE, CONSTRAINED_HIGH_SPEED_VIDEO, RAW, YUV_REPROCESSING, PRIVATE_REPROCESSING, READ_SENSOR_SETTINGS, MANUAL_SENSOR, BURST_CAPTURE, MANUAL_POST_PROCESSING
max digital zoom: 8.0
active array: Rect(0, 0 - 2328, 1748)
pixel array: 2328x1748
physical size: 4.656x3.496
focal lengths (mm): 3.26
min focus distance (diopters): 0.0
apertures: 2.45
ISO range: [100, 25600]
max analog ISO: 1600
exposure time range (ns): [72732, 319151820]
AE compensation range: [-12, 12]
target FPS ranges: [15, 15], [24, 24], [14, 30], [30, 30]
max regions AE/AF/AWB: 1/0/0
flash available: false
max faces: 10
JPEG sizes (16): 2328x1748, 1748x1748, 2328x1308, 1920x1080, 1440x1080, 1280x960, 1080x1080, 1280x720
YUV_420_888 sizes (16): max=2328x1748
```

## Analyse

### Ce qui est excellent (LEVEL_3)
- Les deux caméras sont en `INFO_SUPPORTED_HARDWARE_LEVEL_3` : **contrôle Camera2 complet**.
- `MANUAL_SENSOR` : ISO / temps d'exposition / focus manuels.
- `BURST_CAPTURE` : **rafale native**.
- `RAW` : capture DNG possible.
- `READ_SENSOR_SETTINGS` + `MANUAL_POST_PROCESSING` : métadonnées complètes.
- ZSL (`CONTROL_ENABLE_ZSL`) non listé comme capability mais quasi certain sur LEVEL_3 + BURST_CAPTURE → à valider à l'exécution (cible : latence au clic < 200 ms).

### Correspondance capteurs physiques ↔ IDs Camera2

Le X5 embarque physiquement **3 capteurs** (fiche Crosscall / revendeurs) :
- arrière : **Sony IMX766 50 MP** (f/1,8, OIS) + **ultra grand-angle Samsung 13 MP** (120°)
- avant : **16 MP** avec binning Fusion4 (4 pixels → 1, soit des images finales de 4 MP — confirmé par la doc officielle Crosscall)

Mais `CameraManager.cameraIdList` n'expose que **2 IDs** :
- `camera 0` (back) = l'**IMX766 50 MP exposé en binning 4-en-1** : 8192x6144 / 2 = **4096x3072 (12,6 MP)** max en JPEG. La focale 5,89 mm (≈ 26 mm équiv.) confirme que c'est le capteur principal, pas l'ultra grand-angle.
- `camera 1` (front) = le **16 MP frontal en binning Fusion4** : 2328x1748 = **4 MP**.
- L'**ultra grand-angle 13 MP n'est pas exposé** aux apps tierces via Camera2 standard (device vraisemblablement caché au niveau HAL, accessible à l'app Crosscall via extensions vendor — d'où `PRIVATE_REPROCESSING` dans les capabilities).

Pour lister tous les devices caméra au niveau HAL, y compris cachés :

```sh
adb shell dumpsys media.camera
```

Le 50 MP pleine résolution n'est pas atteignable en JPEG standard ; il faudrait passer par les extensions vendor (remosaic via `PRIVATE_REPROCESSING`).

### Caméra arrière (camera 0)
- Capteur effectif **4096x3072 = 12,6 MP** (4:3), pixel 2,0 µm, dalle ~8,19x6,14 mm (~1/2,5"), f/1,89, focale 5,89 mm, focus mini ~10 cm.
- Note : le « 50 MP » marketing = IMX766 en pleine résolution, mais le HAL n'expose que le binning 4-en-1 (**aucun flux > 4096x3072** en JPEG) → on travaille en 12,6 MP max (voir « Correspondance capteurs physiques »). C'est une bonne nouvelle pour la perf (fusion HDR rapide sur Adreno 642L).
- ISO 50–3200 (analog max 3200), exposition 96 µs – 343 ms, compensation AE ±12, flash présent, détection de 10 visages.
- Régions AE/AF = 1/1 (tap-to-focus OK), AWB = 0 (auto uniquement).
- Preview max **30 fps** (pas de plage 60 fps). `CONSTRAINED_HIGH_SPEED_VIDEO` présent → vidéo haute cadence à résolution réduite.
- Zoom : numérique uniquement, max 8,0x. Pas de caméra logique multi-capteurs → pas de switch optique.

### Caméra avant (camera 1)
- 2328x1748 (4 MP), f/2,45, focus fixe, pas de flash, ISO jusqu'à 25600 (digital).

### Ce qui manque
- **Pas de `DEPTH_OUTPUT`** → pas de bokeh portrait basé profondeur (hors scope v1).
- Pas de HDR natif listé dans les capabilities → **HDR = bracketing multi-frame** (3–5 vues) + fusion GPU.
- Pas de caméra logique multi-caméra.

## Conséquences pour le pipeline de perf (issue #5)

| Feature | Décision |
|---|---|
| ZSL / latence au clic | oui, viser < 200 ms (`CONTROL_ENABLE_ZSL` + preview actif) |
| Rafale | oui, `BURST_CAPTURE`, cadence max mesurée sur appareil |
| RAW (DNG) | oui, option Pro |
| Contrôles manuels | oui, ISO 50–3200, expo 96 µs–343 ms, focus tap |
| HDR | bracketing 3–5 frames en 12,6 MP, fusion GPU Adreno 642L, cible < 2 s (idéal < 1 s) |
| Preview | 30 fps, `STRATEGY_KEEP_ONLY_LATEST` pour l'analyse |
| Portrait bokeh | non (pas de depth) — hors scope v1 |
| Vidéo haute cadence | `CONSTRAINED_HIGH_SPEED_VIDEO` à explorer plus tard |

Baseline à battre (mesurée par le propriétaire) : l'app native Crosscall met jusqu'à ~10 s sur certaines photos (HDR / basse lumière).
