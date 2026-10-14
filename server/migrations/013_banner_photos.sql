-- Banners get a photo (served from public/banners) and a button that opens a feature.
alter table banners
  add column image  text check (image ~ '^[a-z]+\.webp$'),
  add column action text check (action in ('transfer', 'qris', 'pay', 'topup', 'cards'));

delete from banners where title in ('Pay bills in seconds', 'Freeze a card in one tap');

insert into banners (title, body, detail, style, image, action, ends_at, sort) values
  ('Scan. Pay. Done.', 'Pay at any QRIS sticker with your camera.',
   'Tap QRIS on the home screen and point your camera at the code. Check the shop name and the amount, then enter your PIN. A screenshot of a code works too: tap Gallery and pick it.',
   'blue', 'qris.webp', 'qris', now() + interval '180 days', 1),
  ('Free transfers to friends', 'Sending to another ORVYN account costs nothing.',
   'Tap Transfer and type their account number. Their name shows up before you send, so you know it''s the right person. Save people you pay often and next time takes one tap. Other banks go by BI-FAST for Rp2,500.',
   'blue', 'friends.webp', 'transfer', now() + interval '180 days', 2),
  ('Every bill in one place', 'PLN, BPJS, phone credit and e-wallets.',
   'Tap Pay, pick what to pay and enter your PIN. Numbers you paid before are saved, so next month is one tap.',
   'blue', 'home.webp', 'pay', now() + interval '180 days', 3),
  ('You decide where your card works', 'Online, overseas and contactless, each with its own switch.',
   'Open Cards and use the switches under your card. Lost it? Freeze the card in one tap, then unfreeze it when it turns up.',
   'black', 'online.webp', 'cards', now() + interval '180 days', 4),
  ('Flying soon?', 'Switch on overseas payments before you go.',
   'Open Cards, pick your card and switch on Overseas payments. Your daily card limit still applies. Switch it off again when you''re home.',
   'blue', 'travel.webp', 'cards', now() + interval '180 days', 5),
  ('ORVYN Prestige', 'A much higher daily limit for bigger plans.',
   'Prestige customers can send far more each day than on Everyday. Ask the ORVYN team to move your account to Prestige.',
   'black', 'city.webp', null, now() + interval '180 days', 6);
