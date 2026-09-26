package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import com.mercadeira.api.compra.application.*;
import com.mercadeira.api.compra.domain.PresencaOperacional;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.lista.application.*;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Sem transacao no teste: comandos HTTP e threads usam transacoes reais independentes.
@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class PresencaOperacionalIntegrationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwtConfig(DynamicPropertyRegistry r) {
        r.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }
    @Autowired WebApplicationContext context;
    @Autowired CadastrarUsuario cadastrar;
    @Autowired CriarFamilia criarFamilia;
    @Autowired CriarListaCompra criarLista;
    @Autowired AdicionarItemLista adicionarItem;
    @Autowired AdicionarParticipanteLista adicionarParticipante;
    @Autowired IniciarCompra iniciar;
    @Autowired AlterarMinhaPresencaCompra presenca;
    @Autowired FluxoPresencaCompra fluxoPresenca;
    @Autowired ColocarItemNoCarrinho colocar;
    @Autowired RestaurarItemNoCarrinho restaurar;
    @Autowired SolicitarRemocaoItemCompra solicitar;
    @Autowired FinalizarCompra finalizar;
    @Autowired CompraRepository compras;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired jakarta.persistence.EntityManager entityManager;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    record C(UUID ana, UUID bia, UUID familia, UUID lista, UUID compra, UUID item) {
        String url() { return "/api/familias/"+familia+"/listas/"+lista+"/compra"; }
    }
    UUID usuario() { return cadastrar.cadastrar("Pessoa", UUID.randomUUID()+"@test.local", "senha-original").getId(); }
    UUID membro(UUID usuario, UUID familia) {
        var id = UUID.randomUUID();
        jdbc.update("insert into membro_familia(id,familia_id,usuario_id,papel,status,criado_em,atualizado_em) values(?,?,?,'MEMBRO','ATIVO',now(),now())", id, familia, usuario);
        return id;
    }
    C contexto(boolean iniciarAgora) {
        var ana = usuario(); var bia = usuario();
        var familia = criarFamilia.criar(ana,"Casa").getId();
        var lista = criarLista.criar(ana,familia,"Lista",CategoriaCompra.OUTROS,null).getId();
        adicionarParticipante.adicionar(ana,familia,lista,membro(bia,familia));
        adicionarItem.adicionar(ana,familia,lista,"Arroz",BigDecimal.ONE,UnidadeMedida.KG,null,null);
        if (!iniciarAgora) return new C(ana,bia,familia,lista,null,null);
        var compra = iniciar.iniciar(ana,familia,lista).compra().getId();
        var item = jdbc.queryForObject("select id from item_compra where compra_id=?",UUID.class,compra);
        return new C(ana,bia,familia,lista,compra,item);
    }
    Map<String,Object> json(MvcResult r) throws Exception { return JsonPath.read(r.getResponse().getContentAsString(),"$"); }
    Map<String,Object> getCompra(C c, UUID u) throws Exception {
        return json(mvc.perform(get(c.url()).with(jwt().jwt(j -> j.subject(u.toString())))).andExpect(status().isOk()).andReturn());
    }
    Map<String,Object> declarar(C c, UUID u, String estado) throws Exception {
        if ("PRESENTE".equals(estado)) {
            var pedido = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                    .with(jwt().jwt(j -> j.subject(u.toString())))).andExpect(status().isOk()).andReturn());
            var minha = (Map<String,Object>) pedido.get("minhaSolicitacaoPresenca");
            if (minha == null) return pedido;
            UUID responsavel = jdbc.queryForObject("""
                    select membro.usuario_id from compra compra
                    join participante_compra participante on participante.id=compra.responsavel_operacional_id
                    join membro_familia membro on membro.id=participante.membro_familia_id where compra.id=?
                    """, UUID.class, c.compra());
            mvc.perform(post(c.url()+"/solicitacoes-presenca/"+minha.get("id")+"/aprovar")
                    .with(jwt().jwt(j -> j.subject(responsavel.toString())))).andExpect(status().isOk());
            return getCompra(c, u);
        }
        return json(mvc.perform(put(c.url()+"/minha-presenca").with(jwt().jwt(j -> j.subject(u.toString()))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\""+estado+"\"}")).andExpect(status().isOk()).andReturn());
    }
    Map<String,Object> registro(C c, UUID u) {
        return jdbc.queryForMap("select p.* from participante_compra p join membro_familia m on m.id=p.membro_familia_id where p.compra_id=? and m.usuario_id=?", c.compra(),u);
    }
    String itemUrl(C c, String acao) { return c.url()+"/itens/"+c.item()+"/"+acao; }
    void postItem(C c, UUID u, String acao, int esperado) throws Exception {
        mvc.perform(post(itemUrl(c,acao)).with(jwt().jwt(j -> j.subject(u.toString())))).andExpect(status().is(esperado));
    }
    void transferirResponsabilidade(C c, UUID de, UUID para) throws Exception {
        UUID participanteDestino = (UUID) registro(c, para).get("id");
        mvc.perform(post(c.url()+"/responsabilidade-operacional/transferir")
                .with(jwt().jwt(j -> j.subject(de.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participanteCompraId\":\""+participanteDestino+"\"}"))
                .andExpect(status().isOk());
    }

    @Test void inicioPropriedadeReplayGetECapabilities() throws Exception {
        var c=contexto(true);
        assertThat(registro(c,c.ana())).containsEntry("presenca_operacional","PRESENTE");
        assertThat(registro(c,c.bia())).containsEntry("presenca_operacional","NAO_INFORMADA").containsEntry("presenca_alterada_em",null);
        mvc.perform(get(c.url()).with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk())
            .andExpect(jsonPath("$.contextoUsuario.participanteCompra").value(true))
            .andExpect(jsonPath("$.contextoUsuario.podeSolicitarPresenca").value(true))
            .andExpect(jsonPath("$.itens[0].acoes.podeColocarNoCarrinho").value(false));
        var antesAna=registro(c,c.ana());
        var resposta=declarar(c,c.bia(),"PRESENTE");
        assertThat(getCompra(c,c.bia())).isEqualTo(resposta);
        assertThat(registro(c,c.ana())).isEqualTo(antesAna);
        var antesBia=registro(c,c.bia());
        declarar(c,c.bia(),"PRESENTE");
        assertThat(registro(c,c.bia())).isEqualTo(antesBia);
        transferirResponsabilidade(c, c.ana(), c.bia());
        declarar(c,c.ana(),"NAO_PRESENTE");
        var saiu=registro(c,c.ana());
        mvc.perform(post(c.url()).with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk());
        assertThat(registro(c,c.ana())).isEqualTo(saiu);
        mvc.perform(post(c.url()).with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk());
        assertThat(registro(c,c.bia())).isEqualTo(antesBia);
        mvc.perform(get(c.url()).with(jwt().jwt(j -> j.subject(c.bia().toString()))))
            .andExpect(jsonPath("$.itens[0].acoes.podeColocarNoCarrinho").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings={"{}","{\"estado\":null}","{\"estado\":\"NAO_INFORMADA\"}","{\"estado\":\"ONLINE\"}","{\"estado\":\"PRESENTE\",\"usuario\":\"outro\"}","{\"estado\":\"PRESENTE\",\"participante\":\"outro\"}","{\"estado\":\"PRESENTE\",\"executor\":\"outro\"}","{\"estado\":\"PRESENTE\",\"timestamp\":\"2026-09-15T00:00:00Z\"}"})
    void rejeitaPayloadInvalidoOuCamposDeIdentidade(String body) throws Exception {
        var c=contexto(true); var antes=registro(c,c.ana());
        mvc.perform(put(c.url()+"/minha-presenca").with(jwt().jwt(j -> j.subject(c.ana().toString()))).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        assertThat(registro(c,c.ana())).isEqualTo(antes);
    }

    @Test void autorizacaoErrosEFinalizacaoCongelamPresenca() throws Exception {
        var c=contexto(true); var observador=usuario(); membro(observador,c.familia());
        var body="{\"estado\":\"PRESENTE\"}";
        mvc.perform(put(c.url()+"/minha-presenca").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(c.url()+"/minha-presenca").header("Authorization","Bearer invalido").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(c.url()+"/minha-presenca").with(jwt().jwt(j -> j.subject(observador.toString()))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get(c.url()).with(jwt().jwt(j -> j.subject(observador.toString()))))
            .andExpect(jsonPath("$.contextoUsuario.podeAlterarPresenca").value(false));
        jdbc.update("update membro_familia set status='INATIVO' where familia_id=? and usuario_id=?",c.familia(),c.bia());
        mvc.perform(put(c.url()+"/minha-presenca").with(jwt().jwt(j -> j.subject(c.bia().toString()))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(c.url().replace(c.familia().toString(),UUID.randomUUID().toString())+"/minha-presenca")
            .with(jwt().jwt(j -> j.subject(c.ana().toString()))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(put(c.url().replace(c.lista().toString(),UUID.randomUUID().toString())+"/minha-presenca")
            .with(jwt().jwt(j -> j.subject(c.ana().toString()))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        declarar(c,c.ana(),"NAO_PRESENTE"); var antes=registro(c,c.ana());
        mvc.perform(post(c.url()+"/finalizar").with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isConflict());
        declarar(c,c.ana(),"PRESENTE"); var antesFinalizacao=registro(c,c.ana());
        mvc.perform(post(c.url()+"/finalizar").with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk())
            .andExpect(jsonPath("$.contextoUsuario.podeAlterarPresenca").value(false));
        mvc.perform(put(c.url()+"/minha-presenca").with(jwt().jwt(j -> j.subject(c.ana().toString()))).contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"NAO_PRESENTE\"}")).andExpect(status().isConflict());
        assertThat(registro(c,c.ana())).isEqualTo(antesFinalizacao);
    }

    @Test void carrinhoRestauracaoReplaysERemocaoRemotaMantemAuditoria() throws Exception {
        var c=contexto(true);
        postItem(c,c.bia(),"colocar-no-carrinho",409);
        postItem(c,c.ana(),"colocar-no-carrinho",200);
        var carrinho=jdbc.queryForMap("select * from item_compra where id=?",c.item());
        declarar(c,c.ana(),"NAO_PRESENTE");
        postItem(c,c.ana(),"colocar-no-carrinho",409);
        assertThat(jdbc.queryForMap("select * from item_compra where id=?",c.item())).isEqualTo(carrinho);
        declarar(c,c.bia(),"NAO_PRESENTE");
        postItem(c,c.bia(),"solicitar-remocao",200);
        postItem(c,c.ana(),"rejeitar-remocao",403);
        declarar(c,c.ana(),"PRESENTE");
        postItem(c,c.ana(),"rejeitar-remocao",200);
        postItem(c,c.ana(),"solicitar-remocao",200);
        assertThat(jdbc.queryForObject("select status from item_compra where id=?",String.class,c.item())).isEqualTo("REMOVIDO");
        postItem(c,c.bia(),"restaurar-no-carrinho",409);
        declarar(c,c.bia(),"PRESENTE");
        mvc.perform(get(c.url()).with(jwt().jwt(j -> j.subject(c.bia().toString()))))
            .andExpect(jsonPath("$.itens[0].acoes.podeRestaurarNoCarrinho").value(true));
        postItem(c,c.bia(),"restaurar-no-carrinho",200);
        var restaurado=jdbc.queryForMap("select * from item_compra where id=?",c.item());
        postItem(c,c.bia(),"restaurar-no-carrinho",200);
        declarar(c,c.ana(),"PRESENTE");
        postItem(c,c.ana(),"restaurar-no-carrinho",200);
        assertThat(jdbc.queryForMap("select * from item_compra where id=?",c.item())).isEqualTo(restaurado);
        declarar(c,c.bia(),"NAO_PRESENTE");
        postItem(c,c.bia(),"restaurar-no-carrinho",409);
        assertThat(jdbc.queryForMap("select * from item_compra where id=?",c.item())).isEqualTo(restaurado);
    }

    @Test void legadoEmAndamentoPermiteInclusaoFinalizacaoEReutilizacaoSemCopiarPresenca() throws Exception {
        var c=contexto(true);
        jdbc.update("update participante_compra set presenca_operacional='NAO_INFORMADA',presenca_alterada_em=null where compra_id=?",c.compra());
        iniciar.iniciar(c.ana(),c.familia(),c.lista());
        assertThat(registro(c,c.ana())).containsEntry("presenca_operacional","NAO_INFORMADA");
        postItem(c,c.ana(),"colocar-no-carrinho",409);
        declarar(c,c.bia(),"NAO_PRESENTE");
        mvc.perform(post(c.url()+"/itens").with(jwt().jwt(j -> j.subject(c.bia().toString()))).contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Leite\"}"))
            .andExpect(status().isCreated());
        mvc.perform(post(c.url()+"/finalizar").with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isConflict());
        mvc.perform(post(c.url()+"/minha-presenca/solicitacoes").with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk());
        mvc.perform(post(c.url()+"/finalizar").with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk());
        assertThat(registro(c,c.bia())).containsEntry("presenca_operacional","PRESENTE");
        var nova=json(mvc.perform(post(c.url().replace("/compra","/reutilizar")).with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isCreated()).andReturn());
        var novaLista=UUID.fromString(nova.get("id").toString());
        assertThat(jdbc.queryForObject("select count(*) from compra where lista_compra_id=?",Integer.class,novaLista)).isZero();
        var novaCompra=iniciar.iniciar(c.bia(),c.familia(),novaLista).compra().getId();
        assertThat(jdbc.queryForList("select presenca_operacional from participante_compra where compra_id=?",String.class,novaCompra)).containsExactly("PRESENTE");
    }

    @Test void solicitacaoSucessaoESegundoCicloPreservamAutoridadeOperacional() throws Exception {
        var c = contexto(true);
        var solicitacao = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk()).andReturn());
        var pedido = (Map<String,Object>) solicitacao.get("minhaSolicitacaoPresenca");
        var pedidoId = UUID.fromString(pedido.get("id").toString());
        assertThat(pedido).containsEntry("estado", "PENDENTE");
        assertThat(registro(c,c.bia())).containsEntry("presenca_operacional", "NAO_INFORMADA");
        var replay = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk()).andReturn());
        assertThat(((Map<?,?>) replay.get("minhaSolicitacaoPresenca")).get("id")).isEqualTo(pedidoId.toString());
        mvc.perform(post(c.url()+"/solicitacoes-presenca/"+pedidoId+"/aprovar")
                .with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk());
        assertThat(registro(c,c.bia())).containsEntry("presenca_operacional", "PRESENTE");
        transferirResponsabilidade(c, c.ana(), c.bia());
        declarar(c,c.ana(),"NAO_PRESENTE");
        assertThat(jdbc.queryForObject("select responsavel_operacional_id from compra where id=?", UUID.class,c.compra()))
                .isEqualTo(registro(c,c.bia()).get("id"));
        declarar(c,c.bia(),"NAO_PRESENTE");
        assertThat(jdbc.queryForObject("select responsavel_operacional_id from compra where id=?", UUID.class,c.compra())).isNull();
        var novo = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk()).andReturn());
        assertThat(((Map<?,?>) novo.get("responsabilidadeOperacional")).get("ciclo")).isEqualTo(2);
        assertThat(registro(c,c.ana())).containsEntry("presenca_operacional", "PRESENTE");
    }

    @Test void reassuncaoLegadoEFinalizacaoDasPendenciasTemContratoExplicito() throws Exception {
        var c = contexto(true);
        mvc.perform(put(c.url()+"/minha-presenca").with(jwt().jwt(j -> j.subject(c.bia().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PRESENTE\"}"))
                .andExpect(status().isConflict());
        var pedido = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk()).andReturn());
        var id = UUID.fromString(((Map<?,?>) pedido.get("minhaSolicitacaoPresenca")).get("id").toString());
        mvc.perform(post(c.url()+"/solicitacoes-presenca/"+id+"/aprovar")
                .with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk());
        var transferencia=json(mvc.perform(post(c.url()+"/responsabilidade-operacional/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk()).andReturn());
        UUID transferenciaId=UUID.fromString(((Map<?,?>)transferencia.get("minhaSolicitacaoResponsabilidade")).get("id").toString());
        jdbc.update("update membro_familia set papel='ADMINISTRADOR' where familia_id=? and usuario_id=?", c.familia(), c.bia());
        mvc.perform(post(c.url()+"/responsabilidade-operacional/reassumir").with(jwt().jwt(j -> j.subject(c.bia().toString())))
                .contentType(MediaType.APPLICATION_JSON).content("{\"revisao\":1,\"confirmado\":true}")).andExpect(status().isConflict());
        mvc.perform(post(c.url()+"/solicitacoes-responsabilidade/"+transferenciaId+"/aprovar")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isForbidden());
        mvc.perform(post(c.url()+"/solicitacoes-responsabilidade/"+transferenciaId+"/aprovar")
                .with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select responsabilidade_revisao from compra where id=?", Long.class,c.compra())).isEqualTo(2L);
        transferirResponsabilidade(c, c.bia(), c.ana());
        declarar(c,c.bia(),"NAO_PRESENTE");
        var pendente = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk()).andReturn());
        var pendenteId = UUID.fromString(((Map<?,?>) pendente.get("minhaSolicitacaoPresenca")).get("id").toString());
        mvc.perform(post(c.url()+"/finalizar").with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select estado,motivo_cancelamento from solicitacao_presenca_compra where id=?",pendenteId))
                .containsEntry("estado", "CANCELADA").containsEntry("motivo_cancelamento", "COMPRA_FINALIZADA");
    }

    @Test void rejeicaoDePresencaEncerraOCicloECancelamentoPermiteNovaSolicitacao() throws Exception {
        var c = contexto(true);
        var rejeitada = json(mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk()).andReturn());
        var rejeitadaId = UUID.fromString(((Map<?,?>) rejeitada.get("minhaSolicitacaoPresenca")).get("id").toString());
        mvc.perform(post(c.url()+"/solicitacoes-presenca/"+rejeitadaId+"/rejeitar")
                .with(jwt().jwt(j -> j.subject(c.ana().toString())))).andExpect(status().isOk());
        mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isConflict());
        declarar(c, c.ana(), "NAO_PRESENTE");
        mvc.perform(post(c.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(c.bia().toString())))).andExpect(status().isOk());
        assertThat(registro(c,c.bia())).containsEntry("presenca_operacional", "PRESENTE");

        var cancelavel = contexto(true);
        var pendente = json(mvc.perform(post(cancelavel.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(cancelavel.bia().toString())))).andExpect(status().isOk()).andReturn());
        var pendenteId = UUID.fromString(((Map<?,?>) pendente.get("minhaSolicitacaoPresenca")).get("id").toString());
        mvc.perform(post(cancelavel.url()+"/minha-presenca/solicitacoes/"+pendenteId+"/cancelar")
                .with(jwt().jwt(j -> j.subject(cancelavel.bia().toString())))).andExpect(status().isOk());
        var nova = json(mvc.perform(post(cancelavel.url()+"/minha-presenca/solicitacoes")
                .with(jwt().jwt(j -> j.subject(cancelavel.bia().toString())))).andExpect(status().isOk()).andReturn());
        assertThat(((Map<?,?>) nova.get("minhaSolicitacaoPresenca")).get("id")).isNotEqualTo(pendenteId.toString());
    }

    @Test void duasEntradasNoZeroPresentesGeramUmResponsavelEUmaSolicitacao() throws Exception {
        var c = contexto(true);
        declarar(c, c.ana(), "NAO_PRESENTE");
        var prontas = new CountDownLatch(2); var iniciar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var ana = executor.submit(() -> { prontas.countDown(); aguardar(iniciar); fluxoPresenca.solicitarEntrada(c.ana(),c.familia(),c.lista()); });
            var bia = executor.submit(() -> { prontas.countDown(); aguardar(iniciar); fluxoPresenca.solicitarEntrada(c.bia(),c.familia(),c.lista()); });
            assertThat(prontas.await(10, TimeUnit.SECONDS)).isTrue(); iniciar.countDown();
            ana.get(15, TimeUnit.SECONDS); bia.get(15, TimeUnit.SECONDS);
        } finally { iniciar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
        assertThat(jdbc.queryForObject("select count(*) from participante_compra where compra_id=? and presenca_operacional='PRESENTE'", Integer.class,c.compra())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from compra where id=? and responsavel_operacional_id is not null", Integer.class,c.compra())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from solicitacao_presenca_compra where compra_id=? and estado='PENDENTE'", Integer.class,c.compra())).isEqualTo(1);
    }

    void operar(C c, String operacao) {
        switch (operacao) {
            case "PRESENTE", "NAO_PRESENTE" -> presenca.executar(c.ana(),c.familia(),c.lista(),PresencaOperacional.valueOf(operacao));
            case "COLOCAR" -> colocar.executar(c.ana(),c.familia(),c.lista(),c.item());
            case "RESTAURAR" -> restaurar.executar(c.ana(),c.familia(),c.lista(),c.item());
            case "FINALIZAR" -> { presenca.executar(c.ana(),c.familia(),c.lista(),PresencaOperacional.PRESENTE); finalizar.executar(c.ana(),c.familia(),c.lista()); }
            default -> throw new IllegalArgumentException(operacao);
        }
    }

    void aguardar(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout de concorrencia"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @ParameterizedTest
    @CsvSource({"PRESENTE,PRESENTE,false", "PRESENTE,NAO_PRESENTE,false", "NAO_PRESENTE,PRESENTE,false",
        "NAO_PRESENTE,COLOCAR,true", "COLOCAR,NAO_PRESENTE,false", "NAO_PRESENTE,RESTAURAR,true",
        "RESTAURAR,NAO_PRESENTE,false", "FINALIZAR,NAO_PRESENTE,true", "NAO_PRESENTE,FINALIZAR,false"})
    void serializaPresencaComOperacoesReais(String primeira, String segunda, boolean conflito) throws Exception {
        var c=contexto(true);
        if (primeira.equals("RESTAURAR") || segunda.equals("RESTAURAR")) {
            operar(c,"COLOCAR"); solicitar.executar(c.ana(),c.familia(),c.lista(),c.item());
        }
        if (primeira.equals("PRESENTE")) operar(c,"NAO_PRESENTE");
        var liberar=new CountDownLatch(1); var pronta=new CompletableFuture<Integer>(); var segundaPid=new CompletableFuture<Integer>();
        var primeiraPresenca=new CompletableFuture<Map<String,Object>>();
        var tx=new TransactionTemplate(transactions); var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> tx.executeWithoutResult(s -> {
                operar(c,primeira);
                entityManager.flush();
                primeiraPresenca.complete(registro(c,c.ana()));
                pronta.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                try { if(!liberar.await(30,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout primeira transacao"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            int pidA=pronta.get(15,TimeUnit.SECONDS);
            var primeiraGravacao=primeiraPresenca.get(10,TimeUnit.SECONDS);
            var b=pool.submit(() -> tx.executeWithoutResult(s -> {
                jdbc.execute("set local lock_timeout='25s'");
                segundaPid.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                operar(c,segunda);
            }));
            int pidB=segundaPid.get(10,TimeUnit.SECONDS); boolean bloqueada=false;
            long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<limite) {
                bloqueada=Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,pidA,pidB));
                if(bloqueada) break;
                if(b.isDone()) { b.get(); throw new AssertionError("Segunda operacao nao aguardou lock"); }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(bloqueada).isTrue(); liberar.countDown(); a.get(10,TimeUnit.SECONDS);
            if(conflito) {
                var tipo=primeira.equals("FINALIZAR") ? CompraForaDeAndamentoException.class : PresencaOperacionalObrigatoriaException.class;
                assertThatThrownBy(() -> b.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(tipo);
            } else b.get(10,TimeUnit.SECONDS);
            if (primeira.equals("PRESENTE") && segunda.equals("PRESENTE")) {
                var depois=registro(c,c.ana());
                assertThat(depois.get("presenca_operacional")).isEqualTo("PRESENTE");
                assertThat(depois.get("presenca_alterada_em")).isEqualTo(primeiraGravacao.get("presenca_alterada_em"));
            }
            if(segunda.equals("NAO_PRESENTE") && !conflito) assertThat(registro(c,c.ana())).containsEntry("presenca_operacional","NAO_PRESENTE");
            if(segunda.equals("PRESENTE")) assertThat(registro(c,c.ana())).containsEntry("presenca_operacional","PRESENTE");
            if(primeira.equals("FINALIZAR") || segunda.equals("FINALIZAR"))
                assertThat(jdbc.queryForObject("select status from compra where id=?",String.class,c.compra())).isEqualTo("FINALIZADA");
            if(primeira.equals("COLOCAR") || primeira.equals("RESTAURAR")) {
                assertThat(jdbc.queryForMap("select status,marcado_por_membro_familia_id from item_compra where id=?",c.item()))
                    .containsEntry("status","NO_CARRINHO").containsEntry("marcado_por_membro_familia_id",registro(c,c.ana()).get("membro_familia_id"));
            }
        } finally { liberar.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void doisIniciosMarcamSomenteOIniciadorEfetivo() throws Exception {
        var c=contexto(false); var liberar=new CountDownLatch(1); var prontas=new CountDownLatch(2); var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> { prontas.countDown(); if(!liberar.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return iniciar.iniciar(c.ana(),c.familia(),c.lista()); });
            var b=pool.submit(() -> { prontas.countDown(); if(!liberar.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return iniciar.iniciar(c.bia(),c.familia(),c.lista()); });
            assertThat(prontas.await(10,TimeUnit.SECONDS)).isTrue(); liberar.countDown();
            var ra=a.get(15,TimeUnit.SECONDS); var rb=b.get(15,TimeUnit.SECONDS);
            assertThat(ra.criada()).isNotEqualTo(rb.criada());
            assertThat(ra.compra().getId()).isEqualTo(rb.compra().getId());
            var presentes=jdbc.queryForList("select m.usuario_id from participante_compra p join membro_familia m on m.id=p.membro_familia_id where p.compra_id=? and p.presenca_operacional='PRESENTE'",UUID.class,ra.compra().getId());
            assertThat(presentes).containsExactly(ra.criada()?c.ana():c.bia());
        } finally { liberar.countDown(); pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }

    @Test void migraV9SemInventarPresencaEValidaConstraints() {
        var andamento=contexto(true); var encerrada=contexto(true);
        finalizar.executar(encerrada.ana(),encerrada.familia(),encerrada.lista());
        String schema="presenca_"+UUID.randomUUID().toString().replace("-","");
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).schemas(schema).defaultSchema(schema).target("9").load();
        flyway.migrate();
        // Copia dados persistidos em um schema isolado ainda em V9, sem as novas colunas.
        for(var tabela:List.of("usuario","familia","membro_familia","lista_compra","participante_lista"))
            jdbc.execute("insert into "+schema+"."+tabela+" select * from public."+tabela);
        // A FK do finalizador referencia participante: inserir Compra, participantes e entao finalizacao.
        jdbc.execute("insert into "+schema+".compra (id,lista_compra_id,iniciada_por_membro_familia_id,nome_lista_snapshot,categoria_snapshot,estabelecimento_snapshot,status,iniciada_em) select id,lista_compra_id,iniciada_por_membro_familia_id,nome_lista_snapshot,categoria_snapshot,estabelecimento_snapshot,'EM_ANDAMENTO',iniciada_em from public.compra");
        String colunas="id,compra_id,participante_lista_origem_id,membro_familia_id,nome_snapshot,papel_snapshot,gerado_em";
        jdbc.execute("insert into "+schema+".participante_compra ("+colunas+") select "+colunas+" from public.participante_compra");
        jdbc.execute("update "+schema+".compra c set status=p.status,finalizada_em=p.finalizada_em,finalizada_por_participante_compra_id=p.finalizada_por_participante_compra_id from public.compra p where c.id=p.id");
        org.flywaydb.core.Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).schemas(schema).defaultSchema(schema).target("10").load().migrate();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".participante_compra where presenca_operacional<>'NAO_INFORMADA' or presenca_alterada_em is not null",Integer.class)).isZero();
        assertThat(jdbc.queryForList("select status from "+schema+".compra where id in (?,?)",String.class,andamento.compra(),encerrada.compra())).containsExactlyInAnyOrder("EM_ANDAMENTO","FINALIZADA");
        for(String atribuicao:List.of("presenca_operacional='PRESENTE'", "presenca_operacional='NAO_PRESENTE'", "presenca_alterada_em=now()", "presenca_operacional='ONLINE'", "presenca_operacional=null"))
            assertThatThrownBy(() -> jdbc.execute("update "+schema+".participante_compra set "+atribuicao)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID presenteLegado=jdbc.queryForObject("select id from "+schema+".participante_compra where compra_id=? order by id limit 1",UUID.class,andamento.compra());
        jdbc.update("update "+schema+".participante_compra set presenca_operacional='PRESENTE',presenca_alterada_em=now() where id=?",presenteLegado);
        org.flywaydb.core.Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).schemas(schema).defaultSchema(schema).load().migrate();
        assertThat(jdbc.queryForObject("select responsavel_operacional_id from "+schema+".compra where id=?",UUID.class,andamento.compra())).isEqualTo(presenteLegado);
        assertThat(jdbc.queryForObject("select responsavel_operacional_id from "+schema+".compra where id=?",UUID.class,encerrada.compra())).isNull();
        jdbc.execute("update "+schema+".participante_compra set presenca_operacional='PRESENTE',presenca_alterada_em=now()");
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".participante_compra where presenca_operacional='PRESENTE'",Integer.class)).isPositive();
    }
}
