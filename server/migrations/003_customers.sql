-- Customers, sign-in and Registered Devices (stories S1.5, S1.6, S1.32, S1.33)

alter table customers
  add column phone           text unique,
  add column nik             text unique,
  add column birth_date      date,
  add column address         text,
  add column occupation      text,
  add column income_range    text,
  add column purpose         text,
  add column tier            text not null default 'everyday' check (tier in ('everyday', 'prestige', 'private')),
  add column persona         text,                  -- template it was created from, if any
  add column password_hash   text,
  add column pin_hash        text,                  -- null until the customer sets a PIN on first sign-in
  add column failed_attempts int not null default 0, -- wrong password or PIN in a row
  add column locked          boolean not null default false;

-- The one phone a Customer is signed in on. device_token is a random secret kept on the phone.
create table registered_devices (
  customer_id  uuid primary key references customers(id),
  device_token text not null unique,
  created_at   timestamptz not null default now()
);

create table customer_sessions (
  token       text primary key,
  customer_id uuid not null references customers(id),
  last_seen   timestamptz not null default now()
);

-- Posting date shown to customers; seeded history is backdated, live entries use now().
create index on journal_entries (created_at);
