-- Stories S3.5–S3.9: investments (ADR 0003: holdings in units outside the ledger, Rupiah through Investment Clearing).
alter table customers add column risk_profile text check (risk_profile in ('conservative', 'moderate', 'aggressive'));

insert into accounts (kind, code, name, currency) values ('house', 'INVESTMENT_CLEARING', 'Investment Clearing', 'IDR');

create table products (
  id          text primary key,             -- e.g. ORV-MM
  kind        text not null check (kind in ('fund', 'bond', 'gold')),
  name        text not null,
  subtype     text,                         -- funds: money_market, fixed_income, balanced, equity
  risk_level  int not null check (risk_level between 1 and 4),
  manager     text,
  coupon_bp   int,                          -- bonds: yearly coupon in basis points
  maturity    date,                         -- bonds
  min_buy     bigint not null,
  drift_bp    int,                          -- funds: simulated yearly growth
  vol_bp      int,                          -- funds: simulated yearly volatility
  description text not null
);

-- One price per Bank Day: a fund's NAV per unit, made up by a seeded random walk (src/investments.ts).
create table prices (
  product_id text not null references products(id),
  day        date not null,
  price      numeric(18, 4) not null,
  primary key (product_id, day)
);

create table holdings (
  customer_id uuid not null references customers(id),
  product_id  text not null references products(id),
  quantity    numeric(20, 6) not null check (quantity >= 0), -- fund units, bond face value in Rupiah, or grams of gold
  cost        bigint not null,                                -- Rupiah paid for what is still held
  primary key (customer_id, product_id)
);

create table bond_coupons (
  customer_id uuid not null references customers(id),
  product_id  text not null references products(id),
  month       text not null,
  primary key (customer_id, product_id, month)
);

insert into products (id, kind, name, subtype, risk_level, manager, min_buy, drift_bp, vol_bp, description) values
  ('ORV-MM', 'fund', 'Arunika Pasar Uang', 'money_market', 1, 'PT Arunika Aset Manajemen (fictional)', 100000, 450, 30,
   'Short-term deposits and money market paper. Grows slowly and steadily; good for money you may need soon.'),
  ('ORV-FI', 'fund', 'Samudra Pendapatan Tetap', 'fixed_income', 2, 'PT Samudra Investasi (fictional)', 100000, 620, 350,
   'Indonesian government and top-rated corporate bonds. Steadier than shares, with some ups and downs.'),
  ('ORV-BL', 'fund', 'Garuda Campuran Seimbang', 'balanced', 3, 'PT Garuda Kapital (fictional)', 100000, 800, 900,
   'About half shares and half bonds, for growth with a cushion.'),
  ('ORV-EQ', 'fund', 'Cahaya Ekuitas Indonesia', 'equity', 4, 'PT Cahaya Timur Aset (fictional)', 100000, 1050, 1800,
   'Shares of large Indonesian companies. The most growth over many years, and the biggest swings along the way.');
insert into products (id, kind, name, risk_level, coupon_bp, maturity, min_buy, description) values
  ('ORI-D01', 'bond', 'ORI-D01 retail government bond', 1, 640, (current_date + interval '3 years')::date, 1000000,
   'A made-up retail bond in the style of ORI: a fixed 6.40% a year, paid every month, and your money back at maturity.'),
  ('SR-D01', 'bond', 'SR-D01 retail sukuk', 1, 625, (current_date + interval '2 years')::date, 1000000,
   'A made-up retail sukuk in the style of SR: a fixed 6.25% a year, paid every month, and your money back at maturity.');
insert into products (id, kind, name, risk_level, min_buy, description) values
  ('GOLD', 'gold', 'Gold Savings', 2, 50000, 'Buy gold by the gram, from as little as Rp50,000, and sell it back any time.');

insert into settings (key, value) values ('gold_price', '{"buy": 1855000, "sell": 1962000}') on conflict do nothing; -- Rupiah per gram
