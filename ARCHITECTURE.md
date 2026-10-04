# Fyr — Architecture

A local-only Android habit tracker. One promise governs every decision below:
**"no account · no tracking"** is a property of the *build*, not a policy — the
manifest cannot open a socket except for the one version check §7 names, the
backup rules cannot leak, and the only way data leaves the app is a file the
person picks themselves.

- Kotlin · Jetpack Compose (Material 3) · minSdk 26 · target/compileSdk 35
- No account, no analytics, no storage permission; `INTERNET` is spent on
  one version check (§7)
- Tests: 38 JVM unit tests (`:app:testDebugUnitTest`)

---

## 1. Layer map

```
com.fyr
├── data          // everything that touches disk; no Compose imports
│   ├── Models.kt        FyrState, Habit, TrashedHabit, Category
│   ├── FyrCodec.kt      JSON ↔ models, pure functions, per-element decode
│   ├── Store.kt         the single source of truth; persistence + quarantine
│   ├── Backup.kt        export / strict parse / merge (SAF archives)
│   ├── Dates.kt         epoch-day arithmetic (no java.util.Date anywhere)
│   └── Pictures.kt      avatar staging, decode, circle-crop
├── domain
│   └── Stats.kt         streaks, rates, away — all derived, never stored
├── update
│   └── UpdateChecker.kt the launch gate's verdict: one HTTPS GET + persistence
└── ui
    ├── nav/FyrRoot.kt   one activity, one window, route + tab state, wiring
    ├── update/UpdateGate.kt  the door: checking / blocked / open
    ├── today/ calendar/ insights/ settings/   the four tabs
    ├── add/ habit/ welcome/                   sheet, detail, first-run
    ├── components/       Calendar.kt (DayCell), HabitRow, StreakChip, …
    └── theme/            colors, Poppins/Qurova type, halo graphics
```

Dependency direction: `ui → domain → data`. `data` never imports `ui`.
`update` sits alongside `domain`: it reads models from `data` (only the
build's own `versionCode`) and is the one package allowed to open a socket.
`Stats` reads models from `data` and is the only thing that defines what a
word like *streak* or *on time* means — screens never re-implement it.

## 2. State flow

`Store` holds one immutable `FyrState`, exposed as `StateFlow<FyrState>`.
Every mutation is a method on `Store` (single writer); every screen receives
the state and callbacks. There is no view-model layer by design: the state is
one small document, and the mutation set (~30 methods) *is* the app's API.
Persistence happens inside the same call — `mutate { … }` applies the change,
writes `SharedPreferences` (single JSON-ish document per key), then publishes.

Threading: mutations enter on `Dispatchers.Main`; disk work runs on a
supervised `io` scope. Callbacks that land in Compose state (staging photos,
backup results) hop back to `Main` before firing.

## 3. Persistence & corruption resistance

- **Typed keys** — reads go through `prefs.all` with type checks; a key whose
  runtime type drifted (string → int) is caught instead of crashing the load.
- **Per-element codecs** — `FyrCodec` decodes each habit individually. One
  unreadable habit is *quarantined* (written to `filesDir/quarantine/`, the
  key rewritten without it) rather than taking the whole document down.
  A document that cannot be read at all is written to quarantine first and
  replaced by a fresh baseline — **never silently, never in a loop**: the
  quarantine sweep runs once per process, after `_state` is published.
- **Schema stamp** — `schema` records `SCHEMA_VERSION` (currently 1) using
  `maxOf()` so old installs upgrade in place; migrations become a single
  `when` in `Store`.
- **Durable writes** — `persist(durable = true)` swaps `apply()` for a
  blocking `commit()` and returns its verdict. Only avatar operations use it,
  because they delete files *on the strength of the write*; everything else
  stays on `apply()` (the platform flushes on pause). Lint's `ApplySharedPref`
  warning is suppressed on `persist` with the reasoning in its KDoc.
- `allowBackup=false` + `fullBackupContent` + `dataExtractionRules` each
  exclude everything (see §7).

## 4. Dates & the midnight problem

Everything date-shaped is an **epoch day (`Int`)** — `Dates` does all
arithmetic (`today()`, `startOfMonth`, `dow`, `addMonths`). No `Calendar`,
no time-of-day, no timezone traps in the domain.

The app's clock problem is *what happens at 00:00 while a screen is open*.
Convention: screens read `today` from `FyrRoot`, which refreshes on lifecycle
resume (`LifecycleResumeEffect`) **and** on a day-change signal. Screens that
seed per-day scratch state (month grids, `AddHabitSheet`'s start-date) re-seed
in a `LaunchedEffect(today)` keyed on that value — so a composition straddling
midnight re-renders against the new day instead of editing the old one.
Selection state that must *not* jump (`CalendarScreen`'s user anchor,
`HabitDetail`'s month follow) is explicitly held apart from the seed
(`userMoved` / `anchorWasDay` flags).

## 5. Stats semantics (the words on the screen)

`Stats` is pure and fully unit-tested. Definitions that matter:

- **Due** — `h.isDueOn(day)`: weekday in schedule, not a skip day,
  `day >= createdAt`. A non-due day closes nothing: it neither breaks nor
  extends a streak (skip days are ice, not failure).
- **Streak** — consecutive *due* days completed; a due day not completed ends
  it (strict). Today is grace while `todayInProgress` — the day isn't over.
- **Settled / rate** — windows end at *yesterday*; today that is due but
  unticked is excluded (`settled`), so the percentage never dips over a day
  that can still be completed. Completed today settles back in.
- **Away** — `aways()` measures *due days* (not calendar days): an open run
  extends `to` to today while the habit is live; a completed due day closes
  the window. Non-consecutive schedules are supported (this was a fixed bug —
  `StatsTest` carries the regression case).
- **"N ticks" everywhere** (masthead, Insights, import dialog) =
  `Stats.total` = ticks on days that were genuinely due. The backup file's
  raw count may be one higher (a mark on an excused day); `Backup.tickCount`
  documents that distinction.

## 6. Backup (export / import)

The single sanctioned data-egress path. Format: one JSON document
(`Backup.kt`), base64 avatar, `version` + `exportedAt` + per-field keys.

- **Export** — SAF `CreateDocument` (`application/json`), suggested name
  `fyr-backup-YYYY-MM-DD.json`. Truncating ("wt") write. Nothing else happens
  — no share sheet, no upload, no copy elsewhere.
- **Parse is strict** — typed extraction; *any* unreadable habit, missing
  root key, wrong shape, or truncated file → `null` and the UI says
  "That file isn't a Fyr backup, or it's damaged." Unknown extra keys and a
  newer `version` are tolerated (forward-compatible read; tested).
- **The dialog decides** — reading only fills a confirmation AlertDialog
  (counts + export date). Nothing is applied until the person picks:
  - **Merge** — union: this phone's copy of each habit wins (renames, retires,
    deletions stay); ticks union per habit; trash de-duplicated by id; a habit
    deleted here is never revived by the archive. Tested idempotent
    (importing the same file changes nothing — verified on device).
  - **Replace** — error-colored, described as "clears this phone first".
- Round-trip and refusal guarantees: `BackupTest` (10 tests).

## 7. Security posture

| Control | How it's enforced |
|---|---|
| No tracking, no collection | Exactly three permissions in the merged manifest; `:app:verifyPermissionManifest` (wired into `check`) fails the build if the set drifts, in either direction |
| The one socket | `INTERNET` is read once, at launch, by `UpdateChecker` — a pinned `raw.githubusercontent.com` URL returning a version number (§7a). No identifiers go out, none come back |
| No cloud backup, no D2D | `allowBackup="false"` **and** `@xml/fullBackupContent` (API 26–30) **and** `@xml/data_extraction_rules` (API 31+) — all three exclude `sharedpref` + `file` entirely |
| No storage permissions | All file egress/ingress through SAF pickers the person invokes |
| Release hardening | R8 minify + resource shrinking + conditional signing (skips with a clear message if `FYR_STORE_PASSWORD` is absent); `lintVitalRelease` gate |
| Update cannot be dismissed | `UpdateGate` wraps the whole activity content: the shell is behind one `when`, so a build that owes an update never composes a route, a dialog or a launcher |
| Debug ≠ release | Release is build-verified only and never installed over debug — installing it would wipe `com.fyr` data. `applicationId` is untouched; debug remains the test target |
| VIBRATE only | The sole behavioural permission — haptics; zero privacy cost, normal-level |

`.gitignore` keeps `local.properties`, keys and build outputs out of any
future repo.

### 7a. The update gate

Sideloaded APKs have no store to announce a new version, so the app
carries the announcement itself. Three files, one direction of travel:

- `releases/version.json` in the repo — `versionCode`, `versionName`,
  `url`, `notes`. Public, unauthenticated, and the *only* input.
- `UpdateChecker` — one HTTPS GET at launch, strict parse, and a verdict
  persisted to a preferences file of its own. Every failure mode
  (unreachable, non-2xx, malformed, missing key) resolves to
  *Unreachable* rather than throwing, because this runs before the first
  frame.
- `UpdateGate` — `Checking` → `Open | Blocked`. `Blocked` is a full
  screen that takes all pointers and swallows back.

Two properties worth stating, because both were bugs before they were
designs:

1. **Offline-proof, without bricking.** The verdict is stored, so a
   build that knows it is behind blocks from that record alone. The
   stored record is only ever *consulted* when it outranks the running
   `versionCode`, and it is dropped the moment a check finds the running
   build satisfies it — so installing the update clears the lock on its
   own, and an updated build can never inherit the old one's lock-out.
2. **Fail open.** A missing verdict never blocks. The alternative is an
   offline-first app that will not open offline.

## 8. Build & CI expectations

```
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:verifyPermissionManifest
./gradlew :app:assembleRelease          # R8 + lintVitalRelease
```

- Lint: 0 errors; the 9 remaining warnings are pre-existing (dependency
  version suggestions, `UnusedAttribute` on widget/manifest API-31 attrs).
- Tests live in `app/src/test` and run on the JVM with `org.json` as a real
  test dependency (the platform stub throws):
  - `FyrCodecTest` (10) — round-trips, per-element quarantine, forward keys
  - `StatsTest` (13) — streak/rate/away semantics incl. regression cases
  - `BackupTest` (10) — export→parse fidelity, strict refusal, merge rules
  - `NamesTest` (5) — name limits and greeting rules

## 9. UI architecture notes

- **One window.** `FyrRoot` owns the route stack, tab state and the FAB's
  pager. Bottom-bar taps animate the pager; the bar reads its position from
  `PagerState` internally. Detail pushes over the tabs; sheets/dialogs float
  above the window ("the box over the screen is a window of its own").
- **Launchers at screen scope.** Any `rememberLauncherForActivityResult`
  lives in the screen composable (not inside a dialog) so results arrive
  after recomposition — photo picker, export, import follow the same
  three-step pattern: stage → review → adopt.
- **Focus discipline.** Interactive verification taps are guarded on
  `topResumedActivity == com.fyr` so testing never steals the phone mid-use;
  the app itself never launches anything without an explicit tap.
- **Accessibility.** `DayCell` merges into one semantic node with a full
  description ("Day 4, today, in progress" / "…, unavailable") and passes
  `enabled` to the gesture so future days don't announce dead actions;
  icon-only controls carry `contentDescription` + `Role.Button`
  ("Edit profile", "About this app", "Back", month arrows). Touch-target
  resizing is deliberately deferred (see §10).
- **Verified UI invariants** (both themes, screenshot + pixel checked):
  AboutDialog design, `frostOpen` blur backdrop, fixed `StreakChip`
  104×44 dp, Backup card layout, import dialog, heatmap, calendar marks.

## 10. Deferred recommendations

Reviewed and intentionally left for a later round (each with a reason):

1. **Back during a tab swipe** (M6) — pager + back-handler interplay is
   subtle; needs its own test pass before touching verified navigation.
2. **Widget frozen rows** (M8) — widget provider is out of scope of this
   round's touched surfaces; rows show frozen state only after a refresh.
3. **Touch-target sizes** on calendar cells (~40 dp) — bumping to 48 dp
   shifts the verified grid geometry; do it as a dedicated visual change.
4. **Compose stability config file** — strong skipping already covers the
   benefit; a config could only introduce behavioural drift.
5. **Dependency bumps** (`core-ktx`, `lifecycle-runtime-compose`,
   `activity-compose`) — build works and is verified on current versions;
   bump deliberately with a full re-verification cycle.
