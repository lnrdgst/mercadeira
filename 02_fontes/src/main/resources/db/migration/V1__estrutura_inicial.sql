CREATE TABLE usuario (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_usuario_email UNIQUE (email)
);

CREATE TABLE familia (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    criada_por_usuario_id UUID NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL,
    atualizada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_familia_criada_por_usuario
        FOREIGN KEY (criada_por_usuario_id) REFERENCES usuario (id)
);

CREATE TABLE membro_familia (
    id UUID PRIMARY KEY,
    familia_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    papel VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    apelido VARCHAR(120),
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_membro_familia_familia
        FOREIGN KEY (familia_id) REFERENCES familia (id),
    CONSTRAINT fk_membro_familia_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id),
    CONSTRAINT uk_membro_familia_familia_usuario UNIQUE (familia_id, usuario_id),
    CONSTRAINT ck_membro_familia_papel
        CHECK (papel IN ('ADMINISTRADOR', 'MEMBRO')),
    CONSTRAINT ck_membro_familia_status
        CHECK (status IN ('ATIVO', 'INATIVO'))
);

CREATE UNIQUE INDEX uk_membro_familia_usuario_ativo
    ON membro_familia (usuario_id)
    WHERE status = 'ATIVO';

CREATE TABLE solicitacao_entrada_familia (
    id UUID PRIMARY KEY,
    familia_id UUID NOT NULL,
    solicitante_usuario_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    solicitada_em TIMESTAMPTZ NOT NULL,
    resolvida_por_membro_familia_id UUID,
    resolvida_em TIMESTAMPTZ,
    CONSTRAINT fk_solicitacao_entrada_familia_familia
        FOREIGN KEY (familia_id) REFERENCES familia (id),
    CONSTRAINT fk_solicitacao_entrada_familia_solicitante
        FOREIGN KEY (solicitante_usuario_id) REFERENCES usuario (id),
    CONSTRAINT fk_solicitacao_entrada_familia_resolvida_por
        FOREIGN KEY (resolvida_por_membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT ck_solicitacao_entrada_familia_status
        CHECK (status IN ('PENDENTE', 'APROVADA', 'REJEITADA')),
    CONSTRAINT ck_solicitacao_entrada_familia_resolucao_preenchida
        CHECK (
            (resolvida_por_membro_familia_id IS NULL) = (resolvida_em IS NULL)
        ),
    CONSTRAINT ck_solicitacao_entrada_familia_status_resolucao
        CHECK (
            (status = 'PENDENTE' AND resolvida_por_membro_familia_id IS NULL)
            OR (status IN ('APROVADA', 'REJEITADA') AND resolvida_por_membro_familia_id IS NOT NULL)
        )
);

CREATE TABLE lista_compra (
    id UUID PRIMARY KEY,
    familia_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    estabelecimento VARCHAR(120),
    criada_por_membro_familia_id UUID NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL,
    atualizada_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_lista_compra_familia
        FOREIGN KEY (familia_id) REFERENCES familia (id),
    CONSTRAINT fk_lista_compra_criada_por
        FOREIGN KEY (criada_por_membro_familia_id) REFERENCES membro_familia (id)
);

CREATE TABLE participante_lista (
    id UUID PRIMARY KEY,
    lista_compra_id UUID NOT NULL,
    membro_familia_id UUID NOT NULL,
    entrou_em TIMESTAMPTZ NOT NULL,
    saiu_em TIMESTAMPTZ,
    CONSTRAINT fk_participante_lista_lista_compra
        FOREIGN KEY (lista_compra_id) REFERENCES lista_compra (id),
    CONSTRAINT fk_participante_lista_membro_familia
        FOREIGN KEY (membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT uk_participante_lista_lista_membro
        UNIQUE (lista_compra_id, membro_familia_id)
);

CREATE TABLE item_lista (
    id UUID PRIMARY KEY,
    lista_compra_id UUID NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    quantidade NUMERIC(12, 3),
    unidade_medida VARCHAR(30),
    marca VARCHAR(120),
    observacoes TEXT,
    adicionado_por_membro_familia_id UUID NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_item_lista_lista_compra
        FOREIGN KEY (lista_compra_id) REFERENCES lista_compra (id),
    CONSTRAINT fk_item_lista_adicionado_por
        FOREIGN KEY (adicionado_por_membro_familia_id) REFERENCES membro_familia (id)
);

CREATE TABLE compra (
    id UUID PRIMARY KEY,
    lista_compra_id UUID NOT NULL,
    iniciada_por_membro_familia_id UUID NOT NULL,
    nome_lista_snapshot VARCHAR(120) NOT NULL,
    categoria_snapshot VARCHAR(100) NOT NULL,
    estabelecimento_snapshot VARCHAR(120),
    status VARCHAR(30) NOT NULL,
    iniciada_em TIMESTAMPTZ NOT NULL,
    finalizada_em TIMESTAMPTZ,
    CONSTRAINT fk_compra_lista_compra
        FOREIGN KEY (lista_compra_id) REFERENCES lista_compra (id),
    CONSTRAINT fk_compra_iniciada_por
        FOREIGN KEY (iniciada_por_membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT uk_compra_lista_compra UNIQUE (lista_compra_id),
    CONSTRAINT ck_compra_status
        CHECK (status IN ('EM_ANDAMENTO', 'FINALIZADA', 'CANCELADA'))
);

CREATE TABLE participante_compra (
    id UUID PRIMARY KEY,
    compra_id UUID NOT NULL,
    participante_lista_origem_id UUID NOT NULL,
    membro_familia_id UUID NOT NULL,
    nome_snapshot VARCHAR(120) NOT NULL,
    papel_snapshot VARCHAR(30) NOT NULL,
    gerado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_participante_compra_compra
        FOREIGN KEY (compra_id) REFERENCES compra (id),
    CONSTRAINT fk_participante_compra_participante_lista_origem
        FOREIGN KEY (participante_lista_origem_id) REFERENCES participante_lista (id),
    CONSTRAINT fk_participante_compra_membro_familia
        FOREIGN KEY (membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT uk_participante_compra_origem UNIQUE (participante_lista_origem_id),
    CONSTRAINT uk_participante_compra_compra_membro UNIQUE (compra_id, membro_familia_id),
    CONSTRAINT ck_participante_compra_papel_snapshot
        CHECK (papel_snapshot IN ('ADMINISTRADOR', 'MEMBRO'))
);

CREATE TABLE item_compra (
    id UUID PRIMARY KEY,
    compra_id UUID NOT NULL,
    item_lista_origem_id UUID,
    adicionado_durante_compra BOOLEAN NOT NULL,
    descricao_snapshot VARCHAR(200) NOT NULL,
    quantidade_snapshot NUMERIC(12, 3),
    unidade_medida_snapshot VARCHAR(30),
    marca_snapshot VARCHAR(120),
    observacoes_snapshot TEXT,
    status VARCHAR(40) NOT NULL,
    marcado_por_membro_familia_id UUID,
    marcado_em TIMESTAMPTZ,
    decisao_remocao VARCHAR(20),
    remocao_solicitada_por_membro_familia_id UUID,
    remocao_solicitada_em TIMESTAMPTZ,
    remocao_resolvida_por_membro_familia_id UUID,
    remocao_resolvida_em TIMESTAMPTZ,
    CONSTRAINT fk_item_compra_compra
        FOREIGN KEY (compra_id) REFERENCES compra (id),
    CONSTRAINT fk_item_compra_item_lista_origem
        FOREIGN KEY (item_lista_origem_id) REFERENCES item_lista (id),
    CONSTRAINT fk_item_compra_marcado_por
        FOREIGN KEY (marcado_por_membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT fk_item_compra_remocao_solicitada_por
        FOREIGN KEY (remocao_solicitada_por_membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT fk_item_compra_remocao_resolvida_por
        FOREIGN KEY (remocao_resolvida_por_membro_familia_id) REFERENCES membro_familia (id),
    CONSTRAINT uk_item_compra_origem UNIQUE (item_lista_origem_id),
    CONSTRAINT ck_item_compra_origem
        CHECK (
            (adicionado_durante_compra AND item_lista_origem_id IS NULL)
            OR (NOT adicionado_durante_compra AND item_lista_origem_id IS NOT NULL)
        ),
    CONSTRAINT ck_item_compra_status
        CHECK (status IN ('PENDENTE', 'NO_CARRINHO', 'REMOCAO_SOLICITADA', 'REMOVIDO')),
    CONSTRAINT ck_item_compra_decisao_remocao
        CHECK (decisao_remocao IS NULL OR decisao_remocao IN ('APROVADA', 'REJEITADA')),
    CONSTRAINT ck_item_compra_remocao_solicitada_preenchida
        CHECK (
            (remocao_solicitada_por_membro_familia_id IS NULL) = (remocao_solicitada_em IS NULL)
        ),
    CONSTRAINT ck_item_compra_remocao_resolvida_preenchida
        CHECK (
            (remocao_resolvida_por_membro_familia_id IS NULL) = (remocao_resolvida_em IS NULL)
        ),
    CONSTRAINT ck_item_compra_decisao_exige_solicitacao
        CHECK (
            decisao_remocao IS NULL
            OR remocao_solicitada_por_membro_familia_id IS NOT NULL
        ),
    CONSTRAINT ck_item_compra_decisao_exige_resolucao
        CHECK (
            decisao_remocao IS NULL
            OR remocao_resolvida_por_membro_familia_id IS NOT NULL
        ),
    CONSTRAINT ck_item_compra_solicitacao_em_andamento
        CHECK (
            status <> 'REMOCAO_SOLICITADA'
            OR (
                remocao_solicitada_por_membro_familia_id IS NOT NULL
                AND decisao_remocao IS NULL
                AND remocao_resolvida_por_membro_familia_id IS NULL
            )
        ),
    CONSTRAINT ck_item_compra_decisao_status
        CHECK (
            decisao_remocao IS NULL
            OR (decisao_remocao = 'APROVADA' AND status = 'REMOVIDO')
            OR (decisao_remocao = 'REJEITADA' AND status = 'NO_CARRINHO')
        )
);
