-- Story S3.11: Relationship Managers. Numbers use 0000 blocks so a tester's call can't reach a real person.
create table relationship_managers (
  id    uuid primary key default gen_random_uuid(),
  name  text not null,
  title text not null default 'Relationship Manager',
  phone text not null,
  email text not null
);
alter table customers add column rm_id uuid references relationship_managers(id);

insert into relationship_managers (name, title, phone, email) values
  ('Anindya Prameswari', 'Senior Relationship Manager', '+62 21 0000 1101', 'anindya.p@orvyn.example'),
  ('Bima Aditya', 'Relationship Manager', '+62 21 0000 1102', 'bima.a@orvyn.example');
-- Existing Prestige and Private customers get the first one so the card shows up straight away.
update customers set rm_id = (select id from relationship_managers order by name limit 1) where tier in ('prestige', 'private');
