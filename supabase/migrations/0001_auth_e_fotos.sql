
create table if not exists public.perfis (
  id          uuid primary key references auth.users (id) on delete cascade,
  nome        text not null check (char_length(nome) between 1 and 60),
  usuario     text not null unique check (usuario ~ '^[a-z0-9_.]{3,30}$'),
  xp          integer not null default 0,
  criado_em   timestamptz not null default now()
);

alter table public.perfis enable row level security;

drop policy if exists "perfis: todos leem" on public.perfis;
create policy "perfis: todos leem"
  on public.perfis for select
  to authenticated
  using (true);

drop policy if exists "perfis: dono edita" on public.perfis;
create policy "perfis: dono edita"
  on public.perfis for update
  to authenticated
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- Cria o perfil automaticamente no cadastro, usando os metadados enviados
-- pelo app em signUp({ options: { data: { nome, usuario } } }).
create or replace function public.criar_perfil_no_cadastro()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.perfis (id, nome, usuario)
  values (
    new.id,
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'nome'), ''), split_part(new.email, '@', 1)),
    coalesce(
      nullif(lower(regexp_replace(new.raw_user_meta_data ->> 'usuario', '[^a-z0-9_.]', '', 'gi')), ''),
      lower(regexp_replace(split_part(new.email, '@', 1), '[^a-z0-9_.]', '', 'gi')) || '_' || left(new.id::text, 4)
    )
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists ao_criar_usuario on auth.users;
create trigger ao_criar_usuario
  after insert on auth.users
  for each row execute function public.criar_perfil_no_cadastro();

-- ---------------------------------------------------------------------------
-- 2. FOTOS — cada post do feed. O arquivo fica no bucket "fotos" do Storage
--    em <user_id>/<timestamp>.jpg; aqui guardamos o caminho + legenda.
-- ---------------------------------------------------------------------------
create table if not exists public.fotos (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users (id) on delete cascade,
  caminho     text not null,                          -- caminho no bucket "fotos"
  legenda     text check (char_length(legenda) <= 200),
  filtro      text,                                   -- filtro CSS aplicado na câmera
  local       text,                                   -- nome do rolê/local (texto livre por enquanto)
  local_id    integer,                                -- id do vértice no grafo (futuro)
  criado_em   timestamptz not null default now()
);

create index if not exists fotos_criado_em_idx on public.fotos (criado_em desc);
create index if not exists fotos_user_id_idx on public.fotos (user_id);

alter table public.fotos enable row level security;

drop policy if exists "fotos: logados leem" on public.fotos;
create policy "fotos: logados leem"
  on public.fotos for select
  to authenticated
  using (true);

drop policy if exists "fotos: dono insere" on public.fotos;
create policy "fotos: dono insere"
  on public.fotos for insert
  to authenticated
  with check (auth.uid() = user_id);

drop policy if exists "fotos: dono apaga" on public.fotos;
create policy "fotos: dono apaga"
  on public.fotos for delete
  to authenticated
  using (auth.uid() = user_id);

-- ---------------------------------------------------------------------------
-- 3. STORAGE — bucket "fotos", leitura pública (as fotos aparecem no feed),
--    escrita só na própria pasta (<user_id>/...), máx. 5 MB, só imagem.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('fotos', 'fotos', true, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "fotos storage: leitura publica" on storage.objects;
create policy "fotos storage: leitura publica"
  on storage.objects for select
  to public
  using (bucket_id = 'fotos');

drop policy if exists "fotos storage: dono envia" on storage.objects;
create policy "fotos storage: dono envia"
  on storage.objects for insert
  to authenticated
  with check (
    bucket_id = 'fotos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

drop policy if exists "fotos storage: dono apaga" on storage.objects;
create policy "fotos storage: dono apaga"
  on storage.objects for delete
  to authenticated
  using (
    bucket_id = 'fotos'
    and (storage.foldername(name))[1] = auth.uid()::text
  );

-- ---------------------------------------------------------------------------
-- 4. VIEW do feed — foto + quem postou, pra uma query só no app.
-- ---------------------------------------------------------------------------
create or replace view public.feed_fotos
with (security_invoker = true) as
select f.id, f.user_id, f.caminho, f.legenda, f.filtro, f.local, f.local_id, f.criado_em,
       p.nome, p.usuario, p.xp
from public.fotos f
join public.perfis p on p.id = f.user_id
order by f.criado_em desc;
