create table solicitacao_item_compra (
    id uuid primary key,
    compra_id uuid not null references compra(id),
    solicitante_id uuid not null references participante_compra(id),
    descricao varchar(200) not null,
    quantidade numeric(12,3),
    unidade_medida varchar(30),
    status varchar(20) not null,
    criado_em timestamp with time zone not null,
    decidido_em timestamp with time zone,
    decidido_por_id uuid references participante_compra(id),
    constraint ck_solicitacao_item_compra_quantidade check (quantidade is null or quantidade > 0)
);
create index idx_solicitacao_item_compra_compra_status on solicitacao_item_compra(compra_id, status);
