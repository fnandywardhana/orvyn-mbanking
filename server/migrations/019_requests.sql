-- Stories S2.6–S2.7: one customer asks another for money; a Split Bill is a group of requests with one total.
create table splits (
  id          uuid primary key default gen_random_uuid(),
  customer_id uuid not null references customers(id),
  total       bigint not null check (total > 0),
  note        text,
  created_at  timestamptz not null default now()
);

create table money_requests (
  id           uuid primary key default gen_random_uuid(),
  requester_id uuid not null references customers(id),
  payer_id     uuid not null references customers(id),
  amount       bigint not null check (amount >= 1000),
  note         text,
  split_id     uuid references splits(id),
  status       text not null default 'open' check (status in ('open', 'paid', 'declined', 'cancelled')),
  journal_id   uuid references journal_entries(id),
  created_at   timestamptz not null default now(),
  expires_at   timestamptz not null default now() + interval '7 days', -- open past this reads as expired
  check (requester_id <> payer_id)
);
create index on money_requests (payer_id, created_at desc);
create index on money_requests (requester_id, created_at desc);
