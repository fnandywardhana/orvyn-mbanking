-- Story S2.16: Vyna's daily message limit per customer, and what she cost.
insert into settings (key, value) values ('vyna', '{"daily_limit": 30}') on conflict do nothing;

create table vyna_usage (
  customer_id   uuid not null references customers(id),
  day           date not null,       -- in Jakarta
  messages      int not null default 0,
  input_tokens  bigint not null default 0,
  output_tokens bigint not null default 0,
  cache_read    bigint not null default 0,
  cache_write   bigint not null default 0,
  primary key (customer_id, day)
);
