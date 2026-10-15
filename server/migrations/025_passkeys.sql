-- Story S3.12: passkeys (WebAuthn) for orvyn.delvault.uk. Only the public key is stored.
create table passkeys (
  id          text primary key,              -- credential id, base64url
  customer_id uuid not null references customers(id),
  public_key  bytea not null,
  counter     bigint not null default 0,
  transports  text[],
  created_at  timestamptz not null default now(),
  last_used   timestamptz
);
create index on passkeys (customer_id);
