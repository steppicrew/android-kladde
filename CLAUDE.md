# CLAUDE.md — App Diary
 
Guidance for Claude (and humans) working in this repository.
 
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
| Min SDK | (open) proposed 26 |
 
## Module layout (proposed)
 
```
app/                 Android app shell, navigation, DI wiring
core/model/          Pure Kotlin domain models (no Android deps)
core/crypto/         Key derivation, encryption, key wrapping — pure Kotlin + libsodium
core/data/           Room DB, repositories
core/sync/           Op-log, merge logic, SyncBackend interface + WebDAV impl
feature/diary/       Diary UI
feature/trackers/    Tracker definitions, data entry, quick-add widgets
feature/routines/    Planned tasks, recurrence, reminders, done/skip, calendar mirror
feature/insights/    Graphs, overlays, correlations
feature/export/      CSV/JSON/PDF export
api/                 Integration API (AIDL service, intent receiver, consent handling)
sdk/                 Client library for third-party apps (published separately)
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
## Integration API for other apps (proposed)
 
Other apps (first test client: the owner's beer counter app, "Steppi's Beer Counter")
can log data into the diary.
 
- **Write-only by default.** Third-party apps can add, update and delete *their own* data
  points; they can't read anything. Read access, if ever, is a separate opt-in per tracker.
- **Works while the vault is locked:** incoming data is encrypted on arrival to the
  vault public key (sealed box), stored as pending, and merged into the op-log on next
  unlock — same mechanism as notification actions.
- **Transport:** an exported, permission-protected bound service (AIDL) for apps using
  the SDK, plus a simple intent action (`<appId>.action.LOG_DATA_POINT`) so automation
  tools like Tasker can log too.
- **Consent per calling app:** the first call from a package triggers an in-app approval
  ("Steppi's Beer Counter wants to log to *Beer*"), bound to package name **and** signing
  certificate digest. Users can revoke it at any time; a revoked app's calls are rejected.
- **Tracker mapping:** the calling app may propose a tracker definition (name, type, unit,
  e.g. `COUNTER` "Beer", sub-types "Pils", "Weizen"); the user accepts it, or maps it
  to an existing tracker.
- **Idempotency:** every call carries a client-generated `externalId`; repeated calls
  with the same ID update instead of duplicating. Deletes reference `externalId`.
- Data points remember their **source app** (shown in the UI, filterable, and exportable).
- **Client SDK:** a small AAR (`diary-sdk`) with a typed Kotlin API, and a documented,
  versioned schema so apps can integrate without the SDK.
- Rate-limit and size-limit incoming calls; validate everything against the tracker type.
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
- Integration API: unapproved/revoked/spoofed callers are rejected (wrong signing cert),
  duplicate `externalId`s don't duplicate, calls while locked merge exactly once,
  invalid values are rejected.
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
 
```bash
./gradlew assembleDebug          # build
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator)
./gradlew ktlintCheck detekt     # lint
```
 
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
 