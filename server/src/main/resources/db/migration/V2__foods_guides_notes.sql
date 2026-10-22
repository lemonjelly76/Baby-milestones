-- Each time a baby was offered a food. food_id is a catalogue id, or null for the family's own food.
create table food_tasting (
    id         text primary key,
    baby_id    text        not null references baby (id) on delete cascade,
    food_id    text,
    name       text        not null,
    food_group text        not null,
    tasted_on  date        not null,
    liking     text check (liking in ('liked', 'not_sure', 'disliked')),
    reaction   text,
    note       text,
    photo_ref  text references photo (id) on delete set null,
    logged_by  text references carer (id) on delete set null,
    created_at timestamptz not null default now()
);
create index food_tasting_baby on food_tasting (baby_id, tasted_on);

-- Ticked guide checklist items; unticking deletes the row.
create table checklist_tick (
    baby_id   text not null references baby (id) on delete cascade,
    item_id   text not null,
    ticked_on date not null,
    ticked_by text references carer (id) on delete set null,
    primary key (baby_id, item_id)
);

-- What Claude wrote and the family chose to keep (check-in summaries, answers, plans), saved over MCP.
create table assistant_note (
    id         text primary key,
    baby_id    text        not null references baby (id) on delete cascade,
    kind       text        not null check (kind in ('check_in', 'review_prep', 'answer', 'plan', 'other')),
    title      text        not null,
    body       text        not null,
    created_by text references carer (id) on delete set null,
    created_at timestamptz not null default now()
);
create index assistant_note_baby on assistant_note (baby_id, created_at);
