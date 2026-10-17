-- 0.9.0 quality pass

-- Q-D2 (PO-2): at most 10 NIK checks per registration.
alter table registrations add column nik_checks int not null default 0;

-- Q-D6 (PO-6): a new PIN without the old one, only within 10 minutes of a password sign-in in that same session.
alter table customer_sessions add column password_at timestamptz;

-- Q-F8 (PO-21): banners keep today's order as explicit positions 1..n. starts_at already holds the time each was made.
update banners b set sort = r.n from (select id, row_number() over (order by sort, id) as n from banners) r where r.id = b.id;
