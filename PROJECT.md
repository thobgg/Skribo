# Projekt: Skribo — Handschrift-System für Unterrichtsplanung (PC ↔ CTOUCH-Board)

> **Dieses Dokument ist der Projektkontext.** Motivation, Architektur, Milestones
> und Entscheidungen an einer Stelle — Grundlage für die Weiterentwicklung mit
> Claude Code. (Arbeitstitel des Repos: „Inktest"; Produktname: **Skribo**.)

---

## 1. Motivation & Zielbild

**Der eigentliche Grund (festgehalten am 19.08.2026):**

> **OneNote gibt es für Linux nicht.** Der vollwertige Client ist Windows-only,
> im Browser bleibt eine abgespeckte Fassung. Unterricht am eigenen Rechner
> (Xubuntu) zu planen heißt also: entweder Windows benutzen oder ein anderes
> Werkzeug. Dazu der Wunsch nach **so wenig Microsoft wie möglich**. Das ist
> der Zweck des Projekts — nicht Latenz, nicht Datenschutz, nicht die
> Verteilung an Schüler.

Daraus folgt die Rangfolge: **Der Desktop-Client ist das Produkt**, das Board
ergänzt ihn. Ein Skribo, das am Board glänzt, aber unter Linux nicht zum Planen
taugt, verfehlt den Zweck.

Zweiter, unabhängiger Gewinn — von OneNote gar nicht gelöst:

- **Wiederverwendung über Schuljahre.** In OneNote kopiert man Notizbücher oder
  radiert die Tinte des Vorjahres weg; eine Trennung von Vorlage und
  jahresbezogener Handschrift gibt es nicht. Skribo trennt beides (§2a) —
  dieselbe Basis, je Schuljahr eine eigene Annotationsebene.

**Was ausdrücklich NICHT das Ziel ist** (geklärt im Gespräch am 19.08.2026):

- **Kein OneNote-Ersatz für die Schülerseite.** An der Schule laufen
  Teams/MS 365 Education; Schüler bekommen Material über das
  OneNote-Kursnotizbuch aufs Tablet oder öffnen es in GoodNotes. Das
  funktioniert und bleibt. Skribos Brücke dorthin ist der **PDF-Export** —
  das Format, das beide Wege lesen.
- **Keine Latenz-Rettung.** OneNote schreibt am Board gut; der M0-Test belegte
  nur, dass ein eigener Client schnell genug *sein kann*.

**Gewählter Ansatz:** Zwei selbst gebaute Clients (Board + Desktop), die ein
**offenes On-Disk-Schema** über einen **eigenen WebDAV-Server** teilen. Das Board
ist gleichberechtigter Bearbeitungsort, nicht nur Anzeige.

> **Leitbild Desktop-Client:** ehrlich gesagt ein **OneNote-Desktop-Klon — aber
> mit weniger Müll**. OneNote ist ein über Jahre gereiftes Produkt — seine
> bewährten Konzepte und UX-Muster (Abschnitte/Seiten, freie Seitenfläche,
> Medien-Einbindung, Ausdruck vs. Anhang, …) sind ausdrücklich die **Vorlage**
> und werden übernommen, wo sie für die Unterrichtsplanung Sinn ergeben.
> „Weniger Müll" meint das Drumherum: Konto-/Cloud-Zwang, Enterprise-Ballast
> und Funktionen ohne Nutzen für diesen Einsatzzweck bleiben draußen.

## 2. Architektur

```
┌─────────────────────┐        WebDAV         ┌──────────────────────┐
│  Desktop-Client      │  ◀───────────────▶   │  Board-Client         │
│  (Planung, PC)       │   eigener Server      │  (Android, CTOUCH)    │
│  — geplant —         │   offenes Schema      │  app/  — vorhanden    │
└─────────────────────┘                        └──────────────────────┘
```

- **Monorepo:** beide Clients plus ihr gemeinsamer Kern in diesem Repository.
  - `shared/` — plattformfreier Kern (Kotlin/JVM): Datenmodell, On-Disk- und
    WebDAV-Schema, Strich-Mathematik. **Existiert nur einmal** und wird von
    beiden Clients benutzt — das verhindert Schema-Drift.
  - `android/` — Board-Client (Kotlin, Android Views, landscape/tablet-first).
  - `desktop/` — Planungs-Client (Compose Multiplatform) — folgt in M3.
- **Datenmodell** (`shared/.../Model.kt`): Abschnitte → Seiten → Unterseiten;
  Werkzeuge Pen/Highlighter/Line/Text/Image/Eraser; Papierstile
  Blank/Lined/Grid/Dots/Legal.
- **Rollenteilung der Clients:** Der Desktop-Client ist reiner Planungs- und
  Medienarbeitsplatz — **kein Stift/Ink nötig**; zentral ist die **Einbindung
  von Medien** (PDF-Dokumente, Bilder, Videos, Audios, …) in die Seiten.
  Handschrift/Annotation passiert am Board. Das Schema muss dafür über
  Text-/Bildboxen hinaus Medienboxen bekommen (→ §2a, schemaVersion erhöhen).
- **Sync** (`shared/.../SkriboSync.kt`): WebDAV via OkHttp, HTTP Basic Auth. Push-only
  (Pull/Bidirektional = M4). Details siehe **§2a**.
- **Persistenz** (`shared/.../DocumentStore.kt`): atomares Schreiben des lokalen
  Dokuments; das Debouncing macht der jeweilige Client (`android/Repository.kt`).
- **Ink-Engine:** Die Glättung (Bézier / Catmull-Rom / WMA) liegt plattformfrei in
  `shared/Stroke.kt` und schreibt in einen [`PathSink`] — Android füllt damit ein
  `android.graphics.Path` (`AndroidPathSink`), der Desktop später einen Skia-Pfad.
  Rendering, Motion-Prediction und das Tuning-/Metrics-Panel für Latenz-Benchmarks
  bleiben in `android/InkView.kt`.
- **Plattform-Seams:** `PathSink` (Rendering), `SkriboLog` (Logging),
  `SkriboSync.SyncConfig` (Zugangsdaten) — mehr braucht der Kern nicht, um
  Android-frei zu bleiben.

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
  Texte, Bilder — die *stabile* Seitenbasis. **Geplant (schemaVersion 2):**
  Medienboxen für PDF, Video, Audio; Mediendateien liegen wie Bilder als
  Assets neben `base.json` (heute schon `assets/<id>.png`) und syncen mit.
  **PDF** (Zielgröße ≤ ~8 Seiten) am Desktop wahlweise auf zwei Arten — wie in
  OneNote: als **Dateianhang** (Icon auf der Seite, Klick öffnet das PDF) oder
  als **Ausdruck** (Seiten werden zu Bildern gerendert und aufs Papier gelegt).
  Der Ausdruck ist zugleich die Board-Lösung: das Board sieht nur Bilder und
  kann sie mit Ink annotieren — es braucht **keinen eigenen PDF-Renderer**.
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
- [x] **M2 — Toolchain & Shared-Modul:** Gradle 9.4.1 / AGP 9.2.1 / Kotlin 2.2.x,
      Versionskatalog; Monorepo-Umbau zu `shared/` + `android/`. Modell,
      Schema, Sync und Strich-Mathematik liegen jetzt **einmal** in `shared/`
      und sind ohne Emulator unit-testbar → **erledigt**
- [ ] **M3 — Desktop-Client MVP (Compose Multiplatform):** OneNote-artige
      Planungsoberfläche, **kein Ink** — Kern ist die Medien-Einbindung
      (PDF, Bilder, Video, Audio) inkl. Schema-Erweiterung (schemaVersion 2);
      liest/schreibt dasselbe Schema direkt via WebDAV;
      Pakete für Linux (.deb), Windows 11 (.msi), macOS (.dmg) via `jpackage`
  - [x] Grundgerüst läuft unter Linux: Abschnitts-Reiter, Seitenliste mit
        Unterseiten, Seitenanzeige mit Papierraster, Strichen und Texten.
        Am Board gezeichnete Striche werden korrekt dargestellt — dieselbe
        Glättungsmathematik aus `:shared` über `ComposePathSink` (Skia).
  - [x] Bearbeiten: Abschnitte und Seiten anlegen/umbenennen/löschen,
        Unterseiten, Textfelder setzen/ändern/löschen, Papierstil,
        Rückgängig/Wiederholen, WebDAV-Pfad je Abschnitt. Speichern
        gebündelt (400 ms) über den geteilten `DocumentStore`; die App
        öffnet wieder bei der zuletzt bearbeiteten Seite.
  - [ ] Medien-Einbindung (PDF als Anhang/Ausdruck, Bilder, Video, Audio)
  - [ ] WebDAV direkt aus dem Desktop-Client
  - [ ] Paketierung (.deb / .msi / .dmg)
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
  selbst gehostetes NAS-WebDAV; Wahl fiel auf das **NAS**. Begründung
  nachgeschärft (19.08.2026): Das DSGVO-Argument trägt nur begrenzt, da an der
  Schule ohnehin Teams/MS 365 läuft. Tragfähig sind zwei andere Gründe —
  **der Tenant gehört der Schule, nicht dir** (bei Schulwechsel ist alles weg,
  während ein mehrjähriges Vorbereitungsarchiv gerade überdauern soll), und
  **OneDrive kann kein brauchbares WebDAV** (es bräuchte Graph-API mit
  MSAL-Anmeldung in beiden Clients, oft mit Administrator-Zustimmung).
  Der WebDAV-Server ist bereits eingerichtet.
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
- Medien am **Board**: Video/Audio am Board abspielen? Große Dateien —
  komplett syncen oder vom WebDAV streamen? (PDF ist gelöst: die
  Ausdruck-Variante liefert dem Board annotierbare Bilder, siehe §2a.)
- Repo-Umbenennung Ordner `inktest` → `skribo`? (Repo heißt bereits `Skribo`.)
