-- Inbox Messages and Announcements (stories S1.27, S1.28)
create table inbox_messages (
  id          bigserial primary key,
  customer_id uuid not null references customers(id),
  title       text not null,
  body        text not null,
  link        text,                 -- e.g. 'tx:<journal id>'
  read_at     timestamptz,
  created_at  timestamptz not null default now()
);
create index on inbox_messages (customer_id, id desc);

create table announcements (
  id         bigserial primary key,
  title      text not null,
  body       text not null,
  ends_at    timestamptz not null,
  created_at timestamptz not null default now()
);

create table announcement_seen (
  announcement_id bigint not null references announcements(id),
  customer_id     uuid not null references customers(id),
  primary key (announcement_id, customer_id)
);
