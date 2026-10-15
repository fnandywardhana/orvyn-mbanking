-- Stories S2.8–S2.11: spending categories, budgets and their alerts.
-- The seven categories a customer sees, from the raw category on each journal's memo.
create function spend_category(raw text) returns text language sql immutable as $$
  select case raw
    when 'food' then 'food'
    when 'transport' then 'transport'
    when 'groceries' then 'shopping' when 'shopping' then 'shopping'
    when 'bills' then 'bills' when 'housing' then 'bills' when 'education' then 'bills'
    when 'leisure' then 'entertainment' when 'entertainment' then 'entertainment'
    when 'family' then 'transfer' when 'transfer' then 'transfer'
    else 'other' end
$$;

-- S2.8 AC3: the customer moved one transaction to another category.
create table category_overrides (
  customer_id uuid not null references customers(id),
  journal_id  uuid not null references journal_entries(id),
  category    text not null check (category in ('food', 'transport', 'shopping', 'bills', 'entertainment', 'transfer', 'other')),
  primary key (customer_id, journal_id)
);

create table budgets (
  customer_id   uuid not null references customers(id),
  category      text not null check (category in ('food', 'transport', 'shopping', 'bills', 'entertainment', 'transfer', 'other')),
  monthly_limit bigint not null check (monthly_limit > 0),
  primary key (customer_id, category)
);

-- One reminder per budget, threshold and month (S2.10 AC2).
create table budget_alerts (
  customer_id uuid not null references customers(id),
  category    text not null,
  month       text not null, -- 2026-10
  threshold   int not null,  -- 80 or 100
  primary key (customer_id, category, month, threshold)
);
