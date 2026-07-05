# Skribo (Inktest)

**Native Handschrift-/Notiz-App für Android — Tablet-first, Stylus-optimiert.**

Skribo ist eine schnelle, latenzarme Ink-App für Android-Tablets: schreiben mit
dem Stift, organisiert in Abschnitten und Seiten, mit optionaler WebDAV-Synchronisation.
Der Projektname **Inktest** stammt vom eingebauten Tuning-/Metrics-Testbed, mit dem
Rendering-Performance und Stift-Latenz systematisch vermessen werden.

## Features

- **Ink-Engine** — druck-/geschwindigkeitsabhängige Striche, Motion-Prediction
  (`MotionEventPredictor`) für geringe Latenz
- **Glättungs-Algorithmen** wählbar: Bézier, Catmull-Rom, WMA
- **Werkzeuge:** Stift, Textmarker, Linie, Text, Bild, Radierer
- **Papierstile:** Blank, Lineiert, Kariert, Punkte, Legal (gelb)
- **Struktur:** Abschnitte → Seiten → Unterseiten
- **WebDAV-Sync** (`SkriboSync`) auf das Skribo-On-Disk-Schema
- **Tuning-/Metrics-Panel** — Layer-Typ, Bitmap-Config, Antialiasing, Clipping,
  Damage-Rect-Invalidation u.v.m. live umschaltbar zum Latenz-Benchmarking

## Tech-Stack

| Komponente     | Technologie |
|----------------|-------------|
| Sprache / UI   | Kotlin, Android Views (kein Compose), landscape / tablet-first |
| Ink-Prediction | `androidx.input:input-motionprediction` |
| Sync           | WebDAV über OkHttp |
| Min / Target   | `minSdk 24`, `targetSdk 34`, `compileSdk 34` |

## Build

```bash
./gradlew :app:assembleDebug     # APK bauen
./gradlew :app:installDebug      # auf angeschlossenem Gerät/Emulator installieren
```

> Voraussetzung: Android SDK. Lokale Pfade (`sdk.dir`, JDK) stehen in
> `local.properties` — diese Datei ist bewusst **nicht** eingecheckt.

## Mockups

Design-Explorationen (GoodNotes-/OneNote-Stil, Skribo-Branding, Datenformat)
liegen als HTML unter [`mockups/`](./mockups/).

## Status

🚧 In Entwicklung.

## Lizenz

[GNU General Public License v3.0](./LICENSE) (GPLv3).
