-- Cards (stories S1.23–S1.26). Card numbers start with 9, a range no real card network uses.
create table cards (
  id           uuid primary key default gen_random_uuid(),
  customer_id  uuid not null references customers(id),
  account_id   uuid not null references accounts(id), -- Savings Account for debit, Credit Account for credit
  kind         text not null check (kind in ('debit', 'credit')),
  product      text not null check (product in ('debit', 'cashback', 'voyage')),
  number       text not null unique,
  cvv          text not null,
  expiry       text not null,                 -- MM/YY
  frozen       boolean not null default false,
  online       boolean not null default true,
  overseas     boolean not null default false,
  contactless  boolean not null default true,
  daily_limit  bigint not null,
  created_at   timestamptz not null default now()
);
create index on cards (customer_id);
