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

> **Priorisierung (2026-08-19):** Für den echten Einsatz ist der **Desktop-Client
> der Engpass**, nicht die Board-Politur — er wurde deshalb vorgezogen (jetzt M3/M4).
> Vorgelagert ist der Toolchain-/Monorepo-Umbau (M2), weil Compose Multiplatform
> Kotlin 2.x + Gradle 9 voraussetzt und das `shared/`-Modul die Basis beider
> Clients wird.

- [x] **M0 — Latenz-Machbarkeit (PoC):** rudimentärer Ink-Prototyp live am
      CTOUCH-Board getestet, Stift-Latenz tauglich → **bestanden, Greenlight**
- [ ] **M1 — Board-Client Kernfunktionen:** Ink-Engine, Werkzeuge (Pen/Marker/
      Linie/Text/Bild/Radierer), Papierstile stabil und bedienbar; Dokumentmodell
      & Navigation (Abschnitte/Seiten/Unterseiten, Umbenennen/Löschen, robuste
      lokale Persistenz)
- [ ] **M2 — Toolchain & Shared-Modul:** Gradle 9.x / Kotlin 2.x / AGP 9.x;
      Monorepo-Umbau zu `shared/` (Model, JSON-Schema, `SkriboSync`) +
      `android/` + `desktop/` — Schema-Code existiert danach nur noch **einmal**
- [ ] **M3 — Desktop-Client MVP (Compose Multiplatform):** OneNote-artige
      Planungsoberfläche; liest/schreibt dasselbe Schema direkt via WebDAV;
      Pakete für Linux (.deb), Windows 11 (.msi), macOS (.dmg) via `jpackage`
- [ ] **M4 — WebDAV-Sync Board ↔ Server:** Push aus `shared/` härten, dann
      Pull + Merge-/Konfliktstrategie; Annotationen getrennt von der Basis
- [ ] **M5 — Board-Rollout:** Stabilität/Politur, APK-Verteilung auf die
      schuleigenen CTOUCH-Boards, Betrieb

## 5. Rahmenbedingungen

- **Zielgeräte Board:** CTOUCH-Boards (Android, groß, landscape, Stift/Finger) —
  primär; Android-Tablets sekundär.
- **Zielplattformen Desktop:** Windows 11 und macOS (Kollegium) sowie Linux
  (eigener Arbeitsplatz, Xubuntu) — alle drei müssen bedient werden.
- **Distribution:** intern, APK direkt auf die Boards (kein Store);
  Desktop-Pakete pro Plattform (.deb/.msi/.dmg).
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
- **Desktop-Stack: Compose Multiplatform (Kotlin).** (2026-08-19) Abgewogen
  gegen Qt/QML, Tauri und Electron. Ausschlaggebend: maximaler Code-Reuse
  (Model, JSON-Schema, `SkriboSync` wandern in ein `shared/`-Modul, das Board
  und Desktop teilen — verhindert Schema-Drift), eine Sprache für einen
  Entwickler, und alle drei Desktop-Plattformen (Win 11 / macOS / Linux) aus
  einer Codebasis. Bewusst akzeptiert: JVM-App statt „echtem" Nativ-Look;
  Stift-Druck am Desktop ist zweitrangig, da Ink primär am Board stattfindet.
- **Lizenz GPLv3**, interne APK-Distribution (kein Store).

## 7. Offene Punkte / To decide

- Monorepo-Zielstruktur beim Umbau (M2): `shared/` + `android/` + `desktop/` —
  Details (Modulnamen, was genau nach `shared/` zieht) beim Umbau festlegen.
- Sync: Konfliktbehandlung bei parallelen Änderungen an PC und Board.
- Repo-Umbenennung Ordner `inktest` → `skribo`? (Repo heißt bereits `Skribo`.)
