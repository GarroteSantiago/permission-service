-- Un permiso por usuario y snippet; la clave primaria es la restricción de unicidad.
create table permission (
    snippet_id uuid not null,
    user_id text not null,
    role text not null check (role in ('OWNER', 'READ')),
    created_at timestamptz not null default now(),
    constraint permission_pkey primary key (snippet_id, user_id)
);

-- Listado de snippets accesibles para un usuario.
create index permission_user_id_idx on permission (user_id);
