create table officers (
  id                   uuid primary key default gen_random_uuid(),
  email                text not null unique,
  name                 text not null,
  role                 text not null check (role in ('super_admin', 'operator', 'content')),
  password_hash        text not null,
  must_change_password boolean not null default true,
  failed_attempts      int not null default 0,
  locked_until         timestamptz,
  active               boolean not null default true,
  created_at           timestamptz not null default now()
);

create table officer_sessions (
  token      text primary key,
  officer_id uuid not null references officers(id),
  last_seen  timestamptz not null default now()
);
