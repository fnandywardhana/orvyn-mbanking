-- Stories S3.1–S3.4: FX Accounts and Time Deposits are ordinary Accounts (ADR 0001).
alter table accounts drop constraint accounts_kind_check;
alter table accounts add constraint accounts_kind_check check (kind in ('savings', 'pocket', 'credit', 'house', 'fx', 'deposit'));
create unique index one_fx_account_per_currency on accounts (customer_id, currency) where kind = 'fx';

insert into accounts (kind, code, name, currency) values
  ('house', 'FX_POSITION_IDR', 'FX Position IDR', 'IDR'),
  ('house', 'FX_POSITION_USD', 'FX Position USD', 'USD'),
  ('house', 'FX_POSITION_SGD', 'FX Position SGD', 'SGD'),
  ('house', 'FX_POSITION_JPY', 'FX Position JPY', 'JPY'),
  ('house', 'FX_POSITION_AUD', 'FX Position AUD', 'AUD'),
  ('house', 'FX_POSITION_EUR', 'FX Position EUR', 'EUR'),
  ('house', 'INTEREST_EXPENSE', 'Interest Expense', 'IDR'),
  ('house', 'TAX_PAYABLE', 'Tax Payable', 'IDR');

-- Rupiah per one unit of the currency. buy: ORVYN buys from the customer; sell: ORVYN sells to the customer.
create table fx_rates (
  currency   char(3) primary key,
  buy        numeric(12, 4) not null check (buy > 0),
  sell       numeric(12, 4) not null check (sell >= buy),
  updated_at timestamptz not null default now()
);
insert into fx_rates (currency, buy, sell) values
  ('USD', 16180, 16420), ('SGD', 12480, 12720), ('JPY', 105.40, 108.90), ('AUD', 10560, 10820), ('EUR', 17540, 17880);

create table time_deposits (
  account_id    uuid primary key references accounts(id),
  customer_id   uuid not null references customers(id),
  principal     bigint not null check (principal >= 1000000),
  rate_bp       int not null,          -- yearly rate in basis points, fixed when opened (or renewed)
  tenor_months  int not null check (tenor_months in (1, 3, 6, 12)),
  opened_on     date not null,
  maturity_date date not null,
  instruction   text not null check (instruction in ('rollover', 'payout')),
  closed_at     timestamptz
);
create index on time_deposits (maturity_date) where closed_at is null;

insert into settings (key, value) values
  ('deposit_rates', '{"1": 300, "3": 350, "6": 400, "12": 450}'),   -- basis points a year
  ('fx_prestige_spread', '{"factor": 0.5}')                          -- Prestige/Private pay half the normal spread
on conflict do nothing;
update settings set value = value || '{"fx": true, "deposits": true, "invest": true, "passkeys": true}'::jsonb where key = 'features';
