-- Story S2.0: the Firebase push token of the Registered Device. A new phone replaces the row, so the old phone stops getting pushes.
alter table registered_devices add column push_token text;
