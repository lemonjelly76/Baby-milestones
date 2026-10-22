-- A carer can edit, or only view (for example grandparents); invites say which.
alter table carer add column access text not null default 'edit' check (access in ('edit', 'view'));
alter table invite add column access text not null default 'edit' check (access in ('edit', 'view'));

create table growth_measurement (
    id          text primary key,
    baby_id     text        not null references baby (id) on delete cascade,
    measured_on date        not null,
    weight_kg   double precision,
    length_cm   double precision,
    head_cm     double precision,
    measured_by text,
    note        text,
    created_by  text references carer (id) on delete set null,
    created_at  timestamptz not null default now(),
    check (weight_kg is not null or length_cm is not null or head_cm is not null)
);
create index growth_measurement_baby on growth_measurement (baby_id, measured_on);

create table first_word (
    id         text primary key,
    baby_id    text        not null references baby (id) on delete cascade,
    word       text        not null,
    said_on    date        not null,
    note       text,
    created_by text references carer (id) on delete set null,
    created_at timestamptz not null default now()
);
create unique index first_word_once on first_word (baby_id, lower(word));

create table tooth (
    baby_id  text not null references baby (id) on delete cascade,
    tooth_id text not null,
    came_on  date not null,
    primary key (baby_id, tooth_id)
);

create table contact (
    id         text primary key,
    family_id  text        not null references family (id) on delete cascade,
    role       text        not null check (role in ('health_visitor', 'gp', 'midwife', 'nursery', 'dentist', 'other')),
    name       text        not null,
    phone      text,
    email      text,
    note       text,
    created_at timestamptz not null default now()
);
