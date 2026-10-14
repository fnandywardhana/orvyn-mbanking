-- Transfers (stories S1.12–S1.14, S1.37)

-- Saved Recipients ("Penerima favorit"). bank 'ORVYN' means another ORVYN customer.
create table saved_recipients (
  id             uuid primary key default gen_random_uuid(),
  customer_id    uuid not null references customers(id),
  bank           text not null,
  account_number text not null,
  name           text not null,
  nickname       text,
  created_at     timestamptz not null default now(),
  unique (customer_id, bank, account_number)
);

-- External Directory overrides: specific made-up holder names at other banks. Other numbers get a generated name.
create table external_directory (
  bank           text not null,
  account_number text not null,
  name           text not null,
  primary key (bank, account_number)
);

-- Demo rules an officer can change (story S1.37). Amounts in rupiah.
create table settings (
  key   text primary key,
  value jsonb not null
);
insert into settings (key, value) values
  ('daily_limit', '{"everyday": 100000000, "prestige": 1000000000, "private": 5000000000}'),
  ('transfer_fees', '{"bifast": 2500, "bifast_max": 250000000, "rtgs": 25000, "rtgs_min": 100000001}');
