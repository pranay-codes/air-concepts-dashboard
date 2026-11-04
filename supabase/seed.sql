-- Seed data is synthetic only. Never load real customer, supplier or bank data here.
-- Re-runnable: each insert skips rows that already exist, so repeat runs never duplicate rows.

insert into public.entities (code, display_name) values
  ('services', 'Synthetic Services entity'),
  ('commercial', 'Synthetic Commercial entity'),
  ('hotas', 'Synthetic HotAS entity')
on conflict (code) do nothing;
