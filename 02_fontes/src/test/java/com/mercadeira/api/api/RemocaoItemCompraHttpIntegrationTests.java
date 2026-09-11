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
class RemocaoItemCompraHttpIntegrationTests {
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

    @Test
    void solicitaReplayEGetPreservamSnapshotsECapabilitiesPorUsuario() throws Exception {
        var c = contexto();
        colocar(c);
        jdbc.update("update usuario set nome = 'Cadastro alterado' where id in (?, ?)", c.responsavelId(), c.outroId());
        var solicitacao = acao(c, "solicitar", c.outro(), 200);
        assertThat(solicitacao.get("status")).isEqualTo("REMOCAO_SOLICITADA");
        var remocao = objeto(solicitacao, "remocao");
        assertAutor(objeto(remocao, "solicitadaPor"), c.outroMembro(), c.outroId(), "Bia");
        assertThat(remocao.get("solicitadaEm")).isNotNull();
        assertThat(remocao.get("decisao")).isNull();
        assertThat(remocao.get("decididaPor")).isNull();
        assertThat(remocao.get("decididaEm")).isNull();
        acoes(solicitacao, false, false);
        assertThat(getItem(c, c.outro())).isEqualTo(solicitacao);
        acoes(getItem(c, c.responsavel()), false, true);
        var replay = acao(c, "solicitar", c.responsavel(), 200);
        assertThat(replay.get("remocao")).isEqualTo(remocao);
        assertThat(replay.get("status")).isEqualTo("REMOCAO_SOLICITADA");
        acoes(replay, false, true);
        var aprovado = acao(c, "aprovar", c.responsavel(), 200);
        assertAutor(objeto(objeto(aprovado, "remocao"), "decididaPor"), c.responsavelMembro(), c.responsavelId(), "Ana");
    }

    @Test
    void autoaprovacaoRetornaRemovidoSemAcoesEPreservaItemNoGet() throws Exception {
        var c = contexto();
        colocar(c);
        var item = acao(c, "solicitar", c.responsavel(), 200);
        assertThat(item.get("status")).isEqualTo("REMOVIDO");
        var remocao = objeto(item, "remocao");
        assertThat(remocao.get("decisao")).isEqualTo("APROVADA");
        assertAutor(objeto(remocao, "solicitadaPor"), c.responsavelMembro(), c.responsavelId(), "Ana");
        assertThat(remocao.get("decididaPor")).isEqualTo(remocao.get("solicitadaPor"));
        assertThat(remocao.get("solicitadaEm")).isNotNull();
        assertThat(remocao.get("decididaEm")).isEqualTo(remocao.get("solicitadaEm"));
        acoes(item, false, false);
        assertThat(getItem(c, c.responsavel())).isEqualTo(item);
        assertThat(acao(c, "aprovar", c.responsavel(), 200)).isEqualTo(item);
    }

    @ParameterizedTest
    @CsvSource({"aprovar,REMOVIDO,APROVADA,false", "rejeitar,NO_CARRINHO,REJEITADA,true"})
    void decideReplayEGetRetornamMesmoItem(String acao, String estado, String decisao, boolean podeSolicitar) throws Exception {
        var c = contexto();
        var carrinho = colocar(c);
        var solicitada = acao(c, "solicitar", c.outro(), 200);
        var item = acao(c, acao, c.responsavel(), 200);
        assertThat(item.get("status")).isEqualTo(estado);
        var remocao = objeto(item, "remocao");
        assertThat(remocao.get("decisao")).isEqualTo(decisao);
        assertThat(remocao.get("solicitadaPor")).isEqualTo(objeto(solicitada, "remocao").get("solicitadaPor"));
        assertThat(remocao.get("solicitadaEm")).isEqualTo(objeto(solicitada, "remocao").get("solicitadaEm"));
        assertAutor(objeto(remocao, "decididaPor"), c.responsavelMembro(), c.responsavelId(), "Ana");
        assertThat(remocao.get("decididaEm")).isNotNull();
        assertThat(item.get("colocadoNoCarrinhoPor")).isEqualTo(carrinho.get("colocadoNoCarrinhoPor"));
        assertThat(item.get("colocadoNoCarrinhoEm")).isEqualTo(carrinho.get("colocadoNoCarrinhoEm"));
        acoes(item, podeSolicitar, false);
        assertThat(acao(c, acao, c.responsavel(), 200)).isEqualTo(item);
        assertThat(getItem(c, c.responsavel())).isEqualTo(item);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/compra-2e-" + decisao + ".json"),
                mvc.perform(get(c.url()).header("Authorization", c.responsavel())).andReturn().getResponse().getContentAsString());
    }

    @Test
    void novoCicloSubstituiAuditoriaRejeitadaEGetRecuperaCicloAtual() throws Exception {
        var c = contexto();
        colocar(c);
        acao(c, "solicitar", c.outro(), 200);
        var rejeitado = acao(c, "rejeitar", c.responsavel(), 200);
        assertThat(getItem(c, c.responsavel())).isEqualTo(rejeitado);
        // Novo participante ja esta no snapshot desde o inicio.
        var novo = acao(c, "solicitar", c.terceiro(), 200);
        var remocao = objeto(novo, "remocao");
        assertThat(novo.get("status")).isEqualTo("REMOCAO_SOLICITADA");
        assertThat(objeto(remocao, "solicitadaPor").get("nome")).isEqualTo("Caio");
        assertThat(remocao.get("solicitadaEm")).isNotEqualTo(objeto(rejeitado, "remocao").get("solicitadaEm"));
        assertThat(remocao.get("decisao")).isNull();
        assertThat(remocao.get("decididaPor")).isNull();
        assertThat(remocao.get("decididaEm")).isNull();
        assertThat(getItem(c, c.terceiro())).isEqualTo(novo);
        acoes(getItem(c, c.responsavel()), false, true);
    }

    @ParameterizedTest
    @CsvSource({"solicitar,false", "solicitar,true", "aprovar,false", "aprovar,true", "rejeitar,false", "rejeitar,true"})
    void observadorNaoMutaMesmoAdministrador(String acao, boolean administrador) throws Exception {
        var c = contexto();
        colocar(c);
        acao(c, "solicitar", c.outro(), 200);
        String observador = observador(c, administrador);
        acoes(getItem(c, observador), false, false);
        acao(c, acao, observador, 403);
    }

    @ParameterizedTest
    @CsvSource({"aprovar,false", "aprovar,true", "rejeitar,false", "rejeitar,true"})
    void outroParticipanteNaoDecideMesmoAdministrador(String acao, boolean administrador) throws Exception {
        var c = contexto();
        colocar(c);
        acao(c, "solicitar", c.outro(), 200);
        if (administrador) jdbc.update("update membro_familia set papel = 'ADMINISTRADOR' where id = ?", c.outroMembro());
        acao(c, acao, c.outro(), 403);
        acoes(getItem(c, c.outro()), false, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"solicitar", "aprovar", "rejeitar"})
    void exigeJwtERejeitaContextosInvalidos(String acao) throws Exception {
        var c = contexto();
        String endpoint = c.url() + "/itens/" + c.itemId() + "/" + acao + "-remocao";
        mvc.perform(post(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
        var outra = contexto();
        mvc.perform(post(c.url() + "/itens/" + outra.itemId() + "/" + acao + "-remocao")
                .header("Authorization", c.responsavel())).andExpect(status().isNotFound());
        mvc.perform(post(endpoint.replace(c.familiaId().toString(), outra.familiaId().toString()))
                .header("Authorization", c.responsavel())).andExpect(status().isNotFound());
        mvc.perform(post(c.url() + "/itens/" + UUID.randomUUID() + "/" + acao + "-remocao")
                .header("Authorization", c.responsavel())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"solicitar", "aprovar", "rejeitar"})
    void compraFinalizadaRetornaConflitoECapabilitiesFalsas(String acao) throws Exception {
        var c = contexto();
        colocar(c);
        acao(c, "solicitar", c.outro(), 200);
        jdbc.update("""
                update compra set status = 'FINALIZADA', finalizada_em = CURRENT_TIMESTAMP,
                    finalizada_por_participante_compra_id = (
                        select pc.id from participante_compra pc
                        where pc.compra_id = compra.id order by pc.id limit 1)
                where lista_compra_id = ?
                """, c.listaId());
        acoes(getItem(c, c.responsavel()), false, false);
        acao(c, acao, c.responsavel(), 409);
    }

    @ParameterizedTest
    @CsvSource({"solicitar,PENDENTE", "solicitar,REMOVIDO", "aprovar,PENDENTE", "aprovar,NO_CARRINHO",
        "aprovar,REJEITADA", "rejeitar,PENDENTE", "rejeitar,NO_CARRINHO", "rejeitar,REMOVIDO"})
    void estadosIncompativeisRetornam409(String acao, String estado) throws Exception {
        var c = contexto();
        if (!estado.equals("PENDENTE")) colocar(c);
        if (estado.equals("REMOVIDO")) acao(c, "solicitar", c.responsavel(), 200);
        if (estado.equals("REJEITADA")) {
            acao(c, "solicitar", c.outro(), 200);
            acao(c, "rejeitar", c.responsavel(), 200);
        }
        acao(c, acao, c.responsavel(), 409);
    }

    @Test
    void regressaoPreparacaoInclusaoColocacaoEReplay() throws Exception {
        var c = contexto();
        var original = getItem(c, c.responsavel());
        assertThat(original.get("itemListaOrigemId")).isNotNull();
        assertThat(original.get("adicionadoPor")).isNull();
        assertThat(original.get("remocao")).isNull();
        acoes(original, false, false);
        var novo = json(mvc.perform(post(c.url()+"/itens").header("Authorization",c.outro())
                .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Leite\",\"quantidade\":1,\"unidadeMedida\":\"UNIDADE\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(novo.get("itemListaOrigemId")).isNull();
        assertThat(novo.get("adicionadoDuranteCompra")).isEqualTo(true);
        assertThat(objeto(novo,"adicionadoPor").get("nome")).isEqualTo("Bia");
        assertThat(novo.get("remocao")).isNull();
        acoes(novo,false,false);
        var outroItem = new Contexto(c.url(), novo.get("id").toString(), c.responsavel(),c.outro(),c.terceiro(),
                c.familiaId(),c.listaId(),c.responsavelId(),c.outroId(),c.responsavelMembro(),c.outroMembro());
        assertThat(getItem(outroItem,c.outro())).isEqualTo(novo);
        var colocado = colocar(outroItem);
        acoes(colocado,true,false);
        assertThat(colocar(outroItem)).isEqualTo(colocado);
        assertThat(getItem(outroItem,c.responsavel())).isEqualTo(colocado);
        var removido = acao(outroItem,"solicitar",c.responsavel(),200);
        assertThat(removido.get("adicionadoPor")).isEqualTo(novo.get("adicionadoPor"));
        assertThat(removido.get("adicionadoEm")).isEqualTo(novo.get("adicionadoEm"));
        assertThat(getItem(outroItem,c.responsavel())).isEqualTo(removido);
    }

    @Test
    void decisaoConcorrenteTemUm200EUm409() throws Exception {
        var c = contexto();
        colocar(c);
        var solicitacao = acao(c,"solicitar",c.outro(),200).get("remocao");
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch prontas=new CountDownLatch(2), inicio=new CountDownLatch(1);
        try {
            Future<MvcResult> aprovar=executor.submit(()->concorrente(c,"aprovar",prontas,inicio));
            Future<MvcResult> rejeitar=executor.submit(()->concorrente(c,"rejeitar",prontas,inicio));
            assertThat(prontas.await(10,TimeUnit.SECONDS)).isTrue();
            inicio.countDown();
            var a=aprovar.get(20,TimeUnit.SECONDS); var r=rejeitar.get(20,TimeUnit.SECONDS);
            assertThat(List.of(a.getResponse().getStatus(),r.getResponse().getStatus())).containsExactlyInAnyOrder(200,409);
            var vencedor=json(a.getResponse().getStatus()==200?a:r);
            var perdedor=json(a.getResponse().getStatus()==409?a:r);
            assertThat(perdedor.get("erro")).isEqualTo("CONFLITO_DE_ESTADO");
            assertThat(getItem(c,c.responsavel())).isEqualTo(vencedor);
            assertThat(objeto(vencedor,"remocao").get("solicitadaPor")).isEqualTo(((Map<?,?>)solicitacao).get("solicitadaPor"));
            assertThat(objeto(vencedor,"remocao").get("solicitadaEm")).isEqualTo(((Map<?,?>)solicitacao).get("solicitadaEm"));
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }

    private MvcResult concorrente(Contexto c,String acao,CountDownLatch prontas,CountDownLatch inicio) throws Exception {
        prontas.countDown();
        if(!inicio.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout");
        return mvc.perform(post(c.url()+"/itens/"+c.itemId()+"/"+acao+"-remocao").header("Authorization",c.responsavel())).andReturn();
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
