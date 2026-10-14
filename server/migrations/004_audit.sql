-- Audit Entries: every change an Officer makes (story S1.38). Rows are never updated or deleted.
create table audit_entries (
  id         bigserial primary key,
  officer_id uuid not null references officers(id),
  action     text not null,
  target     text,
  before     jsonb,
  after      jsonb,
  created_at timestamptz not null default now()
);
create index on audit_entries (created_at desc);
