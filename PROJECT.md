# Projekt: Skribo — Handschrift-System für Unterrichtsplanung (PC ↔ CTOUCH-Board)

> **Dieses Dokument ist der Projektkontext.** Motivation, Architektur, Milestones
> und Entscheidungen an einer Stelle — Grundlage für die Weiterentwicklung mit
> Claude Code. (Arbeitstitel des Repos: „Inktest"; Produktname: **Skribo**.)

---

## 1. Motivation & Zielbild

Lehrkräfte planen Unterricht am **Desktop-PC** und arbeiten im Klassenraum am
**CTOUCH-Board** (großes Android-Touchdisplay). Für die Kombination aus
strukturierter Planung *und* handschriftlichem Arbeiten am Board gibt es keine
nahtlose, cloud-freie Lösung:

- **OneNote** bindet an Microsoft-Konten/Cloud; bidirektionale Sync Desktop ↔ Board
  läuft nicht offen über selbst gehostetes WebDAV.
- Reine Whiteboard-Apps am Board haben keine strukturierte Planungsseite am PC.

**Gewählter Ansatz:** Zwei selbst gebaute Clients (Board + Desktop), die ein
**offenes On-Disk-Schema** über einen **eigenen WebDAV-Server** teilen. Das Board
ist gleichberechtigter Bearbeitungsort, nicht nur Anzeige. Kein Cloud-Zwang,
selbst hostbar, Datenhoheit bei der Schule.

## 2. Architektur

```
┌─────────────────────┐        WebDAV         ┌──────────────────────┐
│  Desktop-Client      │  ◀───────────────▶   │  Board-Client         │
│  (Planung, PC)       │   eigener Server      │  (Android, CTOUCH)    │
│  — geplant —         │   offenes Schema      │  app/  — vorhanden    │
└─────────────────────┘                        └──────────────────────┘
```

- **Monorepo:** beide Clients in diesem Repository.
  - `app/` — Android-Board-Client (Kotlin, Android Views, landscape/tablet-first).
  - Desktop-Client — folgt (Verzeichnis noch offen).
- **Datenmodell** (`app/.../Model.kt`): Abschnitte → Seiten → Unterseiten;
  Werkzeuge Pen/Highlighter/Line/Text/Image/Eraser; Papierstile
  Blank/Lined/Grid/Dots/Legal.
- **Sync** (`app/.../SkriboSync.kt`): WebDAV via OkHttp, HTTP Basic Auth. Push-only
  (Pull/Bidirektional = M4). Details siehe **§2a**.
- **Ink-Engine** (`InkView.kt`, `Stroke.kt`): Glättung (Bézier / Catmull-Rom / WMA),
  Motion-Prediction, umfangreiches Tuning-/Metrics-Panel für Latenz-Benchmarks.

## 2a. Infrastruktur & WebDAV-Pfad-Konvention

**Server:** Synology DiskStation (DSM), WebDAV-Server-Paket, HTTPS + HTTP Basic Auth.
Server-URL, Benutzer/Passwort und aktives Schuljahr sind Geräte-Einstellungen
(`Prefs` — nicht im Repo). Verbindungstest per `PROPFIND`, Verzeichnisse per `MKCOL`,
Dateien per `PUT`.

**Pfad-Schema** (jede Seite/Unterseite wird zu einem Verzeichnis — Titel müssen
dateisystem-tauglich sein):

```
<webdavServer>/<section.webdavPath>/<page>/skribo/base.json
<webdavServer>/<section.webdavPath>/<page>/skribo/annotations/<schuljahr>.json
<webdavServer>/<section.webdavPath>/<page>/skribo/<unterseite>/base.json
<webdavServer>/<section.webdavPath>/<page>/skribo/<unterseite>/annotations/<schuljahr>.json
```

- `section.webdavPath` — pro Abschnitt konfiguriert; leer ⇒ Abschnitt bleibt lokal.
- **`base.json`** (`type: skribo-base`, `schemaVersion: 1`): Titel, Papierstil,
  Texte, Bilder — die *stabile* Seitenbasis.
- **`annotations/<schuljahr>.json`** (`type: skribo-annotations`): die *jahresbezogenen*
  Striche/Annotationen. So kann dieselbe Basis über mehrere Schuljahre neu
  annotiert werden, ohne die Vorlage zu überschreiben.

> Konvention wichtig für den Desktop-Client (M5): Er muss **dasselbe** Schema
> lesen/schreiben. Ändert sich das Layout, `schemaVersion` erhöhen.

## 3. Stand heute

Der Android-Client (`app/`) entstand als **Latenz-Test** auf einem echten
CTOUCH-Board: Ziel war zu klären, ob Android-Ink auf dieser Hardware latenzarm
genug für flüssiges Schreiben im Unterricht ist. **Der Test war erfolgreich** —
damit ist die technische Kernannahme bewiesen und das Testbed „Inktest" wird zum
Produkt **Skribo** ausgebaut. Was über die reine Ink-Fläche hinaus im Code liegt
(Modell, Werkzeuge, `SkriboSync`), ist Prototyp-Scaffolding, noch nicht
produktionsreif.

## 4. Milestones

> **Entwurf** — bitte Reihenfolge/Umfang anpassen.

- [x] **M0 — Latenz-Machbarkeit (PoC):** rudimentärer Ink-Prototyp live am
      CTOUCH-Board getestet, Stift-Latenz tauglich → **bestanden, Greenlight**
- [ ] **M1 — Board-Client produktionsreif:** Ink-Engine, Werkzeuge (Pen/Marker/
      Linie/Text/Bild/Radierer), Papierstile stabil und bedienbar
- [ ] **M2 — Dokumentmodell & Navigation:** Abschnitte/Seiten/Unterseiten,
      Umbenennen/Löschen, robuste lokale Persistenz
- [ ] **M3 — WebDAV-Sync Board → Server:** Push ins offene Skribo-Schema
      (`SkriboSync` ausbauen/härten)
- [ ] **M4 — Bidirektionale Sync:** Server → Board (Pull), Merge-/Konfliktstrategie,
      Annotationen getrennt von der Basis
- [ ] **M5 — Desktop-Planungs-Client:** OneNote-artige Oberfläche am PC, gleiches
      Schema, gleicher WebDAV-Server
- [ ] **M6 — Board-Rollout:** Stabilität/Politur, APK-Verteilung auf die
      schuleigenen CTOUCH-Boards, Betrieb

## 5. Rahmenbedingungen

- **Zielgeräte:** CTOUCH-Boards (Android, groß, landscape, Stift/Finger) — primär;
  Android-Tablets sekundär.
- **Distribution:** intern, APK direkt auf die Boards (kein Store).
- **Lizenz:** GPLv3 (offener Quellcode).
- **Datenhaltung:** eigener WebDAV-Server, offenes JSON-Schema, kein Cloud-Zwang.
  **WebDAV läuft bereits auf einer Synology DiskStation** (DSM, WebDAV-Server-Paket)
  — eingerichtet und einsatzbereit. Pfad-/Datenlayout siehe §2a.

## 6. Getroffene Entscheidungen

- **Sync-Ziel: eigenes NAS statt OneDrive.** Abgewogen wurde OneDrive vs.
  selbst gehostetes NAS-WebDAV; Wahl fiel auf das **NAS** — Datenhoheit,
  kein Microsoft-Konto-/Cloud-Zwang, DSGVO-freundlich im Schulkontext. Der
  WebDAV-Server ist bereits eingerichtet.
- **Zwei-Client-System in einem Monorepo** (Board + Desktop).
- **Lizenz GPLv3**, interne APK-Distribution (kein Store).

## 7. Offene Punkte / To decide

- Desktop-Client: Plattform/Tech-Stack (Web? Kotlin Multiplatform? Electron?),
  Verzeichnisstruktur im Monorepo (`android/` + `desktop/`?).
- Sync: Konfliktbehandlung bei parallelen Änderungen an PC und Board.
- Repo-Umbenennung Ordner `inktest` → `skribo`? (Repo heißt bereits `Skribo`.)
