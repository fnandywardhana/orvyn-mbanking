-- Persona history paid people at other banks under fixed names (Sri Wahyuni at BRI...). Record them in the
-- External Directory so paying them again, or scheduling a payment, shows the same name instead of a generated one.
-- Persona transfers are the ones without a "from" in the memo; customers' own transfers already used the directory.
insert into external_directory (bank, account_number, name)
select distinct on (code, j.memo->'to'->>'account') code, j.memo->'to'->>'account', j.memo->'to'->>'name'
from journal_entries j
join (values ('BCA','BCA'),('MANDIRI','Mandiri'),('BRI','BRI'),('BNI','BNI'),('BSI','Bank Syariah Indonesia'),('CIMB','CIMB Niaga'),
  ('PERMATA','Permata'),('DANAMON','Danamon'),('BTN','BTN'),('MAYBANK','Maybank Indonesia'),('PANIN','Panin'),('MEGA','Bank Mega'),
  ('JAGO','Bank Jago'),('SEABANK','SeaBank')) b(code, name) on b.name = j.memo->'to'->>'bank'
where j.kind = 'external_transfer' and j.memo ? 'to' and not j.memo ? 'from' and j.memo->'to'->>'account' is not null
order by code, j.memo->'to'->>'account', j.created_at
on conflict do nothing;
