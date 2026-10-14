-- QRIS merchants (stories S1.15–S1.17). Made-up shops; money paid to them lands in Merchant Settlement.
create table merchants (
  id         uuid primary key default gen_random_uuid(),
  code       text not null unique,        -- short id carried inside the QR (tag 26/01)
  name       text not null,
  city       text not null,
  category   text not null,
  mcc        text not null,               -- merchant category code, 4 digits
  active     boolean not null default true,
  created_at timestamptz not null default now()
);

insert into merchants (code, name, city, category, mcc) values
  ('KSC001', 'Kopi Senja Cipete',        'Jakarta Selatan', 'food',      '5814'),
  ('WBH002', 'Warteg Bahari',            'Jakarta Selatan', 'food',      '5812'),
  ('MAK003', 'Mie Ayam Pak Kumis',       'Jakarta Pusat',   'food',      '5812'),
  ('MSJ004', 'Minimarket Sejahtera',     'Jakarta Barat',   'groceries', '5411'),
  ('TBA005', 'Toko Buku Aksara',         'Bandung',         'shopping',  '5942'),
  ('APS006', 'Apotek Sehat Selalu',      'Surabaya',        'health',    '5912'),
  ('PKS007', 'Parkir Kota Senayan',      'Jakarta Pusat',   'transport', '7523'),
  ('BKM008', 'Bakmi Kemang 88',          'Jakarta Selatan', 'food',      '5812'),
  ('DLB009', 'Distro Lokal Blok M',      'Jakarta Selatan', 'shopping',  '5651'),
  ('SPD010', 'Sate Pak Darto',           'Yogyakarta',      'food',      '5812');
