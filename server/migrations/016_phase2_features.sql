-- Phase 2 switches (STORIES-F2 definition of done: every feature can be switched off in Settings).
update settings set value = value || '{"pockets": true, "scheduled": true, "requests": true, "insights": true, "vyna": true, "statements": true}'::jsonb
where key = 'features';
