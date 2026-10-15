-- Story S2.1–S2.3: Pockets. The money sits in an Account of kind 'pocket'; this row holds the goal and the auto-save rule.
create table pockets (
  account_id  uuid primary key references accounts(id),
  customer_id uuid not null references customers(id),
  image       text not null,
  target      bigint check (target > 0),
  target_date date,
  -- {"type":"payday","percent":10} | {"type":"payday","amount":500000} | {"type":"weekly","day":1,"amount":100000} | {"type":"roundup"}
  rule        jsonb,
  rule_since  timestamptz,          -- auto-save only looks at money that moved after the rule was set
  reached_at  timestamptz,          -- target reached; cleared when the balance drops below it again
  closed_at   timestamptz
);
create index on pockets (customer_id);

-- One row per auto-save attempt, so a skipped one (not enough money) is not retried every minute.
create table autosave_runs (
  pocket_id  uuid not null references pockets(account_id),
  source     text not null,         -- the journal id it reacts to, or 'week:2026-W42'
  journal_id uuid references journal_entries(id),
  skipped    text,
  created_at timestamptz not null default now(),
  primary key (pocket_id, source)
);
