-- Every record hangs off a family; deleting a family or a baby removes everything beneath it.

create table family (
    id         text primary key,
    name       text        not null,
    region     text        not null default 'england',
    created_at timestamptz not null default now()
);

create table carer (
    id         text primary key,
    family_id  text        not null references family (id) on delete cascade,
    name       text        not null,
    email      text,
    role       text        not null default 'parent',
    created_at timestamptz not null default now()
);

-- Only a SHA-256 hash of each bearer token is stored.
create table access_token (
    token_hash   text primary key,
    carer_id     text        not null references carer (id) on delete cascade,
    label        text        not null,
    created_at   timestamptz not null default now(),
    last_used_at timestamptz,
    expires_at   timestamptz
);

create table invite (
    code_hash  text primary key,
    family_id  text        not null references family (id) on delete cascade,
    created_by text        not null references carer (id) on delete cascade,
    expires_at timestamptz not null,
    used_at    timestamptz
);

create table photo (
    id           text primary key,
    family_id    text        not null references family (id) on delete cascade,
    content_type text        not null,
    bytes        bytea       not null,
    created_at   timestamptz not null default now()
);

create table baby (
    id            text primary key,
    family_id     text        not null references family (id) on delete cascade,
    name          text        not null,
    date_of_birth date        not null,
    due_date      date,
    sex           text,
    dob_estimated boolean     not null default false,
    photo_ref     text references photo (id) on delete set null,
    created_at    timestamptz not null default now()
);
create index baby_family on baby (family_id);

-- One current entry per milestone; every change is copied to milestone_log_history first.
create table milestone_log (
    id           text primary key,
    baby_id      text        not null references baby (id) on delete cascade,
    milestone_id text        not null,
    state        text        not null check (state in ('achieved', 'not_sure')),
    achieved_on  date,
    approximate  boolean     not null default false,
    marked_on    date        not null,
    note         text,
    photo_ref    text references photo (id) on delete set null,
    source       text        not null check (source in ('manual', 'backfill', 'assistant')),
    logged_by    text references carer (id) on delete set null,
    lost_on      date,
    updated_at   timestamptz not null default now(),
    unique (baby_id, milestone_id)
);

create table milestone_log_history (
    id         bigserial primary key,
    log_id     text        not null,
    baby_id    text        not null references baby (id) on delete cascade,
    action     text        not null check (action in ('created', 'updated', 'deleted')),
    snapshot   jsonb       not null,
    changed_by text,
    changed_at timestamptz not null default now()
);
create index milestone_log_history_log on milestone_log_history (log_id);

create table moment (
    id         text primary key,
    baby_id    text        not null references baby (id) on delete cascade,
    title      text        not null,
    date       date        not null,
    note       text,
    photo_ref  text references photo (id) on delete set null,
    created_by text references carer (id) on delete set null,
    created_at timestamptz not null default now()
);

create table appointment (
    id        text primary key,
    baby_id   text not null references baby (id) on delete cascade,
    type      text not null,
    booked_on date,
    done_on   date,
    note      text,
    unique (baby_id, type)
);

create table vaccination_dose (
    id               text primary key,
    baby_id          text not null references baby (id) on delete cascade,
    schedule_version text not null,
    dose_code        text not null,
    given_on         date not null,
    note             text,
    unique (baby_id, dose_code)
);

create table review_note (
    id         text primary key,
    baby_id    text        not null references baby (id) on delete cascade,
    text       text        not null,
    created_by text references carer (id) on delete set null,
    created_at timestamptz not null default now(),
    resolved   boolean     not null default false
);
