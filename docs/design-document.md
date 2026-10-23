# Baby Milestones — Design Document

Last updated: 22 October 2026

Status key: Accepted · Proposed · Deferred · Rejected

Source: the "Baby Milestones App — Spec" (Claude Docs, 20 October 2026). This document records how the first build
implements it and where it departs from it.

## 1. Purpose

Parents and carers see, for each child from birth to 5, what's typical now, what's coming next and what hasn't been
logged yet; they log milestones in two taps, get calm signposting when something may be worth a chat, and take a
summary to health visitor reviews. An AI assistant answers from the same data, never from its own knowledge.

## 2. Engineering principles

- SOLID and clean code; test-driven development
- One rulebook: ages, statuses, concerns and schedules live in `core` and nowhere else
- Content is versioned, cited data; the model never writes content
- Safety rules enforced in code, not only in prompts

## 3. Constraints

- Development notes about a child are health data (special category under UK GDPR): store little, keep it in the
  family, keep it in the UK.
- Family use: a private APK and the existing always-on home server, like music-player and garmin-ai.
- claude.ai reaches custom connectors from Anthropic's cloud, so `/mcp` must be on public HTTPS to use it there.

## 4. Decision log

### ADR-001 Engineering approach — Accepted

SOLID, clean code, TDD, minimal comments (section 2).

### ADR-002 Technology stack — Accepted

- **Shared library (`core`):** Kotlin/JVM 17 bytecode with kotlinx.serialization, used by both app and server.
- **Server:** Kotlin, Spring Boot 4.1, Spring AI 2.0 MCP server (Streamable HTTP), PostgreSQL 18 with Flyway and
  `JdbcClient`, Anthropic Java SDK. One Gradle build with the app, Docker Compose to run.
- **App:** Kotlin, Jetpack Compose, Material 3, Ktor client, WorkManager; single module, no DI framework.

**Why:** answers the spec's first open question. The spec asked for one Kotlin library shared by app and server;
a Kotlin Spring Boot server in the same Gradle build gets that for free, keeps the work stack (Spring Boot, MCP Java
SDK) and matches music-player and garmin-ai, which already run this way on the home server. Firebase would have
needed the age engine reimplemented for Cloud Functions or a separate Cloud Run service.

### ADR-003 Content is cited, versioned data — Accepted

The bundle is JSON under `core/src/main/resources/content/` (manifest, references, milestones, periods, concerns,
safety, schedules). It ships inside the APK and the server; the app downloads a newer bundle from `/api/content`
when the server has one. `ContentValidator` rejects duplicate or mis-prefixed ids, broken `leads_to`/`builds_on`
links, links that lead to an earlier milestone, uncited entries, unknown references and period gaps.

### ADR-004 Evidence base — Accepted

At the user's request, content follows published evidence and cites it (29 references, each shown in the app and
returned by the tools):

- **Milestones:** the CDC/AAP *Learn the Signs. Act Early.* checklists (2022), all 12 ages, wording kept close to
  the original in UK English. They come from an evidence review (Zubler et al., *Pediatrics* 2022) that chose
  milestones most children (≥75%) reach by each age from normative data, so a missing one is a prompt to watch and
  talk to a professional, not a sign of being "behind average". The app says this wherever it shows an age.
- **`emerging_from`:** the previous checklist age (the milestone usually appears between the two visits), unless a
  measured window exists.
- **Measured ranges:** the WHO Motor Development Study (Martorell et al. 2006) 1st–99th percentile windows for
  sitting alone (3.8–9.2 months), standing with help, walking with help and walking alone (8.2–17.6 months), shown
  as reassurance. Crawling is included from the same study but never raises a concern: 4.3% of healthy children never
  crawl, which is also why the 2022 checklists dropped it.
- **Activities:** chosen for evidence behind them: conversational turns (Romeo et al. 2018), shared reading (AAP
  2024 policy), tummy time (Hewitt et al. 2020 systematic review; WHO 2019), active play and screen limits (WHO 2019).
- **Period guides:** sleep ranges and activity from WHO 2019; safer sleep from the Lullaby Trust and AAP 2022;
  feeding and weaning from SACN 2018 and the NHS, with early allergen introduction from the LEAP trial (Du Toit et
  al. 2015); home safety from the NHS.
- **UK schedule:** NHS vaccination schedule and the UKHSA notice of the July 2025 and January 2026 changes;
  health visitor contacts from England's Healthy Child Programme.
- **Weaning and foods:** NHS Best Start in Life weaning pages (readiness, what to feed, preparing food safely,
  choking and gagging), NHS foods to avoid, food allergies, drinks and cups, iron and vitamins; BSACI early allergen
  guidance with the LEAP and EAT trials; the BLISS trial (Cameron 2015; Fangupo 2016 on choking); repeated exposure
  (Maier 2007) and vegetables first (Barends 2013). Toddler and pre-school guides cite NHS, GOV.UK and Best Start in
  Life pages.
- **Concern thresholds:** the spec's, signposting only. Losing a skill always prompts a check (in line with CDC
  "act early" guidance and NICE CG128, which is not shown in the app because it names a condition).

Content still needs review by a health professional before release (spec open question 5).

### ADR-005 Age engine — Accepted

- Corrected age = time since the due date, applied when born 21+ days early (the spec's 3 weeks; spec open question 6
  stays open, the threshold is one constant). Milestones use it until chronological age 24 months, then chronological.
- Milestone dates are `base.plusMonths(n)`, and whole months are counted the same way, so month ends and leap days
  agree (born 31 January → 1 month on 28 February).
- Before the due date, corrected age reads "3 weeks before due date".

### ADR-006 Status rules — Accepted

Only log entries are stored. Status, in order: lost skill, achieved, not sure, not seen yet (past `expected_by`),
due now (within 28 days of it), coming up (past `emerging_from`), later. "Due now" wins over "later" when a
milestone's window is shorter than four weeks. Not sure is re-asked after 14 days. One current log entry per
milestone; every change is copied to `milestone_log_history` first.

### ADR-007 Concerns: unlogged is not "not seen" — Accepted

**Departure from the spec:** the spec's thresholds would flag every milestone a family hadn't logged before they
started using the app, so a 6-month-old added today would get a screen of "Worth a chat" cards. Milestones whose
checklist date passed before the baby was added (`tracking_since`) appear as catch-up prompts instead, and only
count towards concerns once a parent answers "not sure". Found while testing on the emulator.

### ADR-008 Access: a token per carer, scoped to the family — Accepted

- Creating a family needs `SETUP_CODE` (or, without one, only the first family can be created).
- Carers join with a 10-character, single-use invite code valid for 7 days (also as a `babymilestones://join` link).
- Every carer gets a random bearer token; only its SHA-256 is stored. Every query is scoped by family id; tests
  check that one family can't read or write another's baby over REST, MCP or the assistant's confirm endpoint.
- `/mcp` takes the same tokens; Settings creates a separate "claude" token for Claude Desktop or claude.ai so it can
  be replaced without signing a phone out.

**Departure from the spec:** the spec suggested OAuth scoped to a family. Bearer tokens carry the same scope without
an authorisation server, as in music-player ADR-011.

### ADR-009 The app computes, the server stores — Accepted

The app fetches a baby's raw records (`/api/babies/{id}/data`) and computes every view with `core`, so Today, the
period guide and notifications work offline from the last copy. Writes go to the server and refresh that baby.

**Departure from the spec:** the cache is JSON files rather than Room; writes need a connection (no offline queue
yet).

### ADR-010 The assistant — Accepted

- **Where the loop runs:** on the server (`POST /api/assistant/messages`), so the API key never reaches the APK.
  The loop calls the same tool catalog in-process that `/mcp` serves.
- **Model:** `claude-opus-5-5` at low effort (short, phone-sized answers), the stable system prompt and tool list
  cached, server-side refusal fallback enabled. `ANTHROPIC_MODEL` overrides it.
- **Writes:** write tools never run from the model. Each becomes a proposal with a plain-words summary; the app shows
  them together on one confirmation sheet and `POST /api/assistant/confirm` runs the ones the parent ticks. Ids are
  validated when proposed, so the model hears about a bad id before the parent sees it.
- **Safety:** the fixed phrase check (`core`'s `SafetyCheck`) runs on the phone before anything is sent and again on
  the server. Urgent symptoms and distress show app-rendered cards and never reach the model; medicine questions get
  the pharmacist card alongside the answer.
- **Privacy:** the conversation lives on the phone; only its text turns are sent, never photos. The server keeps no
  assistant history.

### ADR-011 MCP surface — Accepted

All 11 read tools and 8 write tools from the spec, plus 8 for foods, guides and saved notes (ADR-014, ADR-016) 6 for growth, words, teeth and contacts (ADR-018) and 2 for the food plan (ADR-021), defined
once (`ToolCatalog`) and served both over MCP and to the assistant. Resources `content://version`, `baby://{id}/summary`, `content://milestones/{id}`, `content://periods/{id}`;
prompts `weekly_checkin`, `prepare_for_review`, `explain_milestone`, `meal_ideas`, `food_plan`. Every result carries `content_version`; content
results carry their sources.

### ADR-012 UK schedule — Accepted

One schedule, versioned by date of birth: the 12/16-week swap of MenB and PCV (born from 7 April 2025), MMRV at
1 year (born from 2025), the 18-month appointment with the 4th 6-in-1 and an MMRV dose (born from 1 July 2024), the
pre-school MMRV for children born September 2022 to June 2024, and yearly flu from 2. Health visitor contacts follow
England's Healthy Child Programme; the other nations use the same vaccinations and fall back to these reviews with a
note (spec open question 4). The transitional date boundaries need checking against the current NHS schedule before
release (spec section "Age engine and developmental periods").

### ADR-014 Guides tab and foods tracker — Accepted

The user asked for more than milestones as the child grows, starting with a list of foods tried during baby-led
weaning. Of three options (a weaning add-on; a Guides tab; a full feeding app like Solid Starts with 400 foods, meal logs
and recipes) the user chose the Guides tab, built so the full feeding app can follow without a rewrite.

- **Guides tab:** cited guides shown by chronological age ("for now", "coming up", all): ready for solids, baby-led
  weaning (BLISS), gagging or choking, foods to avoid, allergens, drinks and cups, vitamins, teeth, sun safety, potty
  training, childcare, starting school. Guides can carry an urgent card (the NHS choking steps, signs of a serious
  reaction), a checklist ticked per baby (`checklist_tick`), and a feature the app works out: the foods tracker, or
  England's reception dates (`SchoolStart`: the September after the 4th birthday, apply by 15 January, offers 16 April).
- **Foods tracker:** a catalogue of 109 foods in `foods.json` (group, allergens, iron-rich, earliest age, how to serve
  by age, choking-risk preparation, sources) and one `food_tasting` row each time a food is offered, with how it went
  and any possible reaction in the parent's words. The family's own foods are kept by name.
- **Rules in `core` (`FoodTracker`):** keep offering a food not yet liked until 10 tries (NHS; Maier 2007); allergens
  one at a time, no new one within 3 days of the last, and due again after 7 days without (NHS: keep them in the usual
  diet; BSACI; EAT); a noted reaction stops nudges for that allergen and is listed for the health visitor; suggestions
  put a due allergen first, then one new allergen, foods to offer again, untried vegetables (less sweet first: NHS;
  Barends 2013) and an iron-rich food (BLISS). Nothing is suggested before 6 months or that the child is too young for.
- **No "100 foods before 1":** no study supports the number, so the tracker shows variety by group and repetition.
- **Ask moved:** the bottom bar keeps five tabs (Today, Milestones, Guides, Timeline, Health); Claude is a button on
  every screen (ADR-016).
- **Towards the full feeding app:** more foods are content only; a meal log groups tastings; an allergen plan builds on
  the allergen statuses; recipes would be a new content file. The cost is writing and reviewing content, not code.

### ADR-015 Allergen wording names conditions — Accepted

The no-condition-names rule (ADR-010) has one narrow exception: the allergens guide repeats the NHS's own list of who
should talk to a GP or health visitor before introducing allergens (a diagnosed food allergy or eczema, or a family
history of food allergies, eczema, asthma or hay fever). It's eligibility for advice, not a diagnosis; leaving it out
would make the guide less safe. Reactions are recorded only in the parent's words, and the app never names one.

### ADR-016 Claude through MCP first; the in-app assistant is opt-in — Accepted

The user wants to avoid paying per API call and uses Claude mobile with the MCP connector for almost everything.

- **In-app assistant off by default:** a per-phone setting ("In-app assistant", Settings) turns on the Ask here chat.
  Off, the app makes no Claude API calls. The server still needs `ANTHROPIC_API_KEY` for it to work at all.
- **Outputs saved on the server:** `save_note` keeps what Claude writes (check-in summaries, answers, review
  preparation, plans and meal ideas) in `assistant_note`; `list_notes` reads them back. The app shows them under the
  Claude button and the newest on Today for a week, rendering headings, bullets and bold. The prompts and the server
  instructions ask Claude to offer to save its results.
- **Check-in from the app:** Today's check-in card copies a prompt and opens the Claude app (or claude.ai).
- **Connector address:** `MCP_PUBLIC_URL` (for example the Tailscale Funnel path) is shown in Settings next to the
  connector token.

### ADR-017 Growth charts on the WHO standard behind the UK-WHO charts — Accepted

The main gap against the red book and other baby apps (spec phase 3, brought forward at the user's request).

- **Data:** the WHO Child Growth Standards (2006) weight-for-age, length/height-for-age and head-circumference-for-age
  expanded tables (L, M, S by day), sampled weekly into `growth.json` (35 KB) and interpolated in between. Used under
  CC BY-NC-SA 3.0 IGO (non-commercial family use). The RCPCH growth library is AGPL, so it isn't copied.
- **Maths in `core` (`GrowthEngine`):** z = ((value / M)^L − 1) / (L·S), with the WHO restricted method for weight beyond
  ±3 SD; centiles from the normal distribution; the UK-WHO chart's nine lines two thirds of an SD apart.
- **Gestation:** born before 37 weeks, measurements are plotted from the due date until 1 year (32 to 36 weeks) or 2
  years (under 32 weeks), as RCPCH advises; nothing is plotted before the due date. This differs from the milestone
  correction (ADR-005) on purpose: each follows its own chart's rule.
- **Departure from UK-WHO:** UK-WHO uses UK90 data at birth and from 4 years; this build uses the WHO standard
  throughout 0 to 5. The differences are small, and the chart says it shows the WHO standard.
- **Signposting:** NICE NG75's thresholds only (a fall across 1, 2 or 3 centile spaces depending on the first weight's
  centile, or a weight below the 2nd centile), with calm copy that mentions how home weighing varies. No condition is
  named. Centiles are explained as "how many in 100 children weigh less", never as good or bad.

### ADR-018 First words, teeth, contacts and newborn checks — Accepted

- **Words** (`first_word`, one row per word per child) and **teeth** (`tooth`, 20 milk teeth in FDI notation with the
  NHS's usual ages) appear on the timeline (first word and first tooth called out) and in the health visitor summary as
  counts, never a score.
- **Contacts** belong to the family (health visitor, GP surgery, midwife, nursery, dentist), with tap-to-call.
- **Newborn checks:** the physical examination (within 72 hours), blood spot test (day 5) and hearing test (before 4
  weeks) join the England schedule, so the Health tab matches the red book from birth.
- **MCP:** `get_growth`, `log_growth`, `get_firsts`, `log_word`, `log_tooth`, `get_contacts`; the weekly check-in prompt
  asks about words, teeth and weigh-ins.

### ADR-019 View-only carers — Accepted

Invites carry an access level: edit (as before) or view, for grandparents and others who should see but not change.
The bearer-token filter refuses any non-GET request from a view-only carer except on `/mcp`, where every call is a POST
and the tool layer refuses writes instead. The app shows a "View only" banner and hides the main edit controls.

### ADR-020 Memory book and weekly play ideas — Accepted

- **Memory book:** `MemoryBook` in `core` groups the timeline's milestones, moments, words and teeth into chapters by
  month of age; the app renders it as a PDF with the moments' photos (fetched with the carer's token, scaled to fit)
  and shares it.
- **Play ideas:** `WeeklyPlay` picks four activities a week from milestones the child is working on, then the period
  guide, rotating each Monday; Today and `get_whats_next` show the same ones.

### ADR-021 Food plan — Accepted

The user asked for a schedule of foods to try that works as a checklist, generated by Claude on the phone if need be.

- **Model:** `food_plan_item` rows (a catalogue food or the family's own, a date, a kind and a reason, made by the app or
  Claude). An item is done once that food is tasted on or after the day the plan was made, so logging a food is the
  only way to tick it, and nothing else needs updating.
- **Rules in `core` (`FoodPlanner`):** one new thing a day; a new allergen at most every 3 days and on a day with no
  other allergen, so a reaction can be put down to it; introduced allergens kept going weekly; untried vegetables (less
  sweet first), then iron-rich foods, then the rest; foods not liked yet offered again every other day until 10 tries;
  nothing with an allergen that caused a possible reaction, or that the baby is too young for.
- **Two ways to make one, both free of API charges:** "Make a 2-week plan" in the app uses the rules; "Ask Claude"
  copies a prompt and opens the Claude app, where the `food_plan` prompt adapts the rules' suggestion to the family's
  meals and saves it with `save_food_plan`. `get_food_plan` returns the plan with progress and the rules' suggestion.
- **UI:** the plan sits at the top of the foods tracker's "Try next" tab as a checklist by day (ticking logs the food);
  Today lists the foods planned for today with a tick to log each.
- **Day details (`FoodPlanner.days`):** each food shows how to serve it at that age and when it was had before; a new
  allergen's day gets a note explaining why the rest of the day is safe to pair with it, or a warning when the day also
  has something else new or another allergen (useful for plans Claude writes). `get_food_plan` returns the same.

### ADR-022 Structure for change — Accepted

A review for maintainability found code that had grown as features were added. Changes, with no change in behaviour:

- **One set of API models** in `core.api` (42 types) used by app and server; before, each new field was added twice.
- **Tools by area:** `ToolCatalog` (640 lines) became four `ToolSet` beans (milestones, foods, guides, records). Each
  write tool carries its own confirm-chip text instead of a central switch in `AssistantService`.
- **Controllers by area:** `ApiController` became seven controllers; `BabyService.data` builds `BabyData`, so
  controllers no longer reach into stores.
- **Stores by area:** food entries and the plan in `FoodStore`, checklist ticks and Claude's notes in `GuideStore`, with
  shared row helpers in `StoreSupport`.

### ADR-023 UX review — Accepted

- **Today** opens with this week (ADR-026), then daily actions: what's around now, catch-up questions and food (with
  today's plan), then what to watch for, appointments and Claude.
- **Health** shows what's due, booked or next; later and earlier checks and vaccinations are folded, with a count of
  past checks still to mark.
- **Foods:** opens on "Try next" (plan, then ideas); any past entry opens an editor (date, how it went, reaction, note,
  remove).
- **Pull to refresh** on every tab, so a partner's changes show straight away.
- **Photos** are decoded scaled to 1280 px and cached by memory size, instead of full size, which risked running out of
  memory with camera photos.
- View-only carers don't see edit controls on the foods tracker, growth, teeth, words or timeline.

### ADR-024 How guides are presented — Accepted

Asked for more links and videos, more useful information and best-practice presentation. The layout follows the NHS
standard for creating health content and what NHS Best Start in Life, Solid Starts and Kinedu do: a short summary first,
detail after, trusted onward links, visible sources and a review date.

- **Order on a guide:** emergency steps (when there are any), then "In short" (two to four key points), then the
  detail, the checklist, "Watch and read", related guides, sources (folded) and "Checked against these sources on …".
- **Watch and read:** links only to the NHS, GOV.UK, NHS boards and established charities (British Red Cross, St John
  Ambulance, Lullaby Trust, CAPT, ERIC, BookTrust, CBeebies Parenting, First Steps Nutrition Trust, Anaphylaxis UK,
  Allergy UK, Unicef UK Baby Friendly), each labelled video, read, tool or support. Every link was checked on 22 October
  2026 and must be https (`ContentValidator`). No commercial brands. RoSPA's car seat site was left out because its
  certificate had expired.
- **New guides:** safer sleep (Lullaby Trust, NHS) and when to get urgent help (NHS), both signposting only.
- **Guides tab:** search across all guides; guides for now grouped by topic in compact rows rather than large cards,
  since around 15 apply at 6 months.
- **Claude:** `get_guides` returns the key points, links and related guides, so Claude can pass them on.

### ADR-025 Food photos — Accepted

105 of the 109 foods have a photo from Wikimedia Commons, chosen by eye from each food's Wikipedia lead image or a
targeted search, and kept only under CC0, public domain, CC BY or CC BY-SA (`ContentValidator` rejects anything else).
Each is centre-cropped to 480×360 JPEG in the app's assets (about 4 MB in all) so the tracker works offline. The credit
(author, licence, "via Wikimedia Commons", cropped) shows under the photo on each food's card and links to its source
page; all credits are listed under Settings, and kept in `docs/food-photo-credits.json`. Photos were checked against
the serving advice, so the egg photo shows whole eggs rather than a runny yolk. No photo yet: baby cereal, leek,
parsnip, soya yoghurt (no clear, openly licensed picture found).

Foods without a photo, and the family's own foods, get a plain tile the same size, so lists stay aligned. With a photo
on every plan row, only missed foods, today and tomorrow show the full detail (why, had before, how to serve); later
days are one line each, and only the next five days show until asked.

### ADR-026 Week by week — Accepted

Parents think in weeks for the first year, and much of what matters then is tied to a week: the heel prick, the cord,
regaining birth weight, registering the birth, the crying peak, the 8, 12 and 16 week vaccinations.

- **One page per week, birth to 5 (`core.weeks.WeekByWeek`):** week 0 is the first seven days. Each page gathers what
  falls in that week from data the app already has: checks and vaccinations whose window or due date is in it (earlier
  doses not marked as given are counted on this week, not listed), milestones whose due-now window overlaps it with
  today's status (or the next checklist when none do), guides whose starting age falls in it, the stage, play ideas
  worked out for that week and the food plan's days. App (`WeekScreen`, a pager with arrows and "Back to this week")
  and MCP (`get_week`) use the same engine.
- **Week notes are content** (`weeks.json`, 16 notes, each citing NHS, GOV.UK, Lullaby Trust or ICON pages checked on
  23 October 2026). Notes count weeks from birth, or from the due date while milestones are corrected (crying peaks
  about 6 to 8 weeks after the due date for a baby born early); they can be limited to UK nations (registering a birth)
  or calendar months (strong sun, March to October). Weekly development content is deliberately not invented: child
  development is measured in months, so between notes a page leans on the milestone checklists and the stage.
- **Today** opens with a "This week" card listing what's particular to the week; play ideas moved to the week page. The
  Sunday digest names the week's newest notes.
- **View-only carers** get the same pages with every log, tick and edit control hidden: `BabyView.canEdit` carries
  the carer's access to every screen, including Today, Milestones and the milestone card, which used to show them.
- **Urgent help corrected:** checking sources for the notes found the NHS now lists 38°C or more under 3 months (39°C
  at 3 to 6 months), very fast or difficult breathing, clammy skin and a weak, high-pitched or continuous cry under
  "call 999 or go to A&E". The urgent-help guide and the app's emergency card said NHS 111 for the temperatures; both
  now match the NHS page.

### ADR-013 Out of this build — Deferred

Feed/nappy/sleep logging, a medicine and temperature log (needs a decision: it would be a record only, ADR-010), a meal log, recipes and a step-by-step allergen plan (ADR-014), iOS, Room and an offline write queue, OAuth, Scotland/Wales/NI
review schedules.

## 5. Requirements coverage

| Spec item | Status |
| --- | --- |
| Onboarding: name, DOB or age, due date, sex; age read back; backfill checklist | Built |
| Age engine + tests (premature, month ends, leap years) | Built (`core`, 53 tests) |
| Periods, milestone content 0–60 months with sources | Built (12 periods, 160 milestones) |
| Status rules, not sure re-check, edits with history, delete | Built |
| Custom moments, appointments, vaccinations by DOB and region | Built |
| Today, Period guide, Milestones, Timeline, Health, Ask screens | Built (Ask behind the Claude button, ADR-016) |
| Health visitor summary export (PDF and share sheet) | Built |
| Multiple babies, several carers, invite by link | Built |
| Concerns and safety rules, disclaimer footer | Built (ADR-007 departure) |
| Notifications: weekly digest, due now, appointment window, 3-week nudge | Built (daily WorkManager job) |
| MCP read and write tools, resources, prompts | Built |
| Assistant flows: weekly check-in, free-text logging, questions, review prep | Built; needs `ANTHROPIC_API_KEY` |
| Privacy: UK hosting, family scoping, no backups, export and delete | Built |
| Guides by age with checklists (user request, ADR-014) | Built |
| Foods tracker for baby-led weaning (user request, ADR-014) | Built (109 foods, allergen tracker, suggestions) |
| Claude's outputs saved on the server; in-app assistant opt-in (user request, ADR-016) | Built |
| Growth charts | Built (ADR-017) |
| First words, teeth, contacts, newborn checks (comparison with other apps) | Built (ADR-018) |
| View-only carers, memory book, weekly play ideas | Built (ADR-019, ADR-020) |
| Food plan as a checklist, made by the app or Claude (user request) | Built (ADR-021) |
| Guides: childproofing, car seats, toddler meals, talking and books, safer sleep, urgent help | Built (18 guides, ADR-024) |

## 6. Open questions

- Who reviews milestone, concern and safety copy before release? (spec)
- Corrected-age threshold: 3+ weeks early, or any birth before 37 weeks? (spec)
- England only, or each nation's review schedule? (spec)
- Should past appointment windows for a baby added late be marked in one go?
