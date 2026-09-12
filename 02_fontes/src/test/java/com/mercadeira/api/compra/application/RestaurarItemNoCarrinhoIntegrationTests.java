package com.mercadeira.api.compra.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.compra.repository.ItemCompraRepository;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ItemLista;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.ParticipanteLista;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.lista.repository.ItemListaRepository;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class RestaurarItemNoCarrinhoIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired private ColocarItemNoCarrinho colocarItemNoCarrinho;
    @Autowired private IniciarCompra iniciarCompra;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FamiliaRepository familiaRepository;
    @Autowired private MembroFamiliaRepository membroFamiliaRepository;
    @Autowired private ListaCompraRepository listaCompraRepository;
    @Autowired private ParticipanteListaRepository participanteListaRepository;
    @Autowired private ItemListaRepository itemListaRepository;
    @Autowired private CompraRepository compraRepository;
    @Autowired private ItemCompraRepository itemCompraRepository;
    @Autowired private JdbcTemplate jdbcTemplate;



    @Autowired private RestaurarItemNoCarrinho restaurar;
    @Autowired private SolicitarRemocaoItemCompra solicitar;
    @Autowired private AprovarRemocaoItemCompra aprovar;
    @Autowired private RejeitarRemocaoItemCompra rejeitar;
    @Autowired private FinalizarCompra finalizar;
    @Autowired private AdicionarItemDuranteCompra adicionar;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void restauraPreservaAuditoriaEReplayDeOutroParticipante(boolean admin) {
        var c=removido(); var antes=auditoria(c);
        UUID autor=admin?c.primeiroUsuarioId():c.camilaUsuarioId();
        UUID membro=admin?c.primeiroMembroId():c.camilaMembroId();
        var resultado=restore(c,autor);
        assertThat(resultado.restauradoAgora()).isTrue();
        verificar(c,membro);
        assertThat(auditoria(c)).isEqualTo(antes);
        var completo=linha(c);
        assertThat(restore(c,c.segundoUsuarioId()).restauradoAgora()).isFalse();
        assertThat(linha(c)).isEqualTo(completo);
    }

    @org.junit.jupiter.api.Test
    void itemIncluidoDuranteCompraMantemAutoriaDeInclusao() {
        var base=criarContexto();
        var novo=adicionar.executar(base.segundoUsuarioId(),base.familiaId(),base.listaId(),
                new AdicionarItemDuranteCompraCommand("Leite",BigDecimal.ONE,UnidadeMedida.UNIDADE,null,null));
        var c=new Contexto(base.familiaId(),base.listaId(),base.compraId(),novo.getId(),
                base.primeiroUsuarioId(),base.segundoUsuarioId(),base.primeiroMembroId(),base.segundoMembroId(),
                base.camilaUsuarioId(),base.camilaMembroId());
        remover(c); var antes=auditoria(c);
        restore(c,c.camilaUsuarioId());
        assertThat(auditoria(c)).isEqualTo(antes);
        assertThat(antes.get("adicionado_por_participante_compra_id")).isNotNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"aprovar","rejeitar"})
    void restauradorPassaASerUnicoDecisorNoNovoCiclo(String decisao) {
        var c=removido(); restore(c,c.camilaUsuarioId());
        var rest=linha(c);
        solicitar.executar(c.segundoUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
        var pendente=linha(c);
        assertThat(pendente.get("decisao_remocao")).isNull();
        assertThat(pendente.get("remocao_resolvida_em")).isNull();
        assertThat(pendente.get("restaurado_em")).isEqualTo(rest.get("restaurado_em"));
        assertThat(pendente.get("restaurado_por_participante_compra_id")).isEqualTo(rest.get("restaurado_por_participante_compra_id"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decidir(c,c.primeiroUsuarioId(),decisao))
                .isInstanceOf(UsuarioNaoPodeDecidirRemocaoItemCompraException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decidir(c,c.segundoUsuarioId(),decisao))
                .isInstanceOf(UsuarioNaoPodeDecidirRemocaoItemCompraException.class);
        decidir(c,c.camilaUsuarioId(),decisao);
        assertThat(linha(c).get("status")).isEqualTo(decisao.equals("aprovar")?"REMOVIDO":"NO_CARRINHO");
        assertThat(linha(c).get("restaurado_em")).isEqualTo(rest.get("restaurado_em"));
    }

    @org.junit.jupiter.api.Test
    void segundaRestauracaoSubstituiSomenteUltimaRestauracao() {
        var c=removido(); restore(c,c.camilaUsuarioId()); var primeira=linha(c);
        solicitar.executar(c.segundoUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
        decidir(c,c.camilaUsuarioId(),"aprovar"); var auditoriaCiclo2=auditoria(c);
        assertThat(restore(c,c.segundoUsuarioId()).restauradoAgora()).isTrue();
        verificar(c,c.segundoMembroId());
        assertThat(auditoria(c)).isEqualTo(auditoriaCiclo2);
        assertThat(linha(c).get("restaurado_por_participante_compra_id")).isNotEqualTo(primeira.get("restaurado_por_participante_compra_id"));
        assertThat((java.sql.Timestamp)linha(c).get("restaurado_em")).isAfter((java.sql.Timestamp)primeira.get("restaurado_em"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"PENDENTE","NO_CARRINHO","REMOCAO_SOLICITADA","REJEITADA","REMOVIDO_INCONSISTENTE"})
    void estadosIncompativeisNaoSaoReplay(String estado) {
        var c=criarContexto();
        if(!estado.equals("PENDENTE")) colocarItemNoCarrinho.executar(c.primeiroUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
        if(estado.equals("REMOCAO_SOLICITADA")||estado.equals("REJEITADA")) {
            solicitar.executar(c.segundoUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
            if(estado.equals("REJEITADA")) decidir(c,c.primeiroUsuarioId(),"rejeitar");
        }
        if(estado.equals("REMOVIDO_INCONSISTENTE")) jdbcTemplate.update("update item_compra set status='REMOVIDO' where id=?",c.itemCompraId());
        var antes=linha(c);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restore(c,c.camilaUsuarioId()))
                .isInstanceOf(com.mercadeira.api.compra.domain.RestauracaoItemCompraInvalidaException.class);
        assertThat(linha(c)).isEqualTo(antes);
    }

    @org.junit.jupiter.api.Test
    void aprovacaoAntigaNaoRemoveItemRestaurado() {
        var c=removido(); restore(c,c.camilaUsuarioId()); var antes=linha(c);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decidir(c,c.primeiroUsuarioId(),"aprovar"))
                .isInstanceOf(UsuarioNaoPodeDecidirRemocaoItemCompraException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decidir(c,c.camilaUsuarioId(),"aprovar"))
                .isInstanceOf(com.mercadeira.api.compra.domain.TransicaoStatusItemCompraInvalidaException.class);
        assertThat(linha(c)).isEqualTo(antes);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"observador","administrador","inativo","outra_familia"})
    void rejeitaUsuariosNaoAutorizados(String tipo) {
        var c=removido(); UUID usuario;
        if(tipo.equals("inativo")) {
            usuario=c.camilaUsuarioId();
            jdbcTemplate.update("update membro_familia set status='INATIVO' where id=?",c.camilaMembroId());
        } else if(tipo.equals("outra_familia")) usuario=criarContexto().primeiroUsuarioId();
        else {
            var novo=usuarioRepository.saveAndFlush(Usuario.criar("Observador",UUID.randomUUID()+"@test.local","hash",Instant.now()));
            var familia=familiaRepository.findById(c.familiaId()).orElseThrow();
            membroFamiliaRepository.saveAndFlush(tipo.equals("administrador")?
                    MembroFamilia.criarAdministrador(familia,novo,Instant.now()):MembroFamilia.criarMembro(familia,novo,Instant.now()));
            usuario=novo.getId();
        }
        var antes=linha(c);
        Class<? extends RuntimeException> erro=tipo.equals("inativo")||tipo.equals("outra_familia")?
                com.mercadeira.api.lista.application.MembroFamiliaInvalidoException.class:UsuarioNaoParticipaDaCompraException.class;
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restore(c,usuario)).isInstanceOf(erro);
        assertThat(linha(c)).isEqualTo(antes);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FINALIZADA","CANCELADA"})
    void compraForaDeAndamentoBloqueiaInclusiveReplay(String status) {
        var c=removido(); restore(c,c.camilaUsuarioId());
        if(status.equals("FINALIZADA")) finalizar.executar(c.primeiroUsuarioId(),c.familiaId(),c.listaId());
        else jdbcTemplate.update("update compra set status='CANCELADA' where id=?",c.compraId());
        var antes=linha(c);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restore(c,c.segundoUsuarioId())).isInstanceOf(CompraForaDeAndamentoException.class);
        assertThat(linha(c)).isEqualTo(antes);
    }

    @org.junit.jupiter.api.Test
    void rejeitaContextosDivergentes() {
        var c=removido(); var outro=removido();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restaurar.executar(c.primeiroUsuarioId(),c.familiaId(),c.listaId(),outro.itemCompraId()))
                .isInstanceOf(ItemCompraNaoEncontradoException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restaurar.executar(c.primeiroUsuarioId(),outro.familiaId(),c.listaId(),c.itemCompraId()))
                .isInstanceOf(com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException.class);
    }

    @org.junit.jupiter.api.Test
    void concorrenciaDuasRestauracoesPreservaPrimeiroAutor() throws Exception {
        var c=removido(); var pool=Executors.newFixedThreadPool(2);
        var prontas=new CountDownLatch(2); var liberar=new CountDownLatch(1);
        try {
            var a=pool.submit(() -> {prontas.countDown(); if(!liberar.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return restore(c,c.camilaUsuarioId());});
            var b=pool.submit(() -> {prontas.countDown(); if(!liberar.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return restore(c,c.segundoUsuarioId());});
            assertThat(prontas.await(10,TimeUnit.SECONDS)).isTrue(); liberar.countDown();
            var ra=a.get(20,TimeUnit.SECONDS); var rb=b.get(20,TimeUnit.SECONDS);
            assertThat(List.of(ra.restauradoAgora(),rb.restauradoAgora())).containsExactlyInAnyOrder(true,false);
            assertThat(ra.item().getRestauradoEm()).isEqualTo(rb.item().getRestauradoEm());
            assertThat(ra.item().getRestauradoPorParticipanteCompra().getId()).isEqualTo(rb.item().getRestauradoPorParticipanteCompra().getId());
            verificar(c,ra.restauradoAgora()?c.camilaMembroId():c.segundoMembroId());
        } finally {liberar.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"finalizar,true","finalizar,false","solicitar,true","solicitar,false"})
    void concorrenciaComFinalizacaoOuSolicitacao(String operacao,boolean restaurarPrimeiro) throws Exception {
        var c=removido(); var pool=Executors.newFixedThreadPool(2);
        var pronta=new java.util.concurrent.CompletableFuture<Integer>();
        var segundaPid=new java.util.concurrent.CompletableFuture<Integer>();
        var liberar=new CountDownLatch(1);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try {
            var a=pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbcTemplate.execute("set local lock_timeout='30s'");
                if(restaurarPrimeiro) restore(c,c.camilaUsuarioId());
                else if(operacao.equals("finalizar")) finalizar.executar(c.primeiroUsuarioId(),c.familiaId(),c.listaId());
                else {
                    compraRepository.findByListaCompra_IdForUpdate(c.listaId()).orElseThrow();
                }
                pronta.complete(jdbcTemplate.queryForObject("select pg_backend_pid()",Integer.class));
                try {if(!liberar.await(40,TimeUnit.SECONDS)) throw new IllegalStateException();}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                if(!restaurarPrimeiro && operacao.equals("solicitar")) solicitar.executar(c.segundoUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
            }));
            int pidA=pronta.get(10,TimeUnit.SECONDS);
            var b=pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbcTemplate.execute("set local lock_timeout='30s'");
                segundaPid.complete(jdbcTemplate.queryForObject("select pg_backend_pid()",Integer.class));
                if(!restaurarPrimeiro) restore(c,c.camilaUsuarioId());
                else if(operacao.equals("finalizar")) finalizar.executar(c.primeiroUsuarioId(),c.familiaId(),c.listaId());
                else solicitar.executar(c.segundoUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
            }));
            int pidB=segundaPid.get(10,TimeUnit.SECONDS); long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            boolean bloqueada=false;
            while(System.nanoTime()<limite) {
                bloqueada=Boolean.TRUE.equals(jdbcTemplate.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,pidA,pidB));
                if(bloqueada) break;
                if(b.isDone()){b.get();throw new AssertionError("Nao aguardou lock");}
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(bloqueada).isTrue(); liberar.countDown();
            if(!restaurarPrimeiro && operacao.equals("solicitar")) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> a.get(10,TimeUnit.SECONDS))
                        .hasCauseInstanceOf(com.mercadeira.api.compra.domain.TransicaoStatusItemCompraInvalidaException.class);
            } else a.get(10,TimeUnit.SECONDS);
            if(!restaurarPrimeiro && operacao.equals("finalizar")) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> b.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(CompraForaDeAndamentoException.class);
                assertThat(linha(c).get("status")).isEqualTo("REMOVIDO");
            } else {
                b.get(10,TimeUnit.SECONDS);
                assertThat(linha(c).get("status")).isEqualTo(restaurarPrimeiro&&operacao.equals("solicitar")?"REMOCAO_SOLICITADA":"NO_CARRINHO");
            }
            if(operacao.equals("finalizar")) assertThat(jdbcTemplate.queryForObject("select status from compra where id=?",String.class,c.compraId())).isEqualTo("FINALIZADA");
        } finally {liberar.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }

    private ResultadoRestauracaoItemCompra restore(Contexto c,UUID usuario) {return restaurar.executar(usuario,c.familiaId(),c.listaId(),c.itemCompraId());}
    private void decidir(Contexto c,UUID usuario,String acao) {
        if(acao.equals("aprovar")) aprovar.executar(usuario,c.familiaId(),c.listaId(),c.itemCompraId());
        else rejeitar.executar(usuario,c.familiaId(),c.listaId(),c.itemCompraId());
    }
    private Contexto removido(){var c=criarContexto();remover(c);return c;}
    private void remover(Contexto c){
        colocarItemNoCarrinho.executar(c.primeiroUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
        solicitar.executar(c.segundoUsuarioId(),c.familiaId(),c.listaId(),c.itemCompraId());
        decidir(c,c.primeiroUsuarioId(),"aprovar");
    }
    private java.util.Map<String,Object> linha(Contexto c){return jdbcTemplate.queryForMap("select * from item_compra where id=?",c.itemCompraId());}
    private java.util.Map<String,Object> auditoria(Contexto c){return jdbcTemplate.queryForMap("""
            select remocao_solicitada_por_membro_familia_id,remocao_solicitada_em,decisao_remocao,
            remocao_resolvida_por_membro_familia_id,remocao_resolvida_em,
            adicionado_por_participante_compra_id,adicionado_em from item_compra where id=?
            """,c.itemCompraId());}
    private void verificar(Contexto c,UUID membro){
        var linha=linha(c);
        assertThat(linha.get("status")).isEqualTo("NO_CARRINHO");
        assertThat(linha.get("marcado_por_membro_familia_id")).isEqualTo(membro);
        assertThat(linha.get("restaurado_em")).isNotNull().isEqualTo(linha.get("marcado_em"));
        assertThat(linha.get("restaurado_por_participante_compra_id")).isEqualTo(jdbcTemplate.queryForObject(
                "select id from participante_compra where compra_id=? and membro_familia_id=?",UUID.class,c.compraId(),membro));
    }

    private Contexto criarContexto() {
        Instant agora = Instant.parse("2026-09-08T18:00:00Z");
        Usuario primeiroUsuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Ana", "ana-" + UUID.randomUUID() + "@test.local", "hash", agora));
        Familia familia = familiaRepository.saveAndFlush(Familia.criar(
                "Familia Teste", UUID.randomUUID().toString().replace("-", ""), primeiroUsuario, agora));
        MembroFamilia primeiroMembro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarAdministrador(familia, primeiroUsuario, agora));
        ListaCompra lista = listaCompraRepository.saveAndFlush(ListaCompra.criar(
                familia, "Lista semanal", CategoriaCompra.SUPERMERCADO, null, primeiroMembro, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, primeiroMembro, agora));

        Usuario segundoUsuario = usuarioRepository.saveAndFlush(Usuario.criar(
                "Bia", "bia-" + UUID.randomUUID() + "@test.local", "hash", agora));
        MembroFamilia segundoMembro = membroFamiliaRepository.saveAndFlush(
                MembroFamilia.criarMembro(familia, segundoUsuario, agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista, segundoMembro, agora));

        Usuario camila = usuarioRepository.saveAndFlush(Usuario.criar("Camila",UUID.randomUUID()+"@test.local","hash",agora));
        MembroFamilia camilaMembro = membroFamiliaRepository.saveAndFlush(MembroFamilia.criarMembro(familia,camila,agora));
        participanteListaRepository.saveAndFlush(ParticipanteLista.criar(lista,camilaMembro,agora));
        itemListaRepository.saveAndFlush(ItemLista.criar(
                lista, "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, null, null, 1, primeiroMembro, agora));

        UUID compraId = iniciarCompra.iniciar(primeiroUsuario.getId(), familia.getId(), lista.getId()).compra().getId();
        UUID itemCompraId = itemCompraRepository.findByCompra_IdOrderByOrdemExibicaoAscIdAsc(compraId).getFirst().getId();

        return new Contexto(
                familia.getId(), lista.getId(), compraId, itemCompraId,
                primeiroUsuario.getId(), segundoUsuario.getId(), primeiroMembro.getId(), segundoMembro.getId(),camila.getId(),camilaMembro.getId());
    }

    private record Contexto(
            UUID familiaId,
            UUID listaId,
            UUID compraId,
            UUID itemCompraId,
            UUID primeiroUsuarioId,
            UUID segundoUsuarioId,
            UUID primeiroMembroId,
            UUID segundoMembroId, UUID camilaUsuarioId, UUID camilaMembroId) {
    }
}
