package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.lista.application.CriarListaCompra;
import com.mercadeira.api.lista.application.AdicionarItemLista;
import com.mercadeira.api.lista.application.AdicionarParticipanteLista;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// Sem @Transactional: cada request atravessa as fronteiras reais de transacao.
@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class FinalizarCompraIntegrationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void jwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }

    @Autowired WebApplicationContext context;
    @Autowired CadastrarUsuario cadastrar;
    @Autowired AutenticarUsuario autenticar;
    @Autowired CriarFamilia criarFamilia;
    @Autowired CriarListaCompra criarLista;
    @Autowired AdicionarItemLista adicionarItem;
    @Autowired AdicionarParticipanteLista adicionarParticipante;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }


    @Autowired com.mercadeira.api.compra.application.FinalizarCompra finalizarCompra;
    @Autowired com.mercadeira.api.compra.application.AdicionarItemDuranteCompra incluir;
    @Autowired com.mercadeira.api.compra.application.ColocarItemNoCarrinho carrinho;
    @Autowired com.mercadeira.api.compra.application.SolicitarRemocaoItemCompra solicitar;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void finalizaPendenteReplayDeOutroParticipanteEGetPreservamSnapshot() throws Exception {
        var c = contexto();
        var antes = consultar(c, c.outro());
        assertThat(antes).containsEntry("finalizadaPor", null).containsEntry("finalizadaEm", null);
        assertThat(objeto(antes, "contextoUsuario")).containsEntry("podeFinalizarCompra", true);
        var listaAntes = jdbc.queryForObject("select atualizada_em from lista_compra where id=?", java.sql.Timestamp.class,c.listaId());
        jdbc.update("update usuario set nome='Nome novo' where id=?", c.outroId());
        var fim = finalizar(c, c.outro(), 200);
        assertThat(fim.get("status")).isEqualTo("FINALIZADA");
        assertAutor(objeto(fim,"finalizadaPor"),c.outroMembro(),c.outroId(),"Bia");
        assertThat(fim.get("finalizadaEm")).isNotNull();
        assertThat(fim.get("participantes")).isEqualTo(antes.get("participantes"));
        assertThat(getItem(c,c.outro()).get("status")).isEqualTo("PENDENTE");
        assertThat(objeto(fim,"contextoUsuario")).containsEntry("participanteCompra",true).containsEntry("podeFinalizarCompra",false);
        assertThat(listaStatus(c)).isEqualTo("FINALIZADA");
        var instante = jdbc.queryForObject("select atualizada_em from lista_compra where id=?",java.sql.Timestamp.class,c.listaId());
        assertThat(instante.toInstant()).isEqualTo(java.time.Instant.parse((String)fim.get("finalizadaEm")));
        assertThat(instante).isAfterOrEqualTo(listaAntes);
        assertThat(finalizar(c,c.outro(),200)).isEqualTo(fim);
        var replayResponsavel=finalizar(c,c.responsavel(),200);
        assertThat(replayResponsavel).containsEntry("status","FINALIZADA")
                .containsEntry("finalizadaPor",fim.get("finalizadaPor"))
                .containsEntry("finalizadaEm",fim.get("finalizadaEm"))
                .containsEntry("participantes",fim.get("participantes"));
        var consultaResponsavel=consultar(c,c.responsavel());
        assertThat(consultaResponsavel).containsEntry("status","FINALIZADA")
                .containsEntry("finalizadaPor",fim.get("finalizadaPor"))
                .containsEntry("finalizadaEm",fim.get("finalizadaEm"))
                .containsEntry("participantes",fim.get("participantes"));
        assertThat(objeto(consultaResponsavel,"contextoUsuario")).containsEntry("podeFinalizarCompra",false)
                .containsEntry("podeReassumirResponsabilidade",false);
        assertThat(jdbc.queryForObject("select atualizada_em from lista_compra where id=?",java.sql.Timestamp.class,c.listaId())).isEqualTo(instante);
        acoes(getItem(c,c.responsavel()),false,false);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/compra-3-finalizada.json"),
                mvc.perform(get(c.url()).header("Authorization",c.outro())).andReturn().getResponse().getContentAsString());
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void removidosPermanecemNoGetComAuditoria(boolean somenteRemovidos) throws Exception {
        var c=contexto();
        colocar(c);
        var removido=acao(c,"solicitar",c.responsavel(),200);
        if(!somenteRemovidos) {
            var novo=json(mvc.perform(post(c.url()+"/itens").header("Authorization",c.outro()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descricao\":\"Leite\"}")).andExpect(status().isCreated()).andReturn());
            mvc.perform(post(c.url()+"/itens/"+novo.get("id")+"/colocar-no-carrinho").header("Authorization",c.outro()))
                    .andExpect(status().isOk());
        }
        var fim=finalizar(c,c.outro(),200);
        assertThat(((List<?>)fim.get("itens")).size()).isEqualTo(somenteRemovidos?1:2);
        assertThat(objeto(removido, "acoes")).containsEntry("podeRestaurarNoCarrinho", true);
        var esperado = new java.util.HashMap<>(removido);
        esperado.put("acoes", Map.of("podeColocarNoCarrinho", false, "podeSolicitarRemocao", false,
                "podeDecidirRemocao", false, "podeRestaurarNoCarrinho", false));
        assertThat(getItem(c,c.outro())).isEqualTo(esperado);
        assertThat(consultar(c,c.outro())).isEqualTo(fim);
    }

    @Test
    void remocaoPendenteBloqueiaSemAlterarCompraListaOuItens() throws Exception {
        var c=contexto(); colocar(c); acao(c,"solicitar",c.outro(),200);
        var antes=consultar(c,c.responsavel());
        var listaAntes=jdbc.queryForMap("select * from lista_compra where id=?",c.listaId());
        assertThat(objeto(antes,"contextoUsuario")).containsEntry("podeFinalizarCompra",false);
        assertThat(finalizar(c,c.responsavel(),409)).containsEntry("erro","CONFLITO_DE_ESTADO");
        assertThat(consultar(c,c.responsavel())).isEqualTo(antes);
        assertThat(jdbc.queryForMap("select * from lista_compra where id=?",c.listaId())).isEqualTo(listaAntes);
        assertThat(listaStatus(c)).isEqualTo("EM_COMPRA");
        acao(c,"rejeitar",c.responsavel(),200);
        assertThat(objeto(consultar(c,c.responsavel()),"contextoUsuario")).containsEntry("podeFinalizarCompra",true);
        finalizar(c,c.responsavel(),200);
    }

    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void observadorMesmoAdministradorNaoFinalizaNemFazReplay(boolean admin) throws Exception {
        var c=contexto(); var token=observador(c,admin);
        assertThat(objeto(consultar(c,token),"contextoUsuario")).containsEntry("podeFinalizarCompra",false);
        finalizar(c,token,403);
        finalizar(c,c.outro(),200);
        finalizar(c,token,403);
    }

    @Test
    void membroInativoNaoFinalizaNemFazReplay() throws Exception {
        var c=contexto();
        jdbc.update("update membro_familia set status='INATIVO' where id=?",c.outroMembro());
        finalizar(c,c.outro(),403);
        finalizar(c,c.responsavel(),200);
        finalizar(c,c.outro(),403);
    }

    @Test
    void jwtEContextosInvalidosPreservamCodigos() throws Exception {
        var c=contexto();
        mvc.perform(post(c.url()+"/finalizar")).andExpect(status().isUnauthorized());
        mvc.perform(post(c.url()+"/finalizar").header("Authorization","Bearer invalido")).andExpect(status().isUnauthorized());
        mvc.perform(post(c.url().replace(c.familiaId().toString(),UUID.randomUUID().toString())+"/finalizar")
                .header("Authorization",c.responsavel())).andExpect(status().isNotFound());
        mvc.perform(post(c.url().replace(c.listaId().toString(),UUID.randomUUID().toString())+"/finalizar")
                .header("Authorization",c.responsavel())).andExpect(status().isNotFound());
        var lista=criarLista.criar(c.responsavelId(),c.familiaId(),"Sem compra",CategoriaCompra.OUTROS,null);
        mvc.perform(post(c.url().replace(c.listaId().toString(),lista.getId().toString())+"/finalizar")
                .header("Authorization",c.responsavel())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"EM_ANDAMENTO,FINALIZADA","FINALIZADA,EM_COMPRA","CANCELADA,EM_COMPRA",
            "EM_ANDAMENTO,CANCELADA","EM_ANDAMENTO,EM_PREPARACAO","FINALIZADA,CANCELADA"})
    void estadosInconsistentesNaoSaoReplay(String compra,String lista) throws Exception {
        var c=contexto();
        if(compra.equals("FINALIZADA")) finalizar(c,c.outro(),200);
        else jdbc.update("update compra set status=? where lista_compra_id=?",compra,c.listaId());
        jdbc.update("update lista_compra set status=? where id=?",lista,c.listaId());
        var antes=consultar(c,c.responsavel());
        var listaAntes=jdbc.queryForMap("select * from lista_compra where id=?",c.listaId());
        assertThat(objeto(antes,"contextoUsuario")).containsEntry("podeFinalizarCompra",false);
        finalizar(c,c.responsavel(),409);
        assertThat(consultar(c,c.responsavel())).isEqualTo(antes);
        assertThat(jdbc.queryForMap("select * from lista_compra where id=?",c.listaId())).isEqualTo(listaAntes);
    }

    @Test
    void compraVaziaEhConflito() throws Exception {
        var c=contexto();
        jdbc.update("delete from item_compra where id=?",UUID.fromString(c.itemId()));
        var antes=consultar(c,c.responsavel());
        assertThat(objeto(antes,"contextoUsuario")).containsEntry("podeFinalizarCompra",false);
        finalizar(c,c.responsavel(),409);
        assertThat(consultar(c,c.responsavel())).isEqualTo(antes);
        assertThat(listaStatus(c)).isEqualTo("EM_COMPRA");
    }

    @ParameterizedTest
    @ValueSource(strings={"colocar-no-carrinho","solicitar-remocao","aprovar-remocao","rejeitar-remocao","adicionar"})
    void mutacoesAposFinalizacaoSaoConflito(String acao) throws Exception {
        var c=contexto(); var fim=finalizar(c,c.responsavel(),200);
        if(acao.equals("adicionar")) {
            mvc.perform(post(c.url()+"/itens").header("Authorization",c.outro()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"descricao\":\"Leite\"}")).andExpect(status().isConflict());
        } else {
            mvc.perform(post(c.url()+"/itens/"+c.itemId()+"/"+acao).header("Authorization",c.outro()))
                    .andExpect(status().isConflict());
        }
        assertThat(consultar(c,c.responsavel())).isEqualTo(fim);
    }

    @Test
    void falhaAoGravarListaReverteTambemFinalizacaoDaCompra() throws Exception {
        var c=contexto(); var antes=consultar(c,c.responsavel());
        // Trigger restrito ao fixture: simula falha no segundo agregado durante flush/commit.
        jdbc.execute("create function falha_lista_compra3() returns trigger language plpgsql as $$ begin "
                +"if NEW.id = '"+c.listaId()+"'::uuid and NEW.status = 'FINALIZADA' then raise exception 'falha de teste'; end if; return NEW; end $$");
        jdbc.execute("create trigger teste_falha_lista before update on lista_compra for each row execute function falha_lista_compra3()");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> finalizarCompra.executar(c.responsavelId(),c.familiaId(),c.listaId()))
                    .isInstanceOf(RuntimeException.class).hasStackTraceContaining("falha de teste");
        } finally {
            jdbc.execute("drop trigger teste_falha_lista on lista_compra");
            jdbc.execute("drop function falha_lista_compra3()");
        }
        assertThat(consultar(c,c.responsavel())).isEqualTo(antes);
        assertThat(listaStatus(c)).isEqualTo("EM_COMPRA");
    }

    @Test
    void duasFinalizacoesConcorrentesTemUmaEfetivaEUmReplay() throws Exception {
        var c=contexto(); var executor=Executors.newFixedThreadPool(2);
        var prontas=new CountDownLatch(2); var liberar=new CountDownLatch(1);
        try {
            var a=executor.submit(() -> finalizarConcorrente(c,c.responsavelId(),prontas,liberar));
            var b=executor.submit(() -> finalizarConcorrente(c,c.outroId(),prontas,liberar));
            assertThat(prontas.await(10,TimeUnit.SECONDS)).isTrue(); liberar.countDown();
            var ra=a.get(20,TimeUnit.SECONDS); var rb=b.get(20,TimeUnit.SECONDS);
            assertThat(List.of(ra.finalizadaAgora(),rb.finalizadaAgora())).containsExactlyInAnyOrder(true,false);
            assertThat(ra.compra().getFinalizadaEm()).isEqualTo(rb.compra().getFinalizadaEm());
            assertThat(ra.compra().getFinalizadaPorParticipanteCompra().getId()).isEqualTo(rb.compra().getFinalizadaPorParticipanteCompra().getId());
            var fim=consultar(c,c.responsavel());
            assertThat(objeto(fim,"finalizadaPor").get("usuarioId")).isEqualTo((ra.finalizadaAgora()?c.responsavelId():c.outroId()).toString());
            assertThat(listaStatus(c)).isEqualTo("FINALIZADA");
        } finally { liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }

    @ParameterizedTest
    @CsvSource({"adicionar,true","adicionar,false","carrinho,true","carrinho,false","solicitar,true","solicitar,false"})
    void finalizacaoEMutacaoSerializamEmAmbasAsOrdens(String mutacao, boolean finalizarPrimeiro) throws Exception {
        var c=contexto(); if(mutacao.equals("solicitar")) colocar(c);
        var transacao=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var executor=Executors.newFixedThreadPool(2);
        var liberar=new CountDownLatch(1);
        var pronta=new CompletableFuture<Integer>(); var segundaPid=new CompletableFuture<Integer>();
        try {
            var primeira=executor.submit(() -> transacao.executeWithoutResult(tx -> {
                jdbc.execute("set local lock_timeout='30s'");
                if(finalizarPrimeiro) finalizarCompra.executar(c.responsavelId(),c.familiaId(),c.listaId());
                else mutar(c,mutacao);
                pronta.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                try { if(!liberar.await(40,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            int pidA=pronta.get(10,TimeUnit.SECONDS);
            var segunda=executor.submit(() -> transacao.executeWithoutResult(tx -> {
                jdbc.execute("set local lock_timeout='30s'");
                segundaPid.complete(jdbc.queryForObject("select pg_backend_pid()",Integer.class));
                if(finalizarPrimeiro) mutar(c,mutacao);
                else finalizarCompra.executar(c.responsavelId(),c.familiaId(),c.listaId());
            }));
            int pidB=segundaPid.get(10,TimeUnit.SECONDS);
            long limite=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            boolean bloqueada=false;
            while(System.nanoTime()<limite) {
                bloqueada=Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))",Boolean.class,pidA,pidB));
                if(bloqueada) break;
                if(segunda.isDone()) { segunda.get(); throw new AssertionError("Segunda transacao nao aguardou o lock"); }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            assertThat(bloqueada).isTrue();
            liberar.countDown(); primeira.get(10,TimeUnit.SECONDS);
            if(finalizarPrimeiro) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> segunda.get(10,TimeUnit.SECONDS))
                        .hasCauseInstanceOf(com.mercadeira.api.compra.application.CompraForaDeAndamentoException.class);
            } else if(mutacao.equals("solicitar")) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> segunda.get(10,TimeUnit.SECONDS))
                        .hasCauseInstanceOf(com.mercadeira.api.compra.application.CompraComRemocaoPendenteException.class);
            } else segunda.get(10,TimeUnit.SECONDS);
            var fim=consultar(c,c.responsavel());
            boolean finalizada=finalizarPrimeiro || !mutacao.equals("solicitar");
            assertThat(fim.get("status")).isEqualTo(finalizada?"FINALIZADA":"EM_ANDAMENTO");
            assertThat(listaStatus(c)).isEqualTo(finalizada?"FINALIZADA":"EM_COMPRA");
            assertThat(((List<?>)fim.get("itens")).size()).isEqualTo(!finalizarPrimeiro && mutacao.equals("adicionar")?2:1);
            assertThat(getItem(c,c.responsavel()).get("status")).isEqualTo(
                    mutacao.equals("solicitar")?(finalizarPrimeiro?"NO_CARRINHO":"REMOCAO_SOLICITADA"):
                    (!finalizarPrimeiro && mutacao.equals("carrinho")?"NO_CARRINHO":"PENDENTE"));
        } finally { liberar.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }

    private com.mercadeira.api.compra.application.ResultadoFinalizacaoCompra finalizarConcorrente(
            Contexto c,UUID usuario,CountDownLatch prontas,CountDownLatch liberar) throws Exception {
        prontas.countDown(); if(!liberar.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
        return finalizarCompra.executar(usuario,c.familiaId(),c.listaId());
    }

    private void mutar(Contexto c,String acao) {
        if(acao.equals("adicionar")) incluir.executar(c.outroId(),c.familiaId(),c.listaId(),
                new com.mercadeira.api.compra.application.AdicionarItemDuranteCompraCommand("Leite",BigDecimal.ONE,UnidadeMedida.UNIDADE,null,null));
        else if(acao.equals("carrinho")) carrinho.executar(c.outroId(),c.familiaId(),c.listaId(),UUID.fromString(c.itemId()));
        else solicitar.executar(c.outroId(),c.familiaId(),c.listaId(),UUID.fromString(c.itemId()));
    }
    private String listaStatus(Contexto c) { return jdbc.queryForObject("select status from lista_compra where id=?",String.class,c.listaId()); }
    private Map<String,Object> consultar(Contexto c,String token) throws Exception {
        return json(mvc.perform(get(c.url()).header("Authorization",token)).andExpect(status().isOk()).andReturn());
    }
    private Map<String,Object> finalizar(Contexto c,String token,int esperado) throws Exception {
        return json(mvc.perform(post(c.url()+"/finalizar").header("Authorization",token)).andExpect(status().is(esperado)).andReturn());
    }

    private Map<String,Object> acao(Contexto c,String acao,String token,int esperado) throws Exception {
        return json(mvc.perform(post(c.url()+"/itens/"+c.itemId()+"/"+acao+"-remocao").header("Authorization",token))
                .andExpect(status().is(esperado)).andReturn());
    }
    private Map<String,Object> colocar(Contexto c) throws Exception {
        return json(mvc.perform(post(c.url()+"/itens/"+c.itemId()+"/colocar-no-carrinho").header("Authorization",c.responsavel()))
                .andExpect(status().isOk()).andReturn());
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> getItem(Contexto c,String token) throws Exception {
        var compra=json(mvc.perform(get(c.url()).header("Authorization",token)).andExpect(status().isOk()).andReturn());
        return ((List<Map<String,Object>>)compra.get("itens")).stream().filter(i->i.get("id").equals(c.itemId())).findFirst().orElseThrow();
    }
    private Map<String,Object> json(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(),"$");
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> objeto(Map<String,Object> objeto,String campo) { return (Map<String,Object>)objeto.get(campo); }
    private void acoes(Map<String,Object> item,boolean solicitar,boolean decidir) {
        assertThat(objeto(item,"acoes")).containsEntry("podeSolicitarRemocao",solicitar).containsEntry("podeDecidirRemocao",decidir);
    }
    private void assertAutor(Map<String,Object> autor,UUID membro,UUID usuario,String nome) {
        assertThat(autor).containsEntry("membroFamiliaId",membro.toString()).containsEntry("usuarioId",usuario.toString()).containsEntry("nome",nome);
        assertThat(autor.get("participanteCompraId")).isNotNull();
    }
    private Contexto contexto() throws Exception {
        Usuario ana=usuario("Ana"), bia=usuario("Bia"), caio=usuario("Caio");
        var familia=criarFamilia.criar(ana.getId(),"Familia REST");
        UUID biaMembro=membro(familia.getId(),bia,false), caioMembro=membro(familia.getId(),caio,false);
        var lista=criarLista.criar(ana.getId(),familia.getId(),"Lista",CategoriaCompra.OUTROS,null);
        adicionarParticipante.adicionar(ana.getId(),familia.getId(),lista.getId(),biaMembro);
        adicionarParticipante.adicionar(ana.getId(),familia.getId(),lista.getId(),caioMembro);
        adicionarItem.adicionar(ana.getId(),familia.getId(),lista.getId(),"Arroz",BigDecimal.ONE,UnidadeMedida.UNIDADE,null,null);
        String url="/api/familias/"+familia.getId()+"/listas/"+lista.getId()+"/compra";
        String token=bearer(ana);
        var inicio=mvc.perform(post(url).header("Authorization",token)).andExpect(status().isCreated()).andReturn();
        // Estes cenarios de regressao operam com os demais compradores declarados presentes.
        for (var participante : List.of(bia, caio)) {
            var pedido=mvc.perform(post(url + "/minha-presenca/solicitacoes")
                    .header("Authorization", bearer(participante))).andExpect(status().isOk()).andReturn();
            String pedidoId=JsonPath.read(pedido.getResponse().getContentAsString(),"$.minhaSolicitacaoPresenca.id");
            mvc.perform(post(url + "/solicitacoes-presenca/"+pedidoId+"/aprovar")
                    .header("Authorization", token)).andExpect(status().isOk());
        }
        String itemId=JsonPath.read(inicio.getResponse().getContentAsString(),"$.itens[0].id");
        UUID anaMembro=jdbc.queryForObject("select id from membro_familia where familia_id=? and usuario_id=?",UUID.class,familia.getId(),ana.getId());
        return new Contexto(url,itemId,token,bearer(bia),bearer(caio),familia.getId(),lista.getId(),ana.getId(),bia.getId(),anaMembro,biaMembro);
    }
    private String observador(Contexto c,boolean admin) {
        Usuario usuario=usuario("Observador"); membro(c.familiaId(),usuario,admin); return bearer(usuario);
    }
    private UUID membro(UUID familia,Usuario usuario,boolean admin) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into membro_familia(id,familia_id,usuario_id,papel,status,criado_em,atualizado_em) values(?,?,?,?,'ATIVO',now(),now())",
                id,familia,usuario.getId(),admin?"ADMINISTRADOR":"MEMBRO");
        return id;
    }
    private Usuario usuario(String nome) { return cadastrar.cadastrar(nome,UUID.randomUUID()+"@test.local","senha-original"); }
    private String bearer(Usuario usuario) { return "Bearer "+autenticar.autenticar(usuario.getEmail(),"senha-original").token(); }
    private record Contexto(String url,String itemId,String responsavel,String outro,String terceiro,UUID familiaId,UUID listaId,
            UUID responsavelId,UUID outroId,UUID responsavelMembro,UUID outroMembro) {}
}
