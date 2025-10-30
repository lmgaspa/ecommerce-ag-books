create table if not exists cookie_consents (
  id bigserial primary key,
  created_at timestamptz not null default now(),
  ip_hash text not null,
  user_agent text not null,
  prefs jsonb not null,
  source text not null
);

create index if not exists idx_cookie_consents_created_at on cookie_consents(created_at);
