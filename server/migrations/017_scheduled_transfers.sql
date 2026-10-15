-- Stories S2.4–S2.5: transfers that send themselves at 07:00 WIB, once or on repeat. The PIN was given when it was set up.
create table scheduled_transfers (
  id             uuid primary key default gen_random_uuid(),
  customer_id    uuid not null references customers(id),
  bank           text not null,
  account_number text not null,
  name           text not null,
  amount         bigint not null check (amount >= 10000),
  method         text not null,
  note           text,
  repeat         text not null check (repeat in ('once', 'weekly', 'monthly')),
  day            int,               -- weekly: 1 (Mon)–7; monthly: 1–31, clamped to the month's last day
  next_run       date,              -- null once a one-off has run
  paused         boolean not null default false,
  last_result    jsonb,             -- {"at", "ok", "message", "transactionId"}
  created_at     timestamptz not null default now(),
  deleted_at     timestamptz
);
create index on scheduled_transfers (next_run) where deleted_at is null and not paused;
create index on scheduled_transfers (customer_id);
