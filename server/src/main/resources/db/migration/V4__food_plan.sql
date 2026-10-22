-- Foods planned for coming days, made by the app's rules or by Claude. An item is done once the food is tasted on or
-- after created_on, so nothing here needs updating when a food is logged.
create table food_plan_item (
    id         text primary key,
    baby_id    text        not null references baby (id) on delete cascade,
    food_id    text,
    name       text        not null,
    food_group text        not null,
    planned_on date        not null,
    kind       text        not null check (kind in ('new_food', 'new_allergen', 'keep_going', 'offer_again', 'other')),
    reason     text,
    source     text        not null check (source in ('app', 'claude')),
    created_on date        not null,
    created_by text references carer (id) on delete set null,
    created_at timestamptz not null default now()
);
create index food_plan_item_baby on food_plan_item (baby_id, planned_on);
