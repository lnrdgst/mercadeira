package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.lista.application.*;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.compra.application.IniciarCompra;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class ReutilizarListaIntegrationTests {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry r) {
        r.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }
    @Autowired WebApplicationContext context;
    @Autowired CadastrarUsuario cadastrar;
    @Autowired AutenticarUsuario autenticar;
    @Autowired CriarFamilia criarFamilia;
    @Autowired CriarListaCompra criarLista;
    @Autowired AdicionarItemLista adicionarItem;
    @Autowired AdicionarParticipanteLista adicionarParticipante;
    @Autowired EditarDadosBasicosLista editar;
    @Autowired IniciarCompra iniciar;
    @Autowired ListaCompraRepository listas;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    MockMvc mvc;
    Usuario criador;
    UUID familiaId;
    UUID listaId;
    String url;
    static final String BODY = "{\"nome\":\"Lista corrigida\",\"categoria\":\"ROUPAS\",\"estabelecimento\":null}";
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        criador = usuario();
        familiaId = criarFamilia.criar(criador.getId(), "Casa").getId();
        listaId = criarLista.criar(criador.getId(), familiaId, "Original", CategoriaCompra.SUPERMERCADO, "Mercado").getId();
        adicionarItem.adicionar(criador.getId(), familiaId, listaId, "Arroz", BigDecimal.ONE, UnidadeMedida.KG, null, null);
        url = "/api/familias/" + familiaId + "/listas/" + listaId;
    }
    Usuario usuario() { return cadastrar.cadastrar("Pessoa", UUID.randomUUID()+"@test.local", "senha-original"); }
    String token(Usuario u) { return "Bearer " + autenticar.autenticar(u.getEmail(), "senha-original").token(); }
    UUID membro(Usuario u, String papel) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into membro_familia (id,familia_id,usuario_id,papel,status,criado_em,atualizado_em) values (?,?,?,?,'ATIVO',now(),now())", id, familiaId, u.getId(), papel);
        return id;
    }
    void capability(Usuario u, boolean expected) throws Exception {
        mvc.perform(get(url).header("Authorization", token(u))).andExpect(status().isOk())
            .andExpect(jsonPath("$.contextoUsuario.podeEditarDadosBasicos").value(expected));
    }
    String iniciarCompra() throws Exception {
        return mvc.perform(post(url+"/compra").header("Authorization",token(criador)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    void finalizar() throws Exception {
        mvc.perform(post(url+"/compra/finalizar").header("Authorization",token(criador))).andExpect(status().isOk());
    }
    String consultarCompra() throws Exception {
        return mvc.perform(get(url+"/compra").header("Authorization",token(criador))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    UUID reutilizar(Usuario executor) throws Exception {
        var response = mvc.perform(post(url+"/reutilizar").header("Authorization",token(executor)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EM_PREPARACAO")).andReturn().getResponse();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response.getContentAsString(),"$.id"));
        assertThat(response.getHeader("Location")).isEqualTo("/api/familias/"+familiaId+"/listas/"+id);
        return id;
    }
    @Test void copiaCarrinhoPendentesEAdicionadosSemVinculosOuAuditorias() throws Exception {
        adicionarItem.adicionar(criador.getId(),familiaId,listaId,"Carrinho",new java.math.BigDecimal("2.5"),UnidadeMedida.KG,"Marca","Nota");
        adicionarItem.adicionar(criador.getId(),familiaId,listaId,"Removido",null,null,null,null);
        String inicio = iniciarCompra();
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(inicio,"$.itens[*].id");
        for (int n : new int[]{1,2}) mvc.perform(post(url+"/compra/itens/"+ids.get(n)+"/colocar-no-carrinho").header("Authorization",token(criador))).andExpect(status().isOk());
        mvc.perform(post(url+"/compra/itens/"+ids.get(2)+"/solicitar-remocao").header("Authorization",token(criador))).andExpect(status().isOk());
        mvc.perform(post(url+"/compra/itens/"+ids.get(2)+"/aprovar-remocao").header("Authorization",token(criador))).andExpect(status().isOk());
        mvc.perform(post(url+"/compra/itens").header("Authorization",token(criador)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"descricao\":\"Durante\",\"unidadeMedida\":\"PACOTE\"}")).andExpect(status().isCreated());
        finalizar();
        String antes = consultarCompra();
        UUID nova = reutilizar(criador);
        assertThat(nova).isNotEqualTo(listaId);
        assertThat(consultarCompra()).isEqualTo(antes);
        var copiados = jdbc.queryForList("select id,descricao,quantidade,unidade_medida,marca,observacoes,removido_em from item_lista where lista_compra_id=? order by ordem_exibicao",nova);
        assertThat(copiados).hasSize(3);
        assertThat(copiados).extracting(row -> row.get("descricao")).containsExactly("Arroz","Carrinho","Durante");
        assertThat(copiados.get(1).get("quantidade")).isEqualTo(new java.math.BigDecimal("2.500"));
        assertThat(copiados.get(1).get("marca")).isEqualTo("Marca");
        assertThat(copiados.get(1).get("observacoes")).isEqualTo("Nota");
        assertThat(copiados).allSatisfy(row -> { assertThat(row.get("removido_em")).isNull(); assertThat(ids).doesNotContain(row.get("id").toString()); });
        assertThat(jdbc.queryForObject("select count(*) from item_compra where item_lista_origem_id in (select id from item_lista where lista_compra_id=?)",Integer.class,nova)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from compra where lista_compra_id=?",Integer.class,nova)).isZero();
        mvc.perform(get("/api/familias/"+familiaId+"/listas/"+nova+"/itens").header("Authorization",token(criador))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }
    @Test void membroObservadorCriaPreparacaoSuaSemCopiarParticipantes() throws Exception {
        iniciarCompra(); finalizar();
        Usuario observador = usuario(); UUID membroNovo = membro(observador,"MEMBRO");
        mvc.perform(get(url+"/compra").header("Authorization",token(observador)))
            .andExpect(jsonPath("$.contextoUsuario.podeReutilizarLista").value(true))
            .andExpect(jsonPath("$.contextoUsuario.participanteCompra").value(false));
        UUID nova = reutilizar(observador);
        assertThat(jdbc.queryForList("select membro_familia_id from participante_lista where lista_compra_id=?",UUID.class,nova)).containsExactly(membroNovo);
        assertThat(jdbc.queryForObject("select criada_por_membro_familia_id from lista_compra where id=?",UUID.class,nova)).isEqualTo(membroNovo);
        assertThat(jdbc.queryForObject("select adicionado_por_membro_familia_id from item_lista where lista_compra_id=?",UUID.class,nova)).isEqualTo(membroNovo);
        mvc.perform(get("/api/familias/"+familiaId+"/listas/"+nova).header("Authorization",token(observador)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Original"))
            .andExpect(jsonPath("$.categoria").value("SUPERMERCADO"))
            .andExpect(jsonPath("$.estabelecimento").value("Mercado"))
            .andExpect(jsonPath("$.contextoUsuario.podeEditarDadosBasicos").value(true));
    }
    @Test void exigeJwtVinculoAtivoEContextoCorreto() throws Exception {
        iniciarCompra(); finalizar();
        mvc.perform(post(url+"/reutilizar")).andExpect(status().isUnauthorized());
        mvc.perform(post(url+"/reutilizar").header("Authorization",token(usuario()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/familias/"+UUID.randomUUID()+"/listas/"+listaId+"/reutilizar").header("Authorization",token(criador))).andExpect(status().isNotFound());
        jdbc.update("update membro_familia set status='INATIVO' where usuario_id=?",criador.getId());
        mvc.perform(post(url+"/reutilizar").header("Authorization",token(criador))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from lista_compra where familia_id=?",Integer.class,familiaId)).isEqualTo(1);
    }
    @Test void rejeitaOrigemNaoFinalizadaOuFamiliaInativaSemCriarLista() throws Exception {
        mvc.perform(post(url+"/reutilizar").header("Authorization",token(criador))).andExpect(status().isConflict());
        iniciarCompra();
        mvc.perform(get(url+"/compra").header("Authorization",token(criador))).andExpect(jsonPath("$.contextoUsuario.podeReutilizarLista").value(false));
        mvc.perform(post(url+"/reutilizar").header("Authorization",token(criador))).andExpect(status().isConflict());
        finalizar();
        jdbc.update("update familia set status='INATIVA' where id=?",familiaId);
        mvc.perform(get(url+"/compra").header("Authorization",token(criador))).andExpect(jsonPath("$.contextoUsuario.podeReutilizarLista").value(false));
        mvc.perform(post(url+"/reutilizar").header("Authorization",token(criador))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from lista_compra where familia_id=?",Integer.class,familiaId)).isEqualTo(1);
    }
    @Test void todosRemovidosCriamPreparacaoVaziaEChamadasDistintasCriamListasDistintas() throws Exception {
        String inicio = iniciarCompra();
        String item = com.jayway.jsonpath.JsonPath.read(inicio,"$.itens[0].id");
        for (String acao : new String[]{"colocar-no-carrinho","solicitar-remocao","aprovar-remocao"})
            mvc.perform(post(url+"/compra/itens/"+item+"/"+acao).header("Authorization",token(criador))).andExpect(status().isOk());
        finalizar();
        UUID a = reutilizar(criador), b = reutilizar(criador);
        assertThat(a).isNotEqualTo(b);
        assertThat(jdbc.queryForObject("select count(*) from item_lista where lista_compra_id in (?,?)",Integer.class,a,b)).isZero();
    }
    @Test void reaproveitaSomenteItensPendentesOuRemovidosSelecionadosComSnapshots() throws Exception {
        adicionarItem.adicionar(criador.getId(),familiaId,listaId,"Removido",new BigDecimal("2.5"),UnidadeMedida.KG,"Marca","Nota");
        String inicio = iniciarCompra();
        java.util.List<String> ids = com.jayway.jsonpath.JsonPath.read(inicio,"$.itens[*].id");
        for (String acao : new String[]{"colocar-no-carrinho","solicitar-remocao","aprovar-remocao"})
            mvc.perform(post(url+"/compra/itens/"+ids.get(1)+"/"+acao).header("Authorization",token(criador))).andExpect(status().isOk());
        finalizar();
        mvc.perform(get(url+"/compra").header("Authorization",token(criador)))
            .andExpect(jsonPath("$.contextoUsuario.podeCriarListaComItensQueFicaramDeFora").value(true));
        var resposta = mvc.perform(post(url+"/reaproveitar-itens-fora").header("Authorization",token(criador))
                .contentType(MediaType.APPLICATION_JSON).content("{\"itemIds\":[\""+ids.get(1)+"\"]}"))
            .andExpect(status().isCreated()).andReturn().getResponse();
        UUID nova = UUID.fromString(com.jayway.jsonpath.JsonPath.read(resposta.getContentAsString(),"$.id"));
        var copiados = jdbc.queryForList("select descricao,quantidade,unidade_medida,marca,observacoes,removido_em from item_lista where lista_compra_id=?",nova);
        assertThat(copiados).hasSize(1);
        assertThat(copiados.getFirst()).containsEntry("descricao","Removido").containsEntry("quantidade",new BigDecimal("2.500"))
            .containsEntry("unidade_medida","KG").containsEntry("marca","Marca").containsEntry("observacoes","Nota").containsEntry("removido_em",null);
    }
    @Test void reaproveitarItensForaRejeitaIdDeOutroItemOuEstadoNaoElegivelSemCriarLista() throws Exception {
        String inicio = iniciarCompra();
        String id = com.jayway.jsonpath.JsonPath.read(inicio,"$.itens[0].id");
        mvc.perform(post(url+"/compra/itens/"+id+"/colocar-no-carrinho").header("Authorization",token(criador))).andExpect(status().isOk());
        finalizar();
        int antes = jdbc.queryForObject("select count(*) from lista_compra where familia_id=?",Integer.class,familiaId);
        mvc.perform(post(url+"/reaproveitar-itens-fora").header("Authorization",token(criador)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\""+id+"\"]}"))
            .andExpect(status().isConflict());
        mvc.perform(post(url+"/reaproveitar-itens-fora").header("Authorization",token(criador)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemIds\":[\""+UUID.randomUUID()+"\"]}"))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from lista_compra where familia_id=?",Integer.class,familiaId)).isEqualTo(antes);
    }
    @Test void falhaAoCopiarItemReverteCriacaoDaListaEParticipante() throws Exception {
        iniciarCompra(); finalizar();
        jdbc.execute("""
            create function falhar_copia_teste() returns trigger language plpgsql as $$
            begin if new.descricao = 'Arroz' then raise exception 'falha controlada'; end if; return new; end $$;
            """);
        jdbc.execute("create trigger falhar_copia_teste before insert on item_lista for each row execute function falhar_copia_teste()");
        try {
            mvc.perform(post(url+"/reutilizar").header("Authorization",token(criador))).andExpect(status().is5xxServerError());
            assertThat(jdbc.queryForObject("select count(*) from lista_compra where familia_id=?",Integer.class,familiaId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from participante_lista where lista_compra_id in (select id from lista_compra where familia_id=?)",Integer.class,familiaId)).isEqualTo(1);
        } finally {
            jdbc.execute("drop trigger falhar_copia_teste on item_lista");
            jdbc.execute("drop function falhar_copia_teste()");
        }
    }
}
