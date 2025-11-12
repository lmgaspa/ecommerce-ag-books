-- garante unicidade case-insensitive com apenas 1 índice
drop index if exists ux_authors_email_lower;                     -- idempotente
create unique index if not exists uq_authors_email_lower
  on public.authors (lower(email));

-- garante coluna gerada e defaults (idempotente)
alter table public.authors
  add column if not exists email_lower text
    generated always as (lower(email)) stored;

alter table public.authors
  alter column created_at set default now();

-- índice temporal (idempotente)
create index if not exists ix_authors_created_at
  on public.authors (created_at desc);

-- check de formato de e-mail (idempotente)
do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'chk_authors_email_format'
      and conrelid = 'public.authors'::regclass
  ) then
    alter table public.authors
      add constraint chk_authors_email_format
      check (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$');
  end if;
end$$;
