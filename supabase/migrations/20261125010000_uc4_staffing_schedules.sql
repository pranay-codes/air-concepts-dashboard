set lock_timeout = '5s';

alter table public.forecast_staffing
  add column hourly_rate numeric(18,2),
  add column weekly_hours numeric(7,2),
  add column payment_frequency text,
  add column first_payday date,
  add column employment_start date,
  add column employment_end date,
  add constraint forecast_staffing_schedule_check check (
    (payment_frequency is null and hourly_rate is null and weekly_hours is null and first_payday is null and employment_start is null and employment_end is null)
    or (payment_frequency is not null and payment_frequency in ('WEEKLY','FORTNIGHTLY','MONTHLY') and hourly_rate is not null and hourly_rate >= 0
      and weekly_hours is not null and weekly_hours >= 0 and weekly_hours <= 168 and first_payday is not null
      and (employment_end is null or employment_start is null or employment_end >= employment_start))
  );
