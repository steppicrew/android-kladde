# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Kladde — private diary and data tracker (working name)

Repo: `steppicrew/android-kladde` (**public**, no licence yet). Package / namespace:
`de.steppicrew.kladde`; debug builds install side by side as `de.steppicrew.kladde.debug`.
Commits are authored as `steppicrew <kladde@steppicrew.de>` (set in the repo's git config).

## What this is

An Android app that combines a **private diary** with a **flexible personal data tracker**
(blood pressure, drinks, workouts, mood, vacations, …), with **end-to-end encrypted sync**.

Core promise: *your data is readable only on your devices.* Any server or storage
backend only ever sees ciphertext.

### Product pillars
1. **Diary** — mostly text (Markdown), one or more entries per day.
2. **Trackers** — user-defined, timestamped data points with graphs and export.
3. **Routines** — planned, recurring tasks with reminders, ticked off as done or skipped
   (e.g. "workout every day at 18:00 except Wed and Thu").
4. **E2EE sync** — to the user's own storage (WebDAV / Nextcloud / Synology) or to an
   optional hosted sync service (planned paid tier).
5. **Export** — CSV/JSON for raw data, PDF reports (e.g. a blood pressure report for a doctor).
### Non-goals (for now)
- No social features, no public sharing.
- No ads, analytics, crash reporting or other SDKs that send data off the device.
- No AI features that send content to third parties.
- iOS/desktop are out of scope for v1, but the data format and crypto must stay
  platform-neutral so other clients can be added later.
## Status

Early concept / pre-MVP. Items marked **(proposed)** are not final; check with the
owner before treating them as fixed. Record decisions in `docs/decisions/` (ADR style).

What exists so far: the Gradle skeleton, a minimal `core/model` + `core/data` (trackers and
data points in plain Room), the ADX receiver in `integration/adx`, and the first step of
routines in `feature/routines` (see "Routines & reminders"). There is **no vault,
no crypto and no sync yet** — `docs/crypto.md` doesn't exist yet; write it before
`core/crypto`. `VaultLock` is a stub that is unlocked unless locked through the debug
backdoor. Hilt and SQLCipher are not wired yet (manual wiring in `KladdeApp`). The privacy
policy is live on GitHub Pages (see "Privacy & legal"). Known gaps: no revoke screen for ADX
senders (`AdxReceiver.revoke` exists), and the entry list shows the sender's package name
instead of its label.

## Tech stack (proposed)

| Area | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM + unidirectional data flow, Kotlin Coroutines/Flow |
| DI | Hilt |
| Local DB | Room on top of SQLCipher (DB encrypted at rest) |
| Crypto | libsodium (via Lazysodium): XChaCha20-Poly1305, Argon2id, X25519 |
| Key storage | Android Keystore (wraps the locally cached vault key) |
| Background work | WorkManager (sync, reminders) |
| Sync transport | WebDAV client (OkHttp-based) behind a `SyncBackend` interface |
| Health data import | Android Health Connect (optional, opt-in) |
| Charts | (open) Vico or a small custom Compose canvas implementation |
| Build | Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`) |
| Toolchain | Same as ADX: AGP 9.5, Gradle 9.6, JDK 21, target SDK 37 (compile SDK 37.2 here, 37.0 in ADX). AGP 9 compiles Kotlin itself, so Android modules apply no `kotlin-android` plugin; `kotlin-jvm` (2.2.20) is for `core/model` only |
| Min SDK | (open) proposed 26 — set to 26 for now |
| App integration | ADX receiver (`../android-apx`, composite build); see below |

## Module layout (proposed)

```
app/                 Android app shell, navigation, DI wiring
core/model/          Pure Kotlin domain models (no Android deps)
core/crypto/         Key derivation, encryption, key wrapping — pure Kotlin + libsodium
core/data/           Room DB, repositories
core/sync/           Op-log, merge logic, SyncBackend interface + WebDAV impl
feature/diary/       Diary UI
feature/trackers/    Tracker definitions, data entry, quick-add widgets
feature/routines/    Planned tasks, recurrence, reminders, done/skip, calendar mirror (exists)
feature/insights/    Graphs, overlays, correlations
feature/export/      CSV/JSON/PDF export
integration/adx/     ADX receiver: AdxStore on top of core/data, CONNECT approval screen,
                     new-label notifications and screen
docs/                Architecture notes, crypto spec, ADRs
```

`core/model`, `core/crypto` and the merge logic in `core/sync` must stay **free of Android
dependencies** so they can be unit-tested on the JVM and reused by future clients.

## Domain model

Everything is a **timestamped event**, not "one row per day".

- **Tracker** — a user-defined definition: name, icon, type, fields, unit(s), presets.
- **DataPoint** — one measurement/event of a tracker at a point in time (or a time range).
- **DiaryEntry** — Markdown text with a timestamp; may reference DataPoints of that day.
- **Tag** — free-form labels usable on entries and data points.
- **Routine** — a planned, recurring task: title, recurrence rule, local time, time mode
  (floating / fixed zone), reminder settings, optional linked Tracker
  (see "Routines & reminders").
- **RoutineCompletion** — the outcome of one occurrence: `DONE` or `SKIPPED`
  (+ optional note, optional linked DataPoint). `MISSED`, `PAUSED` and `BLOCKED` are
  derived, never stored.
- **BlockRule** — "routine B doesn't happen when routine A (or anything tagged X) happens";
  see "Connected routines".
- **RoutinePause** — a from–until period in which one, several or all routines are paused;
  optionally linked to a `RANGE` data point (e.g. a vacation).
Tracker types:
- `NUMBER` (+ unit) — weight, sleep hours
- `MULTI_NUMBER` — e.g. blood pressure: systolic / diastolic / pulse as one data point
- `COUNTER` — drinks, cigarettes (+1 taps, optional sub-type)
- `DURATION` (+ optional fields) — workouts
- `SCALE` (1–5 or custom range) — mood, calm level, "how was your day"
- `BOOLEAN` — did / didn't
- `SHORT_TEXT` — e.g. "well-being: …"
- `CHOICE` — single/multi select from a defined list
- `RANGE` — date/time span: vacation, illness, a project (shown as bands behind graphs)
Rules:
- All IDs are random UUIDs (v4 or v7), generated on the device. Never auto-increment IDs
  across devices.
- Store timestamps as UTC instants **plus** the original time zone ID (the user's
  "local day" matters for a diary).
- Tracker definitions are versioned; changing a tracker's fields must not invalidate
  existing data points.
- Units are stored explicitly with values; never assume a unit.
## Routines & reminders

**Decided with the owner (2026-11-25) — the first version, built in this order, each step
usable on the phone before the next:**

1. Routines with days and times, and a 7-day view (yesterday, today, the next 6 days) in a
   "Routinen" tab next to "Einträge". **Built.**
2. Conditions: "not on days with …" — a routine drops out on days another routine happens
   (block rules below, `SAME_DAY`, `DROP` only). **Built.**
3. Single days: move one occurrence to another date, or cancel it ("Diesmal verschieben auf…",
   "Diesmal absagen"). Conditions follow the moved/cancelled dates automatically. **Built.**
4. Vacations: a pause from–until for **all routines by default**, or only chosen ones.
5. Then reminders (notifications with Erledigt / Auslassen). **Not in the first version.**

- **One kind of item** for tasks and events: "Sport" and "Rüdersdorf" are both routines;
  ticking off (`checkable`) is a switch per routine. A non-checkable routine just happens and
  is never missed. Anything can be a condition for anything.
- The owner found the ADX label work too complicated: keep routines simple in what the user
  sees. Out of the first version: linked trackers, calendar mirror, streaks and statistics,
  `TIME_OVERLAP`/`SHIFT`/offsets/tags in block rules, schedule versions (`validFrom`).
- Built so far: `Routine` + `Recurrence` (every day / weekdays / every N days, stored as an
  RRULE subset) and `RoutinePlan` in `core/model`; tables `routines` and
  `routine_completions` (DB version 4); `RoutinesScreen` and `RoutineEditor` in
  `feature/routines`. A changed schedule starts the day it is saved.

The full design below is the target; the first version is the subset above.

Example: "Workout every day at 18:00 except Wednesday and Thursday" →
`RRULE:FREQ=WEEKLY;BYDAY=MO,TU,FR,SA,SU` + local time `18:00` (floating, see below).

Model:
- Recurrence uses **RFC 5545 RRULE** semantics (daily/weekly/monthly, BYDAY, INTERVAL,
  UNTIL/COUNT) plus `EXDATE` for single exceptions ("not this Friday"). The UI offers
  simple presets (every day / weekdays / pick days / every N days); the RRULE is the
  storage format. Use a tested library or a small, heavily tested implementation in
  `core/model` — no Android dependencies.
- **Time mode per routine:**
  - `FLOATING` (**default**) — "18:00 wherever I am". The time is local wall-clock time
    with no zone attached (like RFC 5545 floating time) and is evaluated in the device's
    *current* time zone. Travel to Tokyo → reminder at 18:00 Tokyo time.
  - `FIXED_ZONE` — tied to a specific zone, e.g. a weekly call at 18:00 `Europe/Berlin`;
    shown converted to local time while travelling.
- Both modes keep wall-clock time across DST changes (18:00 stays 18:00).
- **An occurrence is identified by its local date**, not by an instant:
  `(routineId, localDate)`. This guarantees at most one occurrence per local day, even when
  a time zone change would make 18:00 happen twice (flying west) or not at all that day
  (flying east). If the time already passed after a zone change, the occurrence is still
  open for ticking but no late reminder is fired (configurable).
- Store the time zone in effect when a completion was recorded, for correct history display.
- **Occurrences are computed, not stored.** Only completions are stored, keyed by
  `(routineId, occurrence date)`. A past occurrence with no completion = `MISSED`.
- Editing a routine's schedule applies from now on; past completions stay untouched
  (store `validFrom` on schedule versions).
- One-off tasks are routines with a single occurrence.
Moving, adding and cancelling single occurrences:
- **Move:** an occurrence can be moved to another date and/or time ("this week's Tuesday
  workout → Thursday"). Stored as an override keyed by the *original* local date (like an
  iCalendar `RECURRENCE-ID` exception): `(routineId, originalDate) → newDate, newTime`.
- **Swap:** UI shortcut that exchanges two days in one week. Swapping a training day with a
  rest day (Tue ↔ Thu) is a single move (Tue → Thu, Tuesday becomes a rest day). Swapping two
  training days that have different times or linked settings is two moves.
- **Add extra:** an additional one-off occurrence on a day that isn't scheduled
  ("rest day doesn't happen → workout is on again"), like an iCalendar `RDATE`.
- **Cancel:** remove a single occurrence (`EXDATE`) — distinct from `SKIPPED`, which means
  "it was due and I didn't do it". Cancelled occurrences don't count against streaks.
- Completions reference the occurrence by its original date, so moving an already-ticked
  occurrence keeps its completion. Insights and streaks use the *actual* (moved) date.
- UI: week view with drag & drop, plus "Move to…", "Swap with…", "Add extra session" actions.
Connected routines (blocking rules):
- A routine can be **blocked by** other routines. Example: "Rüdersdorf" every Wednesday
  blocks "Workout" → the workout schedule is simply "every day 18:00 except Thursday,
  and not on days with Rüdersdorf".
- The blocked days are **derived live, never stored**: if a single Rüdersdorf occurrence is
  cancelled, the workout is back that Wednesday; if it moves to Friday, Wednesday becomes a
  workout day and Friday doesn't. No manual fixing of the workout schedule.
- Rule: `BlockRule(blockerRoutineId | blockerTag, scope, offsetDays = 0)`
  - scope `SAME_DAY` — any blocker occurrence on that local date blocks.
  - scope `TIME_OVERLAP` — only blocks if the time windows overlap (needs durations).
  - `offsetDays` for things like "no workout the day after a long hike" (offset 1).
  - Blocking by **tag** lets several routines/events share one rule ("travel", "family").
- Only **active** blocker occurrences block: cancelled or paused blocker occurrences don't.
  Moved blocker occurrences block at their new date.
- Derived status of a blocked occurrence: `BLOCKED` — no reminder, doesn't count against
  streaks or completion rates (like `PAUSED`).
- **Explicit beats derived:** an occurrence the user moved or added manually onto a blocked
  day is shown (with a hint "conflicts with Rüdersdorf"), not hidden.
- Behaviour when blocked, per rule: `DROP` (default — the session just doesn't happen) or
  `SHIFT` (move to the next free day within the same week, if any) **(proposed)**.
- Dependency graph must be acyclic (A blocks B blocks A is rejected in the UI); evaluate
  in topological order. One-off tasks/events can be blockers too.
- Calendar mirror rewrites affected events whenever a blocker changes.
Pausing:
- A **RoutinePause** has `from` and `until` (local dates, inclusive; `until` may be open
  = "paused until I resume"), an optional reason/note, and a scope: one routine, a set of
  routines, or **all routines** ("vacation mode").
- A pause can be **linked to a `RANGE` data point** (e.g. a "Vacation" entry from–until):
  creating or editing the vacation offers "pause routines during this time?", and the
  pause follows the range if its dates change.
- Occurrences inside a pause are derived as `PAUSED` — no reminders, and they count
  neither as missed nor as done: **streaks and completion rates skip them**, they don't break.
- Ticking a paused occurrence is still allowed (did a workout on vacation anyway → `DONE`).
- Pauses are normal synced objects; creating/ending one triggers reminder rescheduling.
- UI: pause from the routine (quick options: "1 week", "until…", "until I resume") and a
  global "pause all" toggle; paused periods are shown shaded in the routine calendar.
- Completion can optionally prompt for a linked tracker, e.g. "Done" on the workout routine
  opens a quick workout entry (type, duration) → creates a DataPoint and links it.
- Insights: streaks, completion rate per week/month, done/skipped/missed calendar,
  overlay with other trackers (e.g. mood on workout days vs. rest days).
Reminders:
- Schedule with `AlarmManager` (exact alarms where the user grants it, inexact as
  fallback); only the **next** occurrence per routine is scheduled. Reschedule after boot
  (`BOOT_COMPLETED`), time/time-zone change, app update and every sync that changes routines.
- Notification actions: **Done** / **Skip** / **Snooze**. These must work without
  unlocking the app (see below).
- Options per routine: reminder offset, a follow-up nag if not ticked, quiet hours.
- **Notification privacy:** the notification text is outside our encryption. Default to
  showing the routine title only on the private lock screen; offer a "generic text"
  option ("You have a reminder") and use `VISIBILITY_PRIVATE` / a public version.
Locked vault + notification actions:
- The reminder schedule itself (routine ID, next trigger time, display title) is kept
  in a small local store protected by the Android Keystore, so alarms work while
  the vault is locked.
- A Done/Skip tapped while the vault is locked is written as a **pending completion**
  encrypted to the vault's public key (libsodium sealed box). The device can write it
  but can't read it until unlocked; on next unlock it is merged into the op-log. This
  keeps "tap Done from the notification" working without keeping VK in memory.
## Integration API for other apps: ADX

Other apps (first: "Steppi's Beer Counter") log data into Kladde through **ADX — App Data
Exchange**, the owner's own library in `../android-apx` (decision: `docs/decisions/0001-app-integration-via-adx.md`).
The wire format, consent model and status codes are defined in
`../android-apx/docs/ADX-SPEC.md` — read it there, don't restate it here. Kladde is a
*receiver*; senders use `adx-sender`, so Kladde ships no SDK of its own.

What ADX does for us (`adx-receiver`): exported submit service + broadcast, caller identity
from the binder uid / `PendingIntent` creator, consent per package **and** signing cert,
version check, limits, validation, and the rev rule (entries keyed by
(sender, stream, id); equal rev → `DUPLICATE`, lower → `STALE`).

What Kladde does (`integration/adx`):
- `KladdeAdxStore : AdxStore` — maps ADX ops onto data points. Each data point keeps its
  **source** (sender package, stream, ADX id, rev); deletes keep a tombstone row with the
  rev. The full op JSON is kept so unknown fields and attrs aren't lost.
- `ConnectActivity` — the approval screen: per proposed stream, create a new tracker,
  map to an existing tracker of the same type, or decline. `AdxStreamGrant.target` holds
  the Kladde tracker UUID.
- **New labels** (`docs/decisions/0002-unknown-labels-from-other-apps.md`): the first entry
  with a `sub` the tracker has no `SubTypeLabel` record of opens one and posts a
  notification (`LabelNotifications`, Ignore via `LabelActionReceiver`); `LabelsActivity`
  asks for a display name and count-only vs. default values for the stream's attrs. The
  main screen shows a counter while labels are open. Sender values always beat a label's
  defaults. The notification permission is asked for on Allow in `ConnectActivity`.
- Revoking a sender: `AdxReceiver.revoke(packageName)`.

**Locked vault — current behaviour (option B):** while the vault is locked, every
`AdxStore` call throws `AdxBusyException`; the sender keeps the ops in its outbox and
retries with backoff (up to 6 h). **TODO — option A:** accept while locked — `currentRev()`
returns `null`, upserts/deletes are written as sealed-box pending records (same mechanism
as pending routine completions), and the rev rule is applied again when they are merged
on unlock. Needs `core/crypto` first. Until then a locked Kladde does *not* honour the
spec's "a locked Kladde encrypts on arrival" sentence.

Known metadata outside the vault: ADX's consent store (plain SQLite) holds sender package
names, labels and the stream → tracker mapping. It must stay readable while locked.
## Calendar mirror (optional, proposed)

Routine occurrences can be mirrored into the Android calendar (via `CalendarContract`),
so they show up in any calendar app and in calendars synced elsewhere (Google, or
Nextcloud via DAVx⁵).

- **One-way, app → calendar** in v1. The app is the source of truth and rewrites the
  mirrored events on every change (moves, extras, cancellations, pauses).
- Target: either an **app-owned local calendar** (stays on the device, not synced) or a
  calendar the user picks (then synced by that calendar's account).
- **Privacy:** mirrored events are **plaintext outside our E2EE**, and may be synced to
  Google or a server. Strictly opt-in per routine, with a clear warning, and an option
  for generic titles ("Routine") instead of real names.
- **Floating time:** many calendars/servers (e.g. Google) don't support floating times. For
  floating routines the app writes events in the current device zone and rewrites
  upcoming events on time zone change (`ACTION_TIMEZONE_CHANGED`).
- Write a rolling window (e.g. next 8 weeks) of single events rather than one recurring
  event with exceptions — simpler and robust against calendar apps that mishandle exceptions.
- The calendar can show the routines and remind about them, but **can't tick Done/Skip**.
  So the app's own reminders stay the default; the user can switch a routine's reminders
  to "calendar only" if they prefer.
- **Later (maybe):** two-way — detect when the user drags a mirrored event in the
  calendar app and turn that into a "move" in the app.
## Encryption (must-read before touching `core/crypto` or `core/sync`)

Full spec goes in `docs/crypto.md`. Summary of the intended design:

- **Vault key (VK):** random 256-bit key that encrypts all user data.
- **Passphrase:** the user's vault passphrase → Argon2id (parameters stored with the
  vault) → key-encryption key (KEK) → wraps VK. Only the wrapped VK is ever synced.
- **Recovery key:** a random high-entropy key shown once at setup, which also wraps VK.
  No other recovery exists — losing both means losing the data. The UI must say so clearly.
- **On device:** VK is cached wrapped by an Android Keystore key (optionally gated by
  biometrics / device credential). The plaintext VK lives only in memory while unlocked.
- **Vault key pair:** an X25519 key pair; the private key is encrypted with VK, the
  public key is stored on devices so they can write sealed-box data (e.g. pending
  routine completions) while locked.
- **Payload encryption:** XChaCha20-Poly1305 with random nonces; authenticated
  associated data binds the ciphertext to its object ID, type and format version.
- **Metadata minimization:** remote file names, sizes and directory structure must not
  reveal content types, tracker names or dates beyond what's unavoidable.
Hard rules:
- **Never** roll your own crypto primitives. Use libsodium constructions only.
- **Never** log, print, or put plaintext user content, keys or passphrases in crash
  messages, notifications, analytics or exceptions.
- **Never** send plaintext to any network endpoint, including the hosted sync service.
- Every encrypted format carries a version number; keep decoders for old versions.
- Crypto changes need tests with known-answer vectors and must be noted in an ADR.
## Sync (proposed design)

Goal: work on "dumb" storage (plain WebDAV) without a smart server.

- Each device keeps an **append-only operation log** (create/update/delete ops with a
  hybrid logical clock timestamp + device ID).
- Ops are batched, encrypted and uploaded as immutable segment files under a
  per-device path, e.g. `vault/<vaultId>/devices/<deviceId>/<seq>.bin`.
  A device only ever writes to its own directory → no write conflicts on the server.
- Devices download other devices' new segments and **merge deterministically**:
  last-writer-wins per field using HLC, deletes as tombstones.
- Periodic encrypted **snapshots** allow compaction and fast setup of new devices.
- `SyncBackend` is an interface (`list`, `get`, `put`, `delete`); WebDAV is the first
  implementation, the hosted service a later one. The merge logic must not depend
  on the backend.
- Sync must be resumable, idempotent and safe to interrupt at any point.
## Export

- CSV per tracker (one row per data point, ISO-8601 timestamps, explicit unit columns).
- Full JSON export of everything (documented schema, versioned) — this is the user's
  "no lock-in" guarantee, so keep it complete and stable.
- PDF reports (e.g. blood pressure for the last N weeks: table + chart + min/avg/max).
- Exports produce plaintext: always warn the user and use the Android share sheet /
  SAF; never write exports silently to shared storage.
## Privacy & legal

- Blood pressure and similar values are health data (GDPR Art. 9). The E2EE design is
  what keeps the hosted service from ever processing them in plaintext — don't weaken it.
- No third-party SDKs that phone home. Any new dependency with network access needs
  an explicit justification in the PR.
- Health Connect access is opt-in, per data type, and revocable.
- Aim for an F-Droid-compatible build flavor (no proprietary dependencies).

Privacy policy (linked from Play): https://steppicrew.github.io/android-kladde/
- Its only source is `app/src/main/res/values*/privacy.xml` (English default, German).
  `scripts/privacy-page.py` builds one page per language (`index.html`, `index.de.html`)
  with links to every language at the top and a hint offering the browser's language.
  **Never redirect:** Play rejects a policy URL that doesn't land on the policy text.
  `.github/workflows/pages.yml` publishes it on every push to `main` that changes those
  files. `PRIVACY.md` is generated too: `./scripts/privacy-page.py --markdown PRIVACY.md`.
- Every sentence is a claim about current behaviour — **change the text in the same commit
  as the behaviour.** It currently promises: no `INTERNET` permission, no analytics /
  crash reporting / ads, data only in app-private storage, cloud backup off
  (`allowBackup="false"`), ADX senders write-only and approved per app + cert, new entry
  types asked about in the app and by a notification without details on the lock screen. Adding
  sync, export, a revoke screen or at-rest encryption means updating it.
- Play Console declarations must match the shipped build — **remind the owner to update
  them in the same release as the behaviour change:**
  - *App access* ("Zugriff auf die App") is **Nein** (nothing restricted). The release that
    ships the vault lock / biometric unlock must switch it to **Ja** with reviewer
    instructions: no account, create a vault with any passphrase, biometrics optional,
    passphrase/device PIN always unlocks. So biometrics must never be the only way in.
  - *Data safety* ("Datenerhebung und Sicherheit") is **Nein** (no data collected — nothing
    leaves the device). The release that adds sync (WebDAV or hosted) must re-check it
    against Google's current definition of "collected", even for E2EE data; the hosted
    service most likely needs **Ja**. User-initiated share-sheet export should not change it.
- A new language needs a complete `values-<lang>/privacy.xml` and an entry in the
  script's `LANGUAGES`; the script refuses partial translations.
## Coding conventions

- Kotlin official style; ktlint + detekt must pass.
- Compose: stateless composables + state hoisting; previews for UI components.
- No business logic in ViewModels beyond orchestration — put it in use cases / repositories.
- Prefer `Result`/sealed types over exceptions for expected failures (wrong passphrase,
  network down, conflict).
- Strings in `strings.xml`; the app ships in **German and English** from the start.
- Accessibility: content descriptions, sufficient contrast, scalable text.
## Testing

- Unit tests (JVM) for `core/model`, `core/crypto`, `core/sync` — these are the critical parts.
- Sync merge logic: property-based / randomized tests (multiple simulated devices,
  random op orders, interrupted uploads) must converge to the same state.
- Crypto: known-answer tests, round-trip tests, tamper tests (modified ciphertext must fail).
- Room migrations: every schema change needs a migration test.
- Recurrence: table-driven tests incl. DST switches (last Sunday in March/October),
  leap years, month-end rules, EXDATE, schedule edits with `validFrom`.
- Time zones: floating vs. fixed routines when the device zone changes east/west across
  the reminder time — exactly one occurrence per local date, no duplicate reminders.
- Overrides: move, swap, add extra, cancel; moving a completed occurrence; a moved
  occurrence landing on a paused day or on a day that already has one.
- ADX store: upsert/delete/tombstone, a new `sub` extends the tracker, a missing target
  tracker is recreated once, locked vault → `AdxBusyException`. Caller identity, consent,
  rev rule and validation are tested in `../android-apx`; don't duplicate them here. With
  option A: calls while locked merge exactly once.
- Block rules: blocker cancelled → blocked occurrence reappears; blocker moved → block
  moves with it; paused blocker doesn't block; offsets; tag-based rules; manual override on
  a blocked day; SHIFT behaviour; cycle rejection; chains (A blocks B blocks C).
- Calendar mirror: events match occurrences after moves/pauses/time zone changes;
  no orphaned events after disabling the mirror or deleting a routine.
- Pauses: occurrences inside a pause are `PAUSED`, streaks don't break, open-ended pauses,
  pauses linked to a vacation range follow date edits, overlapping pauses.
- Reminders: rescheduling after reboot / time change / sync; pending completions from
  a locked vault merge exactly once.
- UI: Compose tests for critical flows (setup, unlock, quick-add, export).
## Commands

Machine-specific settings live in `.env` (not committed; copy `.env.sample`, which
documents each one): `JAVA_HOME` (JDK 21 — the system default JDK 25 is rejected by
Gradle 9.6), `ANDROID_HOME` (an SDK with `platforms;android-37.2`; compileSdk is 37.2
because current AndroidX needs 37.1+), and `ADX_DIR` (the ADX checkout, default
`../android-apx`, pulled in with `includeBuild`, so ADX changes are picked up without
publishing). **Run Gradle through `./gw`**, which loads `.env` and then calls `./gradlew`.
Put new local configuration in `.env` + `.env.sample`, not in shell exports or
`local.properties` (which would override `ANDROID_HOME`).

```bash
./gw assembleDebug                                   # build
./gw test                                            # all unit tests
./gw :core:model:test                                # pure JVM module
./gw :integration:adx:testDebugUnitTest --tests '*KladdeAdxStoreTest*'   # one class
./gw connectedAndroidTest                            # instrumented tests (device/emulator)
./gw ktlintCheck detekt                              # lint
```

On the phone (see the `android-device-testing` skill), the debug build has a `cmd` extra,
read only in `src/debug` (the `src/release` twin is a no-op). Logs use tag `Kladde`:

```bash
adb shell am start -n de.steppicrew.kladde.debug/de.steppicrew.kladde.MainActivity -e cmd lock
# cmd: lock | unlock | status | tab:entries | tab:routines
adb logcat -d -s Kladde AdxReceiver
```

End-to-end tests against ADX use the sample sender from `../android-apx`
(`de.steppicrew.adx.sample.sender.debug`, see its CLAUDE.md for its commands).

## Working agreements for Claude

- Read `docs/crypto.md` and the relevant ADRs before changing crypto, sync or storage formats.
- Don't change on-disk or on-wire formats without bumping the format version and adding
  a migration path.
- Don't add dependencies without saying why and checking their licence (GPL-compatible
  preferred for the F-Droid flavor).
- When something here is marked **(proposed)** or **(open)** and your change depends on
  it, ask instead of deciding silently.
- Keep this file up to date when decisions are made.
## Open questions

- App name — current favourite: **Kladde**. Runner-up: **Tageslauf** (English:
  "Course of the Day"). Not final until Play Store, trademark (DPMA/EUIPO/TMview,
  classes 9 and 42) and domain checks are done.
  Dropped: Kalendarium (Play Store already has "Kalendarium" by Akstar Mobile, a holiday
  calendar in Productivity), Ephemeris (hard to remember/spell), Tagwerk (existing brands),
  Daybook and Diarium (existing diary apps).
- Licence: open source (GPL/AGPL) vs. source-available vs. closed. Affects F-Droid and trust.
- Monetization split: free BYO-storage + paid hosted sync? One-time "Pro" unlock?
- Chart library choice.
- Attachments (photos) in v1 or later? (Affects sync size and design.)
- Routines: which device fires reminders when several are synced (all? phone only?
  per-device setting?) — avoid duplicate notifications.
- Routines: exact-alarm permission handling on Android 14+ (fallback behaviour).
- Block rules: should events from the user's *calendar* (read-only) also be able to block
  routines? Useful, but needs calendar read permission and a matching rule (calendar/keyword).
- Calendar mirror: in v1 or later? Two-way edits worth the complexity?
- Hosted sync backend: hosting location (EU/Germany), stack, billing.
- ADX has no licence file yet — needs one compatible with Kladde's licence before an
  F-Droid build can include it.
- ADX locked-vault behaviour: switch from option B (BUSY) to option A (sealed-box pending)
  once `core/crypto` exists — see "Integration API for other apps: ADX".
