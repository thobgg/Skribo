# Skribo

**Handschrift-System für Unterrichtsplanung — OneNote-artig, mit bidirektionaler
WebDAV-Sync zwischen Desktop-PC und CTOUCH-Boards.**

Skribo unterstützt den Workflow von Lehrkräften: **Unterrichtsplanung am Desktop-PC**
(ähnlich OneNote) und **Präsentieren/Annotieren am CTOUCH-Board** im Klassenraum —
beide Seiten bleiben über **WebDAV bidirektional synchron**. Was am PC vorbereitet
wird, liegt live auf dem Board; was im Unterricht am Board handschriftlich ergänzt
wird, fließt zurück.

## Komponenten (Monorepo)

Skribo besteht aus zwei Clients, die dasselbe offene On-Disk-Schema über WebDAV teilen:

| Client | Verzeichnis | Zweck | Status |
|--------|-------------|-------|--------|
| **Board-Client** (Android) | [`app/`](./app/) | Ink-Oberfläche am CTOUCH-Board / Tablet | Prototyp — Latenz-PoC ✅ |
| **Desktop-Client** (Planung) | _folgt_ | OneNote-artige Unterrichtsplanung am PC | geplant |

> **Stand heute:** Der Android-Client ist ein **rudimentärer Prototyp**, entstanden
> als Latenz-Test auf einem echten CTOUCH-Board. Der Test war **erfolgreich** (die
> Stift-Latenz taugt für den Unterrichtseinsatz) — das ist der Startschuss, aus dem
> Testbed „Inktest" das echte Produkt **Skribo** zu bauen.

> **Arbeitstitel „Inktest":** Der Repo-/Ordnername stammt vom eingebauten Tuning-/Metrics-Testbed,
> mit dem Rendering-Performance und Stift-Latenz auf den Boards vermessen werden.
> Produktname ist **Skribo**.

## Warum

Für die Kombination aus **Unterrichtsplanung am PC** und **handschriftlichem Arbeiten
am CTOUCH-Board** gibt es keine nahtlose Lösung ohne Cloud-Zwang: OneNote bindet an
Microsoft-Konten/Cloud, und die bidirektionale Synchronisation Desktop ↔ Board läuft
nicht offen über selbst gehostetes WebDAV. Skribo setzt genau hier an — eigenes,
offenes On-Disk-Schema, Sync über den eigenen WebDAV-Server, Board als
gleichberechtigter Bearbeitungsort (nicht nur Anzeige).

## Features (Board-Client)

- **Ink-Engine** — druck-/geschwindigkeitsabhängige Striche, Motion-Prediction
  (`MotionEventPredictor`) für geringe Latenz
- **Glättungs-Algorithmen** wählbar: Bézier, Catmull-Rom, WMA
- **Werkzeuge:** Stift, Textmarker, Linie, Text, Bild, Radierer
- **Papierstile:** Blank, Lineiert, Kariert, Punkte, Legal (gelb)
- **Struktur:** Abschnitte → Seiten → Unterseiten
- **Bidirektionale WebDAV-Sync** (`SkriboSync`) Desktop ↔ Board auf ein offenes
  Skribo-On-Disk-Schema (kein Cloud-Zwang, selbst hostbar)
- **Tuning-/Metrics-Panel** — Layer-Typ, Bitmap-Config, Antialiasing, Clipping,
  Damage-Rect-Invalidation u.v.m. live umschaltbar zum Latenz-Benchmarking

## Tech-Stack

| Komponente     | Technologie |
|----------------|-------------|
| Sprache / UI   | Kotlin, Android Views (kein Compose), landscape / tablet-first |
| Ink-Prediction | `androidx.input:input-motionprediction` |
| Sync           | WebDAV über OkHttp |
| Min / Target   | `minSdk 24`, `targetSdk 34`, `compileSdk 34` |

## Build (Board-Client)

```bash
./gradlew :app:assembleDebug     # APK bauen
./gradlew :app:installDebug      # auf angeschlossenem Board/Gerät/Emulator installieren
```

> Voraussetzung: Android SDK. Lokale Pfade (`sdk.dir`, JDK) stehen in
> `local.properties` — diese Datei ist bewusst **nicht** eingecheckt.

## Distribution

Interner Einsatz an der eigenen Schule — Verteilung der APK direkt auf die
CTOUCH-Boards (kein App-Store).

## Mockups

Design-Explorationen (GoodNotes-/OneNote-Stil, Skribo-Branding, Datenformat)
liegen als HTML unter [`mockups/`](./mockups/).

## Status

🚧 In Entwicklung. Roadmap, Milestones und voller Projektkontext siehe
[`PROJECT.md`](./PROJECT.md).

## Lizenz

[GNU General Public License v3.0](./LICENSE) (GPLv3).
