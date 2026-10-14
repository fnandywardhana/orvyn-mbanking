-- Switches an officer can flip without an app update (story S1.37)
insert into settings (key, value) values
  ('features', '{"transfer": true, "qris": true, "bills": true, "cards": true, "register": true}'),
  ('maintenance', '{"on": false, "message": "ORVYN is getting an update. Payments are paused for a few minutes."}');
