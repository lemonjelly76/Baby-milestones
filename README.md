# Baby Milestones

An Android app that takes a baby's date of birth (and due date), works out where they are developmentally, and
tracks the milestones expected from birth to 5 years. It records when each milestone is reached, explains what's
typical now and what comes next, and serves the same data and logic over MCP so an AI assistant, in the app or in
Claude, can check what needs updating and answer questions.

Built from the "Baby Milestones App — Spec" (Claude Docs, 20 October 2026). How it implements the spec, and where it
departs from it, is in [`docs/design-document.md`](docs/design-document.md).

## Features

- **Ages:** chronological and corrected age (born 3+ weeks early), with milestones on corrected age until 2 and
  appointments and vaccinations always on chronological age. Shown as weeks, then months and weeks, then years.
- **Today:** the current period, things around now (tick in one tap), gentle "Seen this yet?" prompts, what to
  watch for, the next appointment and one idea to try.
- **Milestones:** 160 milestones from the CDC/AAP 2022 checklists (2 months to 5 years) in four areas, by age or by
  area, filtered by status. Each has what it looks like, things to try, a measured age range where one exists
  (WHO), safety notes, what to do if it isn't seen, and its sources. Log with a date, note and photo; mark
  "not sure" (asked again in 2 weeks) or "no longer doing this".
- **Period guide:** 12 periods with what's typical, play, sleep, feeding, safety for new abilities, appointments
  and what happens next; swipe between them.
- **Timeline:** milestones, custom moments with photos, first words, teeth, appointments and vaccinations, and a
  memory book PDF with the photos.
- **Health:** growth charts (weight, length and head on the WHO standard behind the UK-WHO red book charts, corrected
  for babies born before 37 weeks), teeth, the newborn checks, the UK schedule of reviews and vaccinations for the
  baby's date of birth (including the July 2025 and January 2026 changes), questions for the health visitor and the
  family's contacts.
- **Health visitor summary:** achievements, anything not seen yet and the parents' questions, shared as a PDF or text.
- **Concerns:** calm, content-driven signposting to the health visitor or GP; never a diagnosis.
- **Guides:** 18 cited guides for the child's age, from readiness for solids, safer sleep and when to get urgent help to
  potty training, childcare and starting school (with England's reception dates). Each has key points, a checklist,
  and links to trusted videos, tools and support from the NHS, GOV.UK and UK charities.
- **Foods tracker:** 109 foods with how to serve them at each age; log each taste in two taps with how it went and any
  possible reaction; see allergens introduced or due again, foods worth offering again, and what to try next; make a
  day-by-day food plan (in the app, or with Claude) that ticks itself off as you log each food.
- **Claude:** use Claude mobile or claude.ai with the MCP connector; what Claude saves (check-ins, answers, meal ideas)
  appears in the app. An in-app assistant (charged per message through the Claude API) is off unless switched on in
  Settings; urgent symptoms and distress are caught on the phone before anything is sent.
- **Words and play:** a list of the words your child says, and four play ideas a week from what they're working on.
- **Family:** several carers per family (invite code or link, with edit or view-only access), several babies, offline reading, weekly digest and
  other reminders, export and delete everything.
- **MCP:** 35 tools, resources and 5 prompts at `/mcp` for Claude Desktop, claude.ai and the Claude app.

## How it's built

| Path | What |
| --- | --- |
| `core/` | Pure Kotlin shared by app and server: the API models, age engine, status rules, concerns, schedule, pending updates, what's next, search, safety check, timeline, summary. Heavily unit-tested |
| `core/src/main/resources/content/` | The content bundle: milestones, periods, guides, foods, growth tables, teeth, references, concern and safety copy, UK schedule |
| `server/` | Spring Boot 4.1 (Kotlin), PostgreSQL + Flyway, REST API for the app, Spring AI MCP server, Claude API assistant |
| `app/` | Android, Kotlin, Jetpack Compose, Material 3, Ktor client, WorkManager reminders |
| `docs/design-document.md` | Decisions (ADRs), sources, requirement coverage, open questions |

## Running the server

On the home server, next to the other apps:

```
cp .env.example .env    # set POSTGRES_PASSWORD, SETUP_CODE, MCP_PUBLIC_URL and, for the optional in-app assistant, ANTHROPIC_API_KEY
docker compose up -d --build
```

The server listens on port 8083. Publish it to the tailnet over HTTPS for the app, and publish only `/mcp` to the
internet with Tailscale Funnel so claude.ai can reach it, as for music-player and garmin-ai:

```
tailscale serve --bg --https=8445 http://127.0.0.1:8083
tailscale funnel --bg --set-path=/baby-mcp http://127.0.0.1:8083/mcp
```

The app then uses `https://<pc>.<tailnet>.ts.net:8445`, and the claude.ai connector
`https://<pc>.<tailnet>.ts.net/baby-mcp` with a token from Settings → **Create connector token**.

## Building and testing

Gradle 8.14 doesn't run on Java 25, so use JDK 21:

```
export JAVA_HOME=~/.sdkman/candidates/java/21.0.12+1.1-tem
./gradlew :core:test :server:test :app:testDebugUnitTest   # server tests need Docker (Testcontainers)
./gradlew :app:assembleRelease
```

Install `app/build/outputs/apk/release/app-release.apk` on each phone (private APK, signed with the debug key).
Debug builds may use a plain-HTTP server address; release builds need HTTPS.

## First use

1. Open the app, enter the server address, choose **New family**, and give the setup code from `.env`.
2. Add your baby: name and date of birth (or just their age), and whether they were born early.
3. Tick what they already do, then land on Today.
4. Settings → **Invite a carer** shares a one-time code and link; **Create connector token** gives Claude a bearer
   token for the connector address shown there. Add it on claude.ai (Settings → Connectors → Add custom connector) with
   the header `Authorization: Bearer <token>`; it then works in the Claude app too.
