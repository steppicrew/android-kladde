# 0001 — App integration via ADX

- Status: accepted
- Date: 2026-11-25

## Context

Kladde must accept data from other apps on the device, starting with Steppi's Beer
Counter. The original plan was a Kladde-specific integration API: its own AIDL service,
a `<appId>.action.LOG_DATA_POINT` intent, `externalId`-based idempotency, consent per
calling app and a `diary-sdk` client library.

Meanwhile the owner wrote **ADX — App Data Exchange** (`../android-apx`, spec in
`docs/ADX-SPEC.md` there). It covers the same ground for all of the owner's apps (beer
counter, Kladde, a food app): push-only, on-device only, consent per sender → receiver
bound to package and signing certificate, idempotent by (sender, stream, id) + `rev`,
a persistent sender outbox, and a receiver library that does caller identity, consent,
version, limits, validation and the rev rule.

## Decision

- Kladde is an **ADX receiver**. It depends on `adx-receiver` and implements `AdxStore`
  and the CONNECT approval activity in a new module `integration/adx`.
- The planned `api/` and `sdk/` modules are dropped. Senders use `adx-sender`.
- During development ADX is included as a Gradle **composite build**
  (`includeBuild("../android-apx")`); publishing is an open question on the ADX side.
- Kladde's toolchain follows ADX (at the time of writing AGP 8.13, Gradle 8.13, Kotlin 2.2.20, JDK 21;
  since then AGP 9.5 and Gradle 9.6),
  compile/target SDK 37, core library desugaring enabled (required by ADX).

### Locked vault

The spec says a locked Kladde accepts submits and encrypts them on arrival. That needs
`currentRev()` while the encrypted database is unreadable.

| Option | Pros | Cons | Risks | Score |
|---|---|---|---|---|
| **A: Rev check deferred while locked.** `currentRev()` returns `null`, ops become sealed-box pending records, the rev rule runs again during the merge on unlock | Keeps "works while locked"; no ADX change; reuses the pending-completion mechanism | Locked Kladde says `ACCEPTED` where it would say `DUPLICATE`/`STALE` (harmless) | Merge must be idempotent | **9/10** |
| B: `AdxBusyException` while locked | Trivial, no crypto needed | Data waits in the sender's outbox until unlock; contradicts the spec sentence | Backoff up to 6 h delays entries after unlock | 6/10 |
| C: Plaintext rev index outside the vault | Exact statuses while locked | Leaks stream names and activity timing | Breaks the E2EE promise | 3/10 |

**Now: option B**, because `core/crypto` doesn't exist yet. **Later: option A**, as soon
as the vault key pair and sealed-box pending records exist. Until then the ADX spec's
"a locked Kladde encrypts on arrival and merges on unlock" is not true for Kladde.

## Consequences

- ADX's consent store is plain SQLite in Kladde's app data: sender package names,
  labels and the stream → tracker mapping are metadata outside the vault. It has to be
  readable while locked, so this is accepted and documented.
- ADX has no licence yet; it needs one compatible with Kladde's before an F-Droid build.
- Changes to the wire format happen in ADX (spec first), never in Kladde.
