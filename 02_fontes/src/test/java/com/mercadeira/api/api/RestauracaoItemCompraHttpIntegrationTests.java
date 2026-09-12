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
import java.nio.file.Files;
import java.nio.file.Path;
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
class RestauracaoItemCompraHttpIntegrationTests {
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void restauraComSnapshotReplayDeOutroParticipanteEGetIdentico(boolean administrador) throws Exception {
        var c = contexto();
        var removido = remover(c);
        acoesRestauracao(removido, false, false, true);
        assertThat(removido).containsKey("restauracao").containsEntry("restauracao", null);
        String autor = administrador ? c.responsavel() : c.terceiro();
        UUID usuario = administrador ? c.responsavelId() : c.terceiroId();
        UUID membro = administrador ? c.responsavelMembro() : c.terceiroMembro();
        String nome = administrador ? "Ana" : "Camila";
        jdbc.update("update usuario set nome = 'Cadastro alterado' where id = ?", usuario);
        var restaurado = restaurar(c, autor, 200);
        assertThat(restaurado.get("status")).isEqualTo("NO_CARRINHO");
        var restauracao = objeto(restaurado, "restauracao");
        assertAutor(objeto(restauracao, "restauradoPor"), membro, usuario, nome);
        assertThat(restauracao.get("restauradoEm")).isNotNull();
        assertThat(restaurado.get("colocadoNoCarrinhoPor")).isEqualTo(restauracao.get("restauradoPor"));
        assertThat(restaurado.get("colocadoNoCarrinhoEm")).isEqualTo(restauracao.get("restauradoEm"));
        assertThat(restaurado.get("remocao")).isEqualTo(removido.get("remocao"));
        acoesRestauracao(restaurado, true, false, false);
        assertThat(getItem(c, autor)).isEqualTo(restaurado);
        assertThat(restaurar(c, c.outro(), 200)).isEqualTo(restaurado);
        assertThat(getItem(c, c.outro())).isEqualTo(restaurado);
        if (!administrador) {
            Files.writeString(Path.of("target/compra-4-restauracao.json"),
                    mvc.perform(get(c.url()).header("Authorization", autor))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"aprovar", "rejeitar"})
    void novoCicloMantemRestauracaoETransfereDecisaoParaCamila(String decisao) throws Exception {
        var c = contexto();
        var ciclo1 = remover(c);
        var restaurado = restaurar(c, c.terceiro(), 200);
        var pendente = acao(c, "solicitar", c.outro(), 200);
        assertThat(pendente.get("status")).isEqualTo("REMOCAO_SOLICITADA");
        assertThat(pendente.get("restauracao")).isEqualTo(restaurado.get("restauracao"));
        assertThat(pendente.get("colocadoNoCarrinhoPor")).isEqualTo(restaurado.get("colocadoNoCarrinhoPor"));
        var remocao = objeto(pendente, "remocao");
        assertAutor(objeto(remocao, "solicitadaPor"), c.outroMembro(), c.outroId(), "Bia");
        assertThat(remocao.get("solicitadaEm")).isNotEqualTo(objeto(ciclo1, "remocao").get("solicitadaEm"));
        assertThat(remocao).containsEntry("decisao", null).containsEntry("decididaPor", null).containsEntry("decididaEm", null);
        assertThat(getItem(c, c.outro())).isEqualTo(pendente);
        acoesRestauracao(pendente, false, false, false);
        acoesRestauracao(getItem(c, c.terceiro()), false, true, false);
        acoesRestauracao(getItem(c, c.responsavel()), false, false, false);
        acao(c, decisao, c.responsavel(), 403);
        acao(c, decisao, c.outro(), 403);
        var decidido = acao(c, decisao, c.terceiro(), 200);
        assertThat(decidido.get("restauracao")).isEqualTo(restaurado.get("restauracao"));
        assertThat(getItem(c, c.terceiro())).isEqualTo(decidido);
        acoesRestauracao(decidido, decisao.equals("rejeitar"), false, decisao.equals("aprovar"));
    }

    @Test
    void segundaRestauracaoPorBiaSubstituiUltimaAutoriaEPreservaCicloAtual() throws Exception {
        var c = contexto();
        remover(c);
        var primeira = restaurar(c, c.terceiro(), 200);
        acao(c, "solicitar", c.outro(), 200);
        var aprovado = acao(c, "aprovar", c.terceiro(), 200);
        var segunda = restaurar(c, c.outro(), 200);
        assertAutor(objeto(objeto(segunda, "restauracao"), "restauradoPor"), c.outroMembro(), c.outroId(), "Bia");
        assertThat(objeto(segunda, "restauracao").get("restauradoEm"))
                .isNotEqualTo(objeto(primeira, "restauracao").get("restauradoEm"));
        assertThat(segunda.get("colocadoNoCarrinhoPor")).isEqualTo(objeto(segunda, "restauracao").get("restauradoPor"));
        assertThat(segunda.get("colocadoNoCarrinhoEm")).isEqualTo(objeto(segunda, "restauracao").get("restauradoEm"));
        assertThat(segunda.get("remocao")).isEqualTo(aprovado.get("remocao"));
        assertThat(getItem(c, c.outro())).isEqualTo(segunda);
        assertThat(restaurar(c, c.terceiro(), 200)).isEqualTo(segunda);
    }

    @Test
    void restauraAutoaprovacaoERejeitaAprovacaoAntiga() throws Exception {
        var c = contexto();
        colocar(c);
        var removido = acao(c, "solicitar", c.responsavel(), 200);
        acoesRestauracao(removido, false, false, true);
        var restaurado = restaurar(c, c.responsavel(), 200);
        acao(c, "aprovar", c.responsavel(), 409);
        assertThat(getItem(c, c.responsavel())).isEqualTo(restaurado);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void observadorMesmoAdministradorNaoRestauraNemFazReplay(boolean admin) throws Exception {
        var c = contexto();
        remover(c);
        String token = observador(c, admin);
        acoesRestauracao(getItem(c, token), false, false, false);
        restaurar(c, token, 403);
        restaurar(c, c.terceiro(), 200);
        acoesRestauracao(getItem(c, token), false, false, false);
        restaurar(c, token, 403);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void membroInativoNaoRestauraNemFazReplay(boolean replay) throws Exception {
        var c = contexto();
        remover(c);
        if (replay) restaurar(c, c.terceiro(), 200);
        jdbc.update("update membro_familia set status = 'INATIVO' where id = ?", c.terceiroMembro());
        restaurar(c, c.terceiro(), 403);
        mvc.perform(get(c.url()).header("Authorization", c.terceiro())).andExpect(status().isForbidden());
    }

    @Test
    void exigeJwtValido() throws Exception {
        var c = contexto();
        mvc.perform(post(endpoint(c))).andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint(c)).header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"outra_compra", "familia_lista", "item_ausente", "compra_ausente"})
    void contextoIncompativelRetorna404SemDetalhesExternos(String caso) throws Exception {
        var c = contexto();
        String url = endpoint(c);
        if (caso.equals("outra_compra")) url = url.replace(c.itemId(), contexto().itemId());
        if (caso.equals("familia_lista")) url = url.replace(c.familiaId().toString(), UUID.randomUUID().toString());
        if (caso.equals("item_ausente")) url = url.replace(c.itemId(), UUID.randomUUID().toString());
        if (caso.equals("compra_ausente")) {
            var lista = criarLista.criar(c.responsavelId(), c.familiaId(), "Sem compra", CategoriaCompra.OUTROS, null);
            url = url.replace(c.listaId().toString(), lista.getId().toString());
        }
        var erro = json(mvc.perform(post(url).header("Authorization", c.responsavel()))
                .andExpect(status().isNotFound()).andReturn());
        assertThat(erro).containsEntry("erro", "RECURSO_NAO_ENCONTRADO").containsEntry("mensagem", "Recurso nao encontrado.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDENTE", "NO_CARRINHO", "REMOCAO_SOLICITADA", "REJEITADA", "REMOVIDO_INCONSISTENTE", "RESPONSAVEL_INCOERENTE"})
    void estadosIncompativeisRetornam409ECapabilityFalse(String estado) throws Exception {
        var c = contexto();
        if (!estado.equals("PENDENTE")) colocar(c);
        if (estado.equals("REMOCAO_SOLICITADA") || estado.equals("REJEITADA")) {
            acao(c, "solicitar", c.outro(), 200);
            if (estado.equals("REJEITADA")) acao(c, "rejeitar", c.responsavel(), 200);
        }
        if (estado.equals("REMOVIDO_INCONSISTENTE")) {
            jdbc.update("update item_compra set status = 'REMOVIDO' where id = ?", UUID.fromString(c.itemId()));
        }
        if (estado.equals("RESPONSAVEL_INCOERENTE")) {
            acao(c, "solicitar", c.responsavel(), 200);
            jdbc.update("update item_compra set marcado_por_membro_familia_id = ? where id = ?", c.outroMembro(), UUID.fromString(c.itemId()));
        }
        var antes = getItem(c, c.terceiro());
        assertThat(objeto(antes, "acoes")).containsEntry("podeRestaurarNoCarrinho", false);
        restaurar(c, c.terceiro(), 409);
        assertThat(getItem(c, c.terceiro())).isEqualTo(antes);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void finalizaComItemRemovidoOuRestauradoEBloqueiaRestauracao(boolean restaurado) throws Exception {
        var c = contexto();
        remover(c);
        if (restaurado) restaurar(c, c.terceiro(), 200);
        var antes = getItem(c, c.terceiro());
        var finalizada = json(mvc.perform(post(c.url() + "/finalizar").header("Authorization", c.terceiro()))
                .andExpect(status().isOk()).andReturn());
        assertThat(finalizada.get("status")).isEqualTo("FINALIZADA");
        var depois = getItem(c, c.terceiro());
        assertThat(depois.get("status")).isEqualTo(antes.get("status"));
        assertThat(depois.get("restauracao")).isEqualTo(antes.get("restauracao"));
        assertThat(depois.get("remocao")).isEqualTo(antes.get("remocao"));
        acoesRestauracao(depois, false, false, false);
        restaurar(c, c.terceiro(), 409);
    }

    @Test
    void compraCanceladaBloqueiaRestauracaoECapabilities() throws Exception {
        var c = contexto();
        remover(c);
        jdbc.update("update compra set status = 'CANCELADA' where lista_compra_id = ?", c.listaId());
        acoesRestauracao(getItem(c, c.terceiro()), false, false, false);
        restaurar(c, c.terceiro(), 409);
    }

    @Test
    void itemIncluidoDuranteCompraMantemInclusaoEMapperEmTodasAsRespostas() throws Exception {
        var c = contexto();
        assertThat(getItem(c, c.outro())).containsKey("restauracao").containsEntry("restauracao", null);
        var novo = json(mvc.perform(post(c.url() + "/itens").header("Authorization", c.outro())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Leite\",\"quantidade\":1,\"unidadeMedida\":\"UNIDADE\"}"))
                .andExpect(status().isCreated()).andReturn());
        c = new Contexto(c.url(), novo.get("id").toString(), c.responsavel(), c.outro(), c.terceiro(),
                c.familiaId(), c.listaId(), c.responsavelId(), c.outroId(), c.responsavelMembro(), c.outroMembro(), c.terceiroId(), c.terceiroMembro());
        assertThat(getItem(c, c.outro())).isEqualTo(novo);
        assertThat(novo).containsEntry("restauracao", null);
        acoesRestauracao(novo, false, false, false);
        var carrinho = colocar(c);
        assertThat(getItem(c, c.responsavel())).isEqualTo(carrinho);
        var solicitada = acao(c, "solicitar", c.outro(), 200);
        assertThat(getItem(c, c.outro())).isEqualTo(solicitada);
        var rejeitada = acao(c, "rejeitar", c.responsavel(), 200);
        assertThat(getItem(c, c.responsavel())).isEqualTo(rejeitada);
        acao(c, "solicitar", c.outro(), 200);
        var aprovada = acao(c, "aprovar", c.responsavel(), 200);
        assertThat(getItem(c, c.responsavel())).isEqualTo(aprovada);
        var restaurada = restaurar(c, c.terceiro(), 200);
        assertThat(restaurada.get("adicionadoPor")).isEqualTo(novo.get("adicionadoPor"));
        assertThat(restaurada.get("adicionadoEm")).isEqualTo(novo.get("adicionadoEm"));
        assertThat(getItem(c, c.terceiro())).isEqualTo(restaurada);
    }

    private Map<String, Object> remover(Contexto c) throws Exception {
        colocar(c);
        acao(c, "solicitar", c.outro(), 200);
        return acao(c, "aprovar", c.responsavel(), 200);
    }

    private String endpoint(Contexto c) {
        return c.url() + "/itens/" + c.itemId() + "/restaurar-no-carrinho";
    }

    private Map<String, Object> restaurar(Contexto c, String token, int esperado) throws Exception {
        var resposta = json(mvc.perform(post(endpoint(c)).header("Authorization", token))
                .andExpect(status().is(esperado)).andReturn());
        if (esperado == 409 || esperado == 403) {
            assertThat(resposta).containsEntry("status", esperado).containsEntry("path", endpoint(c))
                    .containsEntry("erro", esperado == 409 ? "CONFLITO_DE_ESTADO" : "ACESSO_NEGADO")
                    .containsKeys("timestamp", "mensagem", "campos");
        }
        return resposta;
    }

    private void acoesRestauracao(Map<String, Object> item, boolean solicitar, boolean decidir, boolean restaurar) {
        acoes(item, solicitar, decidir);
        assertThat(objeto(item, "acoes")).containsEntry("podeRestaurarNoCarrinho", restaurar);
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
        Usuario ana=usuario("Ana"), bia=usuario("Bia"), caio=usuario("Camila");
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
        return new Contexto(url,itemId,token,bearer(bia),bearer(caio),familia.getId(),lista.getId(),ana.getId(),bia.getId(),anaMembro,biaMembro,caio.getId(),caioMembro);
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
            UUID responsavelId,UUID outroId,UUID responsavelMembro,UUID outroMembro,UUID terceiroId,UUID terceiroMembro) {}
}
