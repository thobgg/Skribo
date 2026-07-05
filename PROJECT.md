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
- **Sync** (`app/.../SkriboSync.kt`): WebDAV via OkHttp auf das Skribo-On-Disk-Schema
  `<section.webdavPath>/<page.title>/skribo/base.json` +
  `.../annotations/<year>.json`.
- **Ink-Engine** (`InkView.kt`, `Stroke.kt`): Glättung (Bézier / Catmull-Rom / WMA),
  Motion-Prediction, umfangreiches Tuning-/Metrics-Panel für Latenz-Benchmarks.

## 3. Milestones

> **Entwurf** — aus dem aktuellen Code-Stand abgeleitet. Bitte anpassen, was
> bereits erledigt bzw. anders priorisiert ist.

- [ ] **M0 — Board-Client-Prototyp:** Ink-Engine, Werkzeuge, Papierstile,
      Tuning-/Metrics-Testbed *(weitgehend vorhanden)*
- [ ] **M1 — Dokumentmodell & Navigation:** Abschnitte/Seiten/Unterseiten,
      Umbenennen/Löschen, Persistenz *(vorhanden)*
- [ ] **M2 — WebDAV-Sync Board → Server:** Push des lokalen Dokuments ins
      Skribo-Schema *(`SkriboSync` vorhanden — Umfang prüfen)*
- [ ] **M3 — Bidirektionale Sync:** Server → Board (Pull), Merge-/Konfliktstrategie,
      Annotationen getrennt von Basis
- [ ] **M4 — Desktop-Planungs-Client:** OneNote-artige Oberfläche am PC, gleiches
      Schema, gleicher WebDAV-Server
- [ ] **M5 — Board-Rollout:** Stabilität/Politur, APK-Verteilung auf die
      schuleigenen CTOUCH-Boards, Betrieb

## 4. Rahmenbedingungen

- **Zielgeräte:** CTOUCH-Boards (Android, groß, landscape, Stift/Finger) — primär;
  Android-Tablets sekundär.
- **Distribution:** intern, APK direkt auf die Boards (kein Store).
- **Lizenz:** GPLv3 (offener Quellcode).
- **Datenhaltung:** eigener WebDAV-Server, offenes JSON-Schema, kein Cloud-Zwang.

## 5. Offene Punkte / To decide

- Desktop-Client: Plattform/Tech-Stack (Web? Kotlin Multiplatform? Electron?),
  Verzeichnisstruktur im Monorepo (`android/` + `desktop/`?).
- Sync: Konfliktbehandlung bei parallelen Änderungen an PC und Board.
- Repo-Umbenennung Ordner `inktest` → `skribo`? (Repo heißt bereits `Skribo`.)
