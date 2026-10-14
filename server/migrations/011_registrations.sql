-- Registrations (stories S1.1–S1.4). Identity photos never reach the server (ADR 0002).
create table otp_codes (
  phone      text primary key,
  code_hash  text not null,
  expires_at timestamptz not null,
  attempts   int not null default 0
);

create table registrations (
  id            uuid primary key default gen_random_uuid(),
  token         text not null unique,         -- the app's handle for this registration
  phone         text not null,
  verified      boolean not null default false,
  full_name     text,
  nik           text,
  birth_date    date,
  address       text,
  occupation    text,
  income_range  text,
  purpose       text,
  password_hash text,
  pin_hash      text,
  status        text not null default 'draft' check (status in ('draft', 'pending', 'approved', 'rejected')),
  reason        text,
  customer_id   uuid references customers(id),
  submitted_at  timestamptz,
  created_at    timestamptz not null default now()
);
