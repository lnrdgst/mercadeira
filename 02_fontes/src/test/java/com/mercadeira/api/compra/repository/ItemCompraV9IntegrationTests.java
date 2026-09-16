package com.mercadeira.api.compra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mercadeira.api.compra.domain.Compra;
import com.mercadeira.api.compra.domain.ItemCompra;
import com.mercadeira.api.compra.domain.ParticipanteCompra;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@Transactional
@Rollback
class ItemCompraV9IntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ParticipanteCompraRepository participanteCompraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;


    private static final Instant T = Instant.parse("2026-09-11T15:00:00Z");

    @Autowired private org.springframework.core.env.Environment environment;

    @Test
    void flywayAplicaV9EHibernateValidaSchema() {
        assertThat(jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class)).containsExactly("1","2","3","4","5","6","7","8","9","10");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void itemSemRestauracaoMantemCamposNulos() {
        var c=criarContexto();
        var item=itemCompraRepository.saveAndFlush(ItemCompra.criarDaPreparacao(c.compra(),c.itemLista()));
        entityManager.clear();
        item=itemCompraRepository.findById(item.getId()).orElseThrow();
        assertThat(item.getRestauradoPorParticipanteCompra()).isNull();
        assertThat(item.getRestauradoEm()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"2026-09-11T15:00:00.123456Z","2026-09-11T15:00:00.123456789Z"})
    void persistenciaJpaPreservaIgualdadeDosInstantesELazy(String valor) {
        var c=criarContexto(); var item=item(c,"REMOVIDO");
        var instante=Instant.parse(valor);
        org.springframework.test.util.ReflectionTestUtils.setField(item,"status",com.mercadeira.api.compra.domain.StatusItemCompra.NO_CARRINHO);
        org.springframework.test.util.ReflectionTestUtils.setField(item,"restauradoPorParticipanteCompra",c.participanteCompra());
        org.springframework.test.util.ReflectionTestUtils.setField(item,"restauradoEm",instante);
        org.springframework.test.util.ReflectionTestUtils.setField(item,"marcadoEm",instante);
        entityManager.flush(); entityManager.clear();
        var salvo=itemCompraRepository.findById(item.getId()).orElseThrow();
        assertThat(salvo.getRestauradoEm()).isEqualTo(salvo.getMarcadoEm());
        assertThat(salvo.getRestauradoEm()).isEqualTo(jdbcTemplate.queryForObject(
                "select restaurado_em from item_compra where id=?",Timestamp.class,item.getId()).toInstant());
        assertThat(org.hibernate.Hibernate.isInitialized(salvo.getRestauradoPorParticipanteCompra())).isFalse();
        assertThat(salvo.getRestauradoPorParticipanteCompra().getId()).isEqualTo(c.participanteCompra().getId());
        assertThat(org.hibernate.Hibernate.isInitialized(salvo.getRestauradoPorParticipanteCompra())).isFalse();
        assertThat(salvo.getRestauradoPorParticipanteCompra().getNomeSnapshot()).isEqualTo("Ana");
        assertThat(salvo.getDecisaoRemocao()).isEqualTo(com.mercadeira.api.compra.domain.DecisaoRemocao.APROVADA);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"REMOVIDO","REJEITADA"})
    void decisoesAnterioresContinuamValidas(String estado) {
        var c=criarContexto(); var item=item(c,estado);
        entityManager.clear();
        var salvo=itemCompraRepository.findById(item.getId()).orElseThrow();
        assertThat(salvo.getStatus().name()).isEqualTo(estado.equals("REMOVIDO")?"REMOVIDO":"NO_CARRINHO");
        assertThat(salvo.getRestauradoEm()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={
        "autor_sem_data","data_sem_autor","aprovada_sem_restauracao","anterior_resolucao",
        "sem_marcador","sem_marcado_em","instantes_diferentes","pendente","rejeitada_removido"})
    void bancoRejeitaRestauracaoOuDecisaoIncoerente(String caso) {
        var c=criarContexto(); var item=item(c,"REMOVIDO");
        UUID restaurador=c.participanteCompra().getId(), marcador=c.participanteCompra().getMembroFamilia().getId();
        Instant restaurado=T, marcado=T;
        String status="NO_CARRINHO", decisao="APROVADA";
        switch(caso) {
            case "autor_sem_data" -> { restaurado=null; status="REMOVIDO"; }
            case "data_sem_autor" -> { restaurador=null; status="REMOVIDO"; }
            case "aprovada_sem_restauracao" -> { restaurador=null; restaurado=null; }
            case "anterior_resolucao" -> { restaurado=T.minusSeconds(3); marcado=restaurado; }
            case "sem_marcador" -> marcador=null;
            case "sem_marcado_em" -> marcado=null;
            case "instantes_diferentes" -> marcado=T.plusNanos(1000);
            case "pendente" -> { status="PENDENTE"; decisao=null; }
            case "rejeitada_removido" -> { status="REMOVIDO"; decisao="REJEITADA"; }
            default -> throw new IllegalArgumentException(caso);
        }
        final UUID autorFinal=restaurador, marcadorFinal=marcador;
        final Instant dataFinal=restaurado, marcadoFinal=marcado;
        final String estadoFinal=status, decisaoFinal=decisao;
        assertThatThrownBy(() -> atualizar(item.getId(),autorFinal,dataFinal,marcadorFinal,marcadoFinal,estadoFinal,decisaoFinal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void fkCompostaRejeitaRestauradorDeOutraCompra() {
        var c=criarContexto(); var outro=criarContexto(); var item=item(c,"REMOVIDO");
        assertThatThrownBy(() -> atualizar(item.getId(),outro.participanteCompra().getId(),T,
                c.participanteCompra().getMembroFamilia().getId(),T,"NO_CARRINHO","APROVADA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("fk_item_compra_restaurado_por_mesma_compra");
    }

    @Test
    void novoCicloPreservaUltimaRestauracao() {
        var c=criarContexto(); var item=item(c,"REMOVIDO");
        atualizar(item.getId(),c.participanteCompra().getId(),T,c.participanteCompra().getMembroFamilia().getId(),T,"NO_CARRINHO","APROVADA");
        entityManager.clear();
        var salvo=itemCompraRepository.findById(item.getId()).orElseThrow();
        var membro=membroFamiliaRepository.findById(c.participanteCompra().getMembroFamilia().getId()).orElseThrow();
        salvo.solicitarRemocao(membro,T.plusSeconds(1));
        entityManager.flush();
        assertThat(salvo.getRestauradoEm()).isEqualTo(T);
        salvo.rejeitarRemocao(membro,T.plusSeconds(2));
        entityManager.flush();
        salvo.solicitarRemocao(membro,T.plusSeconds(3));
        salvo.aprovarRemocao(membro,T.plusSeconds(4));
        entityManager.flush(); entityManager.clear();
        salvo=itemCompraRepository.findById(item.getId()).orElseThrow();
        assertThat(salvo.getRestauradoEm()).isEqualTo(T);
        assertThat(salvo.getStatus().name()).isEqualTo("REMOVIDO");
    }

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void migraV8ComDadosRepresentativosSemBackfill() {
        var c=criarContexto();
        for(String estado:java.util.List.of("PENDENTE","NO_CARRINHO","REMOCAO_SOLICITADA","REMOVIDO","REJEITADA")) item(c,estado);
        String schema="v8_"+UUID.randomUUID().toString().replace("-","");
        var flyway8=org.flywaydb.core.Flyway.configure().dataSource(postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),postgreSQLContainer.getPassword()).schemas(schema).defaultSchema(schema).target("8").load();
        flyway8.migrate();
        UUID membroId=c.participanteCompra().getMembroFamilia().getId();
        UUID usuarioId=c.participanteCompra().getMembroFamilia().getUsuario().getId();
        UUID familiaId=c.compra().getListaCompra().getFamilia().getId();
        copiar(schema,"usuario","id",usuarioId);
        copiar(schema,"familia","id",familiaId);
        copiar(schema,"membro_familia","id",membroId);
        copiar(schema,"lista_compra","id",c.compra().getListaCompra().getId());
        copiar(schema,"item_lista","id",c.itemLista().getId());
        copiar(schema,"compra","id",c.compra().getId());
        String colunasParticipanteV8="id,compra_id,participante_lista_origem_id,membro_familia_id,nome_snapshot,papel_snapshot,gerado_em";
        jdbcTemplate.update("insert into "+schema+".participante_compra ("+colunasParticipanteV8+") select "+colunasParticipanteV8+
                " from public.participante_compra where id=?",c.participanteCompra().getId());
        String colunas=jdbcTemplate.queryForObject("""
                select string_agg(quote_ident(column_name), ', ' order by ordinal_position)
                from information_schema.columns where table_schema=? and table_name='item_compra'
                """,String.class,schema);
        jdbcTemplate.update("insert into "+schema+".item_compra ("+colunas+") select "+colunas+
                " from public.item_compra where compra_id=?",c.compra().getId());
        var antes=jdbcTemplate.queryForList("select "+colunas+" from "+schema+".item_compra order by id");
        assertThat(antes).hasSize(5);
        org.flywaydb.core.Flyway.configure().dataSource(postgreSQLContainer.getJdbcUrl(),
                postgreSQLContainer.getUsername(),postgreSQLContainer.getPassword()).schemas(schema).defaultSchema(schema).target("9").load().migrate();
        assertThat(jdbcTemplate.queryForList("select "+colunas+" from "+schema+".item_compra order by id")).isEqualTo(antes);
        assertThat(jdbcTemplate.queryForObject("select count(*) from "+schema+
                ".item_compra where restaurado_em is not null or restaurado_por_participante_compra_id is not null",Integer.class)).isZero();
    }

    private void copiar(String schema,String tabela,String coluna,UUID id) {
        jdbcTemplate.update("insert into "+schema+"."+tabela+" select * from public."+tabela+" where "+coluna+"=?",id);
    }

    private void atualizar(UUID item,UUID autor,Instant restaurado,UUID marcador,Instant marcado,String status,String decisao) {
        jdbcTemplate.update("""
                update item_compra set restaurado_por_participante_compra_id=?,restaurado_em=?,
                marcado_por_membro_familia_id=?,marcado_em=?,status=?,decisao_remocao=? where id=?
                """,autor,restaurado==null?null:Timestamp.from(restaurado),marcador,
                marcado==null?null:Timestamp.from(marcado),status,decisao,item);
    }

    private ItemCompra item(Contexto c,String estado) {
        var item=ItemCompra.criarDuranteCompra(c.compra(),c.participanteCompra(),"Produto",BigDecimal.ONE,
                UnidadeMedida.UNIDADE,null,null,1,T.minusSeconds(5));
        var membro=c.participanteCompra().getMembroFamilia();
        if(!estado.equals("PENDENTE")) item.colocarNoCarrinho(membro,T.minusSeconds(4));
        if(java.util.List.of("REMOCAO_SOLICITADA","REMOVIDO","REJEITADA").contains(estado)) item.solicitarRemocao(membro,T.minusSeconds(3));
        if(estado.equals("REMOVIDO")) item.aprovarRemocao(membro,T.minusSeconds(2));
        if(estado.equals("REJEITADA")) item.rejeitarRemocao(membro,T.minusSeconds(2));
        return itemCompraRepository.saveAndFlush(item);
    }

    private Contexto criarContexto() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Usuario usuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), usuario, agora));
        MembroFamilia membro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(familia, usuario, agora));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista", CategoriaCompra.SUPERMERCADO, null, membro, agora));
        Compra compra = compraRepository.saveAndFlush(Compra.iniciar(lista, membro, agora));
        ParticipanteCompra participanteCompra = participanteCompraRepository.saveAndFlush(
                ParticipanteCompra.criarDireto(compra, membro, agora));
        ItemLista itemLista = itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, membro, agora));
        return new Contexto(compra, participanteCompra, itemLista);
    }

    private record Contexto(Compra compra, ParticipanteCompra participanteCompra, ItemLista itemLista) {
    }
}
