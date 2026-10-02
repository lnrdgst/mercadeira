create table registro_financeiro_compra (
    id uuid primary key,
    compra_id uuid not null references compra (id),
    valor numeric(19, 2) not null check (valor > 0),
    tipo varchar(20) not null,
    estabelecimento_nome varchar(120),
    criado_em timestamp with time zone not null
);

create index idx_registro_financeiro_compra_compra_criado_em
    on registro_financeiro_compra (compra_id, criado_em, id);
