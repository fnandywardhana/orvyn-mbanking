-- Double-entry ledger (docs/adr/0001-double-entry-ledger.md)

create table customers (
  id         uuid primary key default gen_random_uuid(),
  full_name  text not null,
  created_at timestamptz not null default now()
);

create table accounts (
  id          uuid primary key default gen_random_uuid(),
  kind        text not null check (kind in ('savings', 'pocket', 'credit', 'house')),
  customer_id uuid references customers(id),
  code        text unique,             -- house accounts only, e.g. EXTERNAL_CLEARING
  number      text unique,             -- savings accounts only
  name        text not null,
  currency    char(3) not null,
  balance     bigint not null default 0, -- minor units; cache of sum(postings.amount)
  floor       bigint,                    -- lowest allowed balance; null = unbounded (house accounts)
  created_at  timestamptz not null default now(),
  check (floor is null or balance >= floor),
  check ((kind = 'house') = (customer_id is null))
);

create table journal_entries (
  id              uuid primary key default gen_random_uuid(),
  kind            text not null,
  idempotency_key text unique,
  reverses_id     uuid unique references journal_entries(id), -- a journal can be reversed at most once
  memo            jsonb not null default '{}',
  created_at      timestamptz not null default now()
);

create table postings (
  id         bigserial primary key,
  journal_id uuid not null references journal_entries(id),
  account_id uuid not null references accounts(id),
  amount     bigint not null check (amount <> 0)
);
create index on postings (account_id, id);
create index on postings (journal_id);

insert into accounts (kind, code, name, currency) values
  ('house', 'EXTERNAL_CLEARING',   'External Clearing',   'IDR'),
  ('house', 'FEE_INCOME',          'Fee Income',          'IDR'),
  ('house', 'MERCHANT_SETTLEMENT', 'Merchant Settlement', 'IDR'),
  ('house', 'BILLER_SETTLEMENT',   'Biller Settlement',   'IDR'),
  ('house', 'DEMO_EQUITY',         'Demo Equity',         'IDR');
