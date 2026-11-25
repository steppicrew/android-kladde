# 0002 — Unknown labels from other apps

- Status: accepted
- Date: 2026-11-25

## Context

ADX entries carry an optional `sub`, a label such as `Pils`. The spec lets a sender
send labels it never proposed, and the beer counter does: built-in drinks send a stable
English key (`beerSmall`), drinks the user made send their own name (`Bier`). Kladde
added each new label to the tracker's sub types and showed it as sent, so the entry
list read `1 count beerSmall`.

The same entries carry `intake.*` attrs, but the beer counter sends placeholder values
per glass, and a drink whose volume or strength the user never entered has none. Kladde
can't tell what such a label means: a 5 % beer, water, or just something to count.

Kladde-side guessing (turning `beerSmall` into "Beer Small") was ruled out: it is a
workaround for one sender and fails for every other language and every user-made name.

| Option | Pros | Cons | Risks | Score |
|---|---|---|---|---|
| **Ask the user about each new label: a notification that opens a screen for it, plus a counter in the app for whatever is still open** | Asked at the moment it matters; works for every sender; fixes names and missing values without a spec change | Needs the notification permission (Android 13+) and a new table | Too many notifications from a chatty sender; grouping keeps it to one group | **8/10** |
| Only a list of new labels inside Kladde, no notification | No permission | Easy to overlook; values stay unknown for long | Labels are never looked at | 6/10 |
| Spec change: senders send a display label next to the key | No Kladde UI | Fixes the name only, not the values; every sender has to implement it | Senders don't send it | 5/10 |

## Decision

- Kladde keeps a **label record** per (tracker, label key): status `OPEN`, `IGNORED` or
  `SPECIFIED`, an optional display name, and default values by ADX attr key. It also keeps
  the attr keys the stream announced at connect, so the screen knows which values to ask for.
- The **first entry** that uses a label Kladde has no record of creates an `OPEN` record
  and posts a notification for it. Labels that were only proposed at connect and never
  used ask nothing.
- **Notification**, one per label, grouped per Kladde: tapping it or its "Specify" action
  opens the label's screen; "Ignore" marks it `IGNORED` without opening anything. Swiping
  it away leaves it `OPEN`.
- **In the app**, a small icon with the number of `OPEN` labels shows on the main screen
  while there are any. It opens the same screen with every open label.
- **The screen** asks per label for a display name and what it is: count only, or
  values for the stream's announced attrs (for drinks: alcoholic with volume and % vol,
  or non-alcoholic with volume, stored as 0 % vol). Saving marks it `SPECIFIED`.
- **Precedence:** a value the sender sends always wins; a label's default fills in only
  where an entry has none. `IGNORED` and "count only" fill in nothing. So a drink that
  arrived before its values were known counts correctly once they are entered.
- **Display:** the entry list shows a label's display name when it has one, else the key.
- **Privacy:** the notification text is outside Kladde's storage. It is private on the
  lock screen (`VISIBILITY_PRIVATE`) with a public version that names no label. The
  channel can be turned off in Android's settings; without the notification permission
  only the in-app counter remains. The permission is asked for when the user allows a
  sender, the moment a notification first becomes possible.
- **Placement:** the record in `core/model` / `core/data` (table `sub_type_labels`,
  database version 2, an automatic migration that only adds the table); notification,
  actions and screen in `integration/adx`, next to the approval screen.

## Direction: the sender describes, Kladde asks only about gaps

Decided the same day, after trying the screen on the phone. Fixed Kladde types (beverage,
food, exercise, medication, sleep, each with its own properties and presets) were
considered and dropped: the sending app knows its domain better than any list in Kladde.

| Option | Pros | Cons | Risks | Score |
|---|---|---|---|---|
| **The sender describes its subs and attrs (labels, defaults, self-described app keys), the ADX registry fixes shared meanings, Kladde asks only about what is left open** | Most flexible; the beer counter can say "beerSmall = Small beer, 330 ml, 5 %" once; also fixes raw keys as names | Additive ADX spec change, both libraries | A sender that describes little → Kladde's questions catch it | **9/10** |
| Fixed types in Kladde with their own properties and presets | Kladde controls the questions | Always behind the senders; duplicates what they know | Mismatch with what senders send | 6/10 |
| Free keys per sender, no shared registry | Most freedom | Nothing comparable across apps | Two apps' alcohol can't be added up | 4/10 |

- Proposed to ADX (spec suggestions, 2026-11-25): `subs` entries as objects with `key`,
  `label` and default `attrs`; app-private attrs described with `label`, `type` and `unit`;
  registry keys `intake.sugar_g`, `exercise.type` (Health Connect exercise types),
  `exercise.distance_km`, `exercise.kcal`, `medication.dose_mg`, `sleep.quality`; an absent
  attr means unknown; a new attr or sub is announced with a new `CONNECT`. Open: a list of
  ingredients per food entry, and updating labels without a new approval.
- In Kladde, once ADX has it: a sender's sub label pre-fills the name and its defaults the
  values; a sub the sender described fully opens no question. **Precedence:** an entry's
  own value, then what the user entered in Kladde, then the sender's sub default. The user's
  name for a label beats the sender's label.
- The label screen keeps asking for the stream's announced attrs; it gains fields for
  self-described keys (by their `type`) once senders send them.

## Consequences

- No ADX spec change. A sender that later sends display labels would only pre-fill the
  display name.
- A label's values now live in two places, the sender and Kladde; the precedence rule
  above decides, and the user is told nothing when they differ.
- A setting for a fully generic notification text ("You have a new entry type") is still
  to come, together with the routine reminders' one.
- With sync, label records become synced objects like trackers; their key is already a
  random UUID with a unique (tracker, label) index.
