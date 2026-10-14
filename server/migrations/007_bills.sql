-- Bill Payments (stories S1.18–S1.22). Billers use real Indonesian names; every customer number and bill is made up.
create table billers (
  code       text primary key,
  name       text not null,
  kind       text not null check (kind in ('pln_token', 'pln_postpaid', 'bpjs', 'mobile', 'ewallet')),
  fee        bigint not null default 0,
  products   jsonb not null default '[]', -- [{id, label, price}] for prepaid kinds
  sort       int not null default 0
);

insert into billers (code, name, kind, fee, products, sort) values
  ('PLN_TOKEN', 'PLN token',      'pln_token',    2500, '[{"id":"20","label":"Rp20,000","price":20000},{"id":"50","label":"Rp50,000","price":50000},{"id":"100","label":"Rp100,000","price":100000},{"id":"200","label":"Rp200,000","price":200000},{"id":"500","label":"Rp500,000","price":500000},{"id":"1000","label":"Rp1,000,000","price":1000000}]', 1),
  ('PLN_BILL',  'PLN bill',       'pln_postpaid', 2500, '[]', 2),
  ('BPJS',      'BPJS Kesehatan', 'bpjs',         2500, '[]', 3),
  ('MOBILE',    'Mobile credit and data', 'mobile', 0, '[{"id":"p10","label":"Credit Rp10,000","price":11500},{"id":"p25","label":"Credit Rp25,000","price":26500},{"id":"p50","label":"Credit Rp50,000","price":51000},{"id":"p100","label":"Credit Rp100,000","price":100500},{"id":"d5","label":"Data 5 GB · 30 days","price":45000},{"id":"d15","label":"Data 15 GB · 30 days","price":85000},{"id":"d40","label":"Data 40 GB · 30 days","price":150000}]', 4),
  ('GOPAY',     'GoPay',          'ewallet',      1000, '[]', 5),
  ('OVO',       'OVO',            'ewallet',      1000, '[]', 6),
  ('DANA',      'DANA',           'ewallet',      1000, '[]', 7),
  ('SHOPEEPAY', 'ShopeePay',      'ewallet',      1000, '[]', 8);

-- Specific made-up customers an officer sets up; any other number gets a stable generated name and bill.
create table biller_customers (
  biller         text not null references billers(code),
  number         text not null,
  name           text not null,
  monthly_bill   bigint,
  primary key (biller, number)
);

-- Postpaid bills already paid, one per biller, number and month (YYYY-MM).
create table bill_payments (
  biller     text not null,
  number     text not null,
  period     text not null,
  journal_id uuid not null references journal_entries(id),
  primary key (biller, number, period)
);

-- Numbers a customer paid before, offered for the next payment.
create table saved_bill_numbers (
  customer_id uuid not null references customers(id),
  biller      text not null,
  number      text not null,
  name        text not null,
  last_used   timestamptz not null default now(),
  primary key (customer_id, biller, number)
);
