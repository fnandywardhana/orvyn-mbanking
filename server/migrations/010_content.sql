-- App content an officer edits without an app update (stories S1.8, S1.31, S1.36)
create table banners (
  id         bigserial primary key,
  title      text not null,
  body       text not null,
  detail     text not null,
  style      text not null default 'blue' check (style in ('blue', 'black', 'gold')),
  starts_at  timestamptz not null default now(),
  ends_at    timestamptz not null,
  sort       int not null default 0
);

create table faqs (
  id       bigserial primary key,
  category text not null,
  question text not null,
  answer   text not null,
  sort     int not null default 0
);

insert into banners (title, body, detail, style, ends_at, sort) values
  ('Pay bills in seconds', 'PLN, BPJS, credit and e-wallets in one place.', 'Tap Pay on the home screen, pick what to pay, and use your PIN. Numbers you paid before are saved, so next time is one tap.', 'blue', now() + interval '60 days', 1),
  ('Freeze a card in one tap', 'Lost your card? Freeze it, then unfreeze when it turns up.', 'Open Cards and switch on Freeze card. Every payment stops until you switch it off again. You can also turn off online, overseas and contactless payments one by one.', 'black', now() + interval '60 days', 2);

insert into faqs (category, question, answer, sort) values
  ('Getting started', 'Is ORVYN Bank a real bank?', 'No. ORVYN Bank is a demo made for a portfolio. All money in it is pretend, and nothing connects to real banks, QRIS or billers.', 1),
  ('Getting started', 'How do I sign in on a new phone?', 'Use your phone number and password once. ORVYN then signs out your old phone and asks you to set a PIN on the new one.', 2),
  ('Transfers', 'How much does a transfer cost?', 'Sending to another ORVYN customer is free. BI-FAST to other banks costs Rp2,500. RTGS, for amounts above Rp100,000,000, costs Rp25,000. You always see the fee before you enter your PIN.', 3),
  ('Transfers', 'Why was my transfer refused?', 'Usually the amount is more than your balance or more than your daily limit. The message tells you which, and how much you can still send today.', 4),
  ('Cards', 'How do I stop my card from being used?', 'Open Cards and switch on Freeze card. Nothing can be paid with it until you switch it off.', 5),
  ('Cards', 'Where can I see my card number?', 'Open Cards, tap Show card details and enter your PIN. The number hides itself after 30 seconds, and the screen can''t be captured while it''s shown.', 6),
  ('Security', 'What happens if I enter the wrong PIN five times?', 'Your account locks to keep it safe. Ask the ORVYN team to unlock it.', 7),
  ('Security', 'Does ORVYN read my contacts or photos?', 'No. ORVYN only uses the camera while you scan a QR code, and the code is read on your phone. See Privacy in your profile for everything ORVYN can and can''t access.', 8);
