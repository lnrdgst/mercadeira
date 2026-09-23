package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.UUID;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.application.SolicitarEntradaFamiliaPorCodigo;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import com.mercadeira.api.lista.application.CriarListaCompra;
import com.mercadeira.api.lista.application.AdicionarItemLista;
import com.mercadeira.api.lista.domain.CategoriaCompra;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.domain.UnidadeMedida;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@Transactional
@Rollback
class ApiIntegrationTests {

    private static final String JWT_TEST_SECRET = Base64.getEncoder().encodeToString(
            "segredo-exclusivo-de-teste-com-32-bytes-ou-mais".getBytes(StandardCharsets.UTF_8));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> JWT_TEST_SECRET);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private CadastrarUsuario cadastrarUsuario;
    @Autowired private AutenticarUsuario autenticarUsuario;
    @Autowired private CriarFamilia criarFamilia;
    @Autowired private SolicitarEntradaFamiliaPorCodigo solicitarEntrada;
    @Autowired private SolicitacaoEntradaFamiliaRepository solicitacaoRepository;
    @Autowired private CriarListaCompra criarListaCompra;
    @Autowired private AdicionarItemLista adicionarItemLista;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void listaFamiliasAtivasOuArrayVazio() throws Exception {
        Usuario semFamilia = usuario("Sem familia");
        mockMvc.perform(get("/api/familias").header("Authorization", bearer(semFamilia)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        Usuario usuario = usuario("Ana");
        criarFamilia.criar(usuario.getId(), "Zeta");
        criarFamilia.criar(usuario.getId(), "Alfa");
        mockMvc.perform(get("/api/familias").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Alfa"))
                .andExpect(jsonPath("$[0].papel").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[1].nome").value("Zeta"));
    }

    @Test
    void rotasDeFamiliaExigemJwt() throws Exception {
        mockMvc.perform(get("/api/familias")).andExpect(status().isUnauthorized());
    }

    @Test
    void solicitacaoEOnboardingFuncionamComOutraFamiliaAtiva() throws Exception {
        Usuario usuario = usuario("Bia");
        criarFamilia.criar(usuario.getId(), "Casa Bia");
        Usuario admin = usuario("Admin");
        Familia destino = criarFamilia.criar(admin.getId(), "Destino");

        mockMvc.perform(post("/api/familias/solicitacoes").header("Authorization", bearer(usuario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoIngresso\":\"" + destino.getCodigoIngresso() + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDENTE"));
        mockMvc.perform(get("/api/familias/solicitacoes/minhas-pendentes").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].familia.id").value(destino.getId().toString()));
    }

    @Test
    void aprovacaoEscopadaNaoCancelaOutrasPendencias() throws Exception {
        Usuario adminA = usuario("Admin A");
        Familia familiaA = criarFamilia.criar(adminA.getId(), "Familia A");
        Usuario adminB = usuario("Admin B");
        Familia familiaB = criarFamilia.criar(adminB.getId(), "Familia B");
        Usuario solicitante = usuario("Bia");
        UUID solicitacaoA = solicitarEntrada.solicitar(solicitante.getId(), familiaA.getCodigoIngresso()).getId();
        UUID solicitacaoB = solicitarEntrada.solicitar(solicitante.getId(), familiaB.getCodigoIngresso()).getId();

        mockMvc.perform(post("/api/familias/{familiaId}/solicitacoes/{id}/aprovar", familiaA.getId(), solicitacaoA)
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APROVADA"));
        assertThat(solicitacaoRepository.findById(solicitacaoB).orElseThrow().getStatus())
                .isEqualTo(StatusSolicitacaoEntradaFamilia.PENDENTE);
        mockMvc.perform(get("/api/familias/{familiaId}/solicitacoes", familiaB.getId())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void endpointAdministrativoExigeFamiliaDaSolicitacao() throws Exception {
        Usuario adminA = usuario("Admin A");
        Familia familiaA = criarFamilia.criar(adminA.getId(), "Familia A");
        Usuario adminB = usuario("Admin B");
        Familia familiaB = criarFamilia.criar(adminB.getId(), "Familia B");
        Usuario solicitante = usuario("Bia");
        SolicitacaoEntradaFamilia solicitacao = solicitarEntrada.solicitar(solicitante.getId(), familiaB.getCodigoIngresso());

        mockMvc.perform(post("/api/familias/{familiaId}/solicitacoes/{id}/aprovar", familiaA.getId(), solicitacao.getId())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void preparaListaPorApiNoContextoDaFamilia() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familiaA = criarFamilia.criar(ana.getId(), "Familia A");
        Usuario bia = usuario("Bia");
        Familia familiaB = criarFamilia.criar(bia.getId(), "Familia B");

        mockMvc.perform(post("/api/familias/{familiaId}/listas", familiaA.getId()).header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Compra semanal\",\"categoria\":\"SUPERMERCADO\",\"estabelecimento\":\"Mercado\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EM_PREPARACAO"));
        ListaCompra lista = criarListaCompra.criar(ana.getId(), familiaA.getId(), "Outra", CategoriaCompra.OUTROS, null);
        mockMvc.perform(get("/api/familias/{familiaId}/listas/{listaId}", familiaA.getId(), lista.getId()).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(lista.getId().toString()));
        mockMvc.perform(get("/api/familias/{familiaId}/listas/{listaId}", familiaB.getId(), lista.getId()).header("Authorization", bearer(bia)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioAutenticadoConsultaPropriosDadosSemCredenciais() throws Exception {
        Usuario usuario = usuario("Leonardo");
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Leonardo")).andExpect(jsonPath("$.email").value(usuario.getEmail()))
                .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.senhaHash").doesNotExist());
        mockMvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void membroAtivoListaMembrosAtivosOrdenadosESemAcessoExterno() throws Exception {
        Usuario ana = usuario("Ana"); Familia familiaA = criarFamilia.criar(ana.getId(), "A");
        Usuario bia = usuario("Bia");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())", UUID.randomUUID(), familiaA.getId(), bia.getId());
        Usuario inativo = usuario("Zoe");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'INATIVO', now(), now())", UUID.randomUUID(), familiaA.getId(), inativo.getId());
        mockMvc.perform(get("/api/familias/{id}/membros", familiaA.getId()).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Ana")).andExpect(jsonPath("$[0].membroFamiliaId").exists())
                .andExpect(jsonPath("$[0].usuarioId").exists()).andExpect(jsonPath("$[0].email").exists()).andExpect(jsonPath("$[0].papel").exists());
        Familia familiaB = criarFamilia.criar(inativo.getId(), "B");
        mockMvc.perform(get("/api/familias/{id}/membros", familiaA.getId()).header("Authorization", bearer(inativo)))
                .andExpect(status().isForbidden());
        assertThat(familiaB).isNotNull();
    }

    @Test
    void detalheDaListaExplicitaCriadorEContextoDoParticipante() throws Exception {
        Usuario ana = usuario("Ana"); Familia familia = criarFamilia.criar(ana.getId(), "A");
        ListaCompra lista = criarListaCompra.criar(ana.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        mockMvc.perform(get("/api/familias/{familiaId}/listas/{listaId}", familia.getId(), lista.getId()).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.criador.membroFamiliaId").value(lista.getCriadaPorMembroFamilia().getId().toString()))
                .andExpect(jsonPath("$.criador.usuarioId").value(ana.getId().toString())).andExpect(jsonPath("$.criador.nome").value("Ana"))
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(true));
    }

    @Test
    void detalheDistingueAdministradorEMembroNaoParticipantes() throws Exception {
        Usuario criador = usuario("Criador"); Familia familia = criarFamilia.criar(criador.getId(), "A");
        ListaCompra lista = criarListaCompra.criar(criador.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        Usuario admin = usuario("Admin"); Usuario membro = usuario("Membro"); entityManager.flush();
        UUID adminId = UUID.randomUUID(); UUID membroId = UUID.randomUUID();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'ADMINISTRADOR', 'ATIVO', now(), now())", adminId, familia.getId(), admin.getId());
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())", membroId, familia.getId(), membro.getId());
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false)).andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(true)).andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(false));
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(membro)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false)).andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(false)).andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(false));
        mockMvc.perform(post("/api/familias/{f}/listas/{l}/participantes", familia.getId(), lista.getId()).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"membroFamiliaId\":\"" + adminId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true)).andExpect(jsonPath("$.contextoUsuario.podeGerenciarParticipantes").value(true)).andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(true));
    }

    @Test
    void detalheNaoPermiteAlterarItensForaDePreparacao() throws Exception {
        Usuario usuario = usuario("Ana"); Familia familia = criarFamilia.criar(usuario.getId(), "A");
        ListaCompra lista = criarListaCompra.criar(usuario.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        entityManager.flush();
        jdbcTemplate.update("update lista_compra set status = 'EM_COMPRA' where id = ?", lista.getId());
        entityManager.clear();
        mockMvc.perform(get("/api/familias/{f}/listas/{l}", familia.getId(), lista.getId()).header("Authorization", bearer(usuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeAlterarItens").value(false));
    }

    @Test
    void permiteSaidaVoluntariaSomenteAoParticipanteAtivoNaoCriadorEmPreparacao() throws Exception {
        Usuario criador = usuario("Criador");
        Familia familia = criarFamilia.criar(criador.getId(), "Casa da equipe");
        ListaCompra lista = criarListaCompra.criar(criador.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        Usuario participante = usuario("Participante");
        Usuario terceiro = usuario("Terceiro");
        UUID participanteId = UUID.randomUUID();
        UUID terceiroId = UUID.randomUUID();
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())", participanteId, familia.getId(), participante.getId());
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())", terceiroId, familia.getId(), terceiro.getId());
        String url = "/api/familias/" + familia.getId() + "/listas/" + lista.getId();

        mockMvc.perform(post(url + "/participantes").header("Authorization", bearer(criador)).contentType(MediaType.APPLICATION_JSON).content("{\"membroFamiliaId\":\"" + participanteId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(url + "/participantes").header("Authorization", bearer(criador)).contentType(MediaType.APPLICATION_JSON).content("{\"membroFamiliaId\":\"" + terceiroId + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get(url).header("Authorization", bearer(criador)))
                .andExpect(jsonPath("$.contextoUsuario.podeSairDaLista").value(false));
        mockMvc.perform(get(url).header("Authorization", bearer(participante)))
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true))
                .andExpect(jsonPath("$.contextoUsuario.podeSairDaLista").value(true));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + terceiroId).header("Authorization", bearer(participante)))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + participanteId).header("Authorization", bearer(participante)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url).header("Authorization", bearer(participante)))
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false))
                .andExpect(jsonPath("$.contextoUsuario.podeSairDaLista").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + participanteId).header("Authorization", bearer(participante)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + lista.getCriadaPorMembroFamilia().getId()).header("Authorization", bearer(criador)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + terceiroId).header("Authorization", bearer(criador)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(url + "/participantes").header("Authorization", bearer(criador)).contentType(MediaType.APPLICATION_JSON).content("{\"membroFamiliaId\":\"" + participanteId + "\"}"))
                .andExpect(status().isCreated());
        jdbcTemplate.update("update lista_compra set status = 'EM_COMPRA' where id = ?", lista.getId());
        entityManager.clear();
        mockMvc.perform(get(url).header("Authorization", bearer(participante)))
                .andExpect(jsonPath("$.contextoUsuario.podeSairDaLista").value(false));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + participanteId).header("Authorization", bearer(participante)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void iniciaCompraRetornaSnapshotsEReplayIdempotente() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra lista = listaComItens(ana, familia, "Compra da semana");

        var primeiro = mockMvc.perform(post(compraUrl(familia, lista)).header("Authorization", bearer(ana)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listaId").value(lista.getId().toString()))
                .andExpect(jsonPath("$.nomeLista").value("Compra da semana"))
                .andExpect(jsonPath("$.categoria").value("SUPERMERCADO"))
                .andExpect(jsonPath("$.status").value("EM_ANDAMENTO"))
                .andExpect(jsonPath("$.participantes.length()").value(1))
                .andExpect(jsonPath("$.participantes[0].nome").value("Ana"))
                .andExpect(jsonPath("$.participantes[0].papel").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].descricao").value("Arroz"))
                .andExpect(jsonPath("$.itens[0].unidadeMedida").value("UNIDADE"))
                .andExpect(jsonPath("$.itens[0].adicionadoDuranteCompra").value(false))
                .andExpect(jsonPath("$.itens[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.itens[0].ordemExibicao").value(1))
                .andExpect(jsonPath("$.itens[1].ordemExibicao").value(2))
                .andExpect(jsonPath("$.contextoUsuario.participanteCompra").value(true))
                .andExpect(jsonPath("$.nomeListaSnapshot").doesNotExist())
                .andExpect(jsonPath("$.categoriaSnapshot").doesNotExist())
                .andReturn();
        String compraId = com.jayway.jsonpath.JsonPath.read(primeiro.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post(compraUrl(familia, lista)).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(compraId));
        mockMvc.perform(get(compraUrl(familia, lista)).header("Authorization", bearer(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(compraId))
                .andExpect(jsonPath("$.contextoUsuario.participanteCompra").value(true));

        assertThat(jdbcTemplate.queryForObject("select count(*) from compra where lista_compra_id = ?", Integer.class, lista.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from participante_compra where compra_id = ?", Integer.class, UUID.fromString(compraId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from item_compra where compra_id = ?", Integer.class, UUID.fromString(compraId))).isEqualTo(2);
    }

    @Test
    void membroObservadorConsultaCompraSemSerParticipante() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra lista = listaComItens(ana, familia, "Lista");
        mockMvc.perform(post(compraUrl(familia, lista)).header("Authorization", bearer(ana))).andExpect(status().isCreated());

        Usuario observador = usuario("Bia");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())",
                UUID.randomUUID(), familia.getId(), observador.getId());

        mockMvc.perform(get(compraUrl(familia, lista)).header("Authorization", bearer(observador)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contextoUsuario.participanteCompra").value(false));
    }

    @Test
    void impedeInicioPorMembrosNaoParticipantesMesmoQuandoAdministrador() throws Exception {
        Usuario criador = usuario("Criador");
        Familia familia = criarFamilia.criar(criador.getId(), "Familia Compra");
        ListaCompra lista = listaComItens(criador, familia, "Lista");
        Usuario administrador = usuario("Admin");
        Usuario membro = usuario("Membro");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'ADMINISTRADOR', 'ATIVO', now(), now())",
                UUID.randomUUID(), familia.getId(), administrador.getId());
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())",
                UUID.randomUUID(), familia.getId(), membro.getId());

        mockMvc.perform(post(compraUrl(familia, lista)).header("Authorization", bearer(administrador))).andExpect(status().isForbidden());
        mockMvc.perform(post(compraUrl(familia, lista)).header("Authorization", bearer(membro))).andExpect(status().isForbidden());
    }

    @Test
    void inicioInformaConflitosParaListaSemItensEOuForaDePreparacao() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra semItens = criarListaCompra.criar(ana.getId(), familia.getId(), "Sem itens", CategoriaCompra.OUTROS, null);
        mockMvc.perform(post(compraUrl(familia, semItens)).header("Authorization", bearer(ana))).andExpect(status().isConflict());

        ListaCompra finalizada = listaComItens(ana, familia, "Finalizada");
        entityManager.flush();
        jdbcTemplate.update("update lista_compra set status = 'FINALIZADA' where id = ?", finalizada.getId());
        entityManager.clear();
        mockMvc.perform(post(compraUrl(familia, finalizada)).header("Authorization", bearer(ana))).andExpect(status().isConflict());
    }

    @Test
    void consultaCompraRespeitaAusenciaContextoFamiliarEAutenticacao() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familiaA = criarFamilia.criar(ana.getId(), "Familia A");
        ListaCompra lista = listaComItens(ana, familiaA, "Lista");
        Usuario bia = usuario("Bia");
        Familia familiaB = criarFamilia.criar(bia.getId(), "Familia B");

        mockMvc.perform(get(compraUrl(familiaA, lista)).header("Authorization", bearer(ana))).andExpect(status().isNotFound());
        mockMvc.perform(get(compraUrl(familiaB, lista)).header("Authorization", bearer(bia))).andExpect(status().isNotFound());
        mockMvc.perform(get(compraUrl(familiaA, lista))).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void administradorParticipaComRespostaCompletaAposEncerrarTransacao() throws Exception {
        Usuario criador = usuario("Criador");
        Familia familia = criarFamilia.criar(criador.getId(), "Participacao sem sessao externa");
        ListaCompra lista = criarListaCompra.criar(criador.getId(), familia.getId(), "Lista", CategoriaCompra.OUTROS, null);
        Usuario admin = usuario("Admin");
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'ADMINISTRADOR', 'ATIVO', now(), now())",
                adminId, familia.getId(), admin.getId());
        UUID criadorId = jdbcTemplate.queryForObject("select id from membro_familia where familia_id = ? and usuario_id = ?",
                UUID.class, familia.getId(), criador.getId());
        String url = "/api/familias/" + familia.getId() + "/listas/" + lista.getId();
        String token = bearer(admin);
        String body = "{\"membroFamiliaId\":\"" + adminId + "\"}";

        mockMvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false));
        mockMvc.perform(post(url + "/participantes").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membroFamiliaId").value(adminId.toString()))
                .andExpect(jsonPath("$.usuarioId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Admin"))
                .andExpect(jsonPath("$.papelFamilia").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.entrouEm").isNotEmpty());
        for (int consulta = 0; consulta < 2; consulta++) {
            mockMvc.perform(get(url).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EM_PREPARACAO"))
                    .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(true));
            mockMvc.perform(get(url + "/participantes").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.membroFamiliaId == '" + adminId + "')]").isNotEmpty());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + criadorId).header("Authorization", token))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url + "/participantes/" + adminId).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextoUsuario.participanteAtivo").value(false));
        mockMvc.perform(post(url + "/participantes").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Admin"));
    }

    @Test
    void participanteColocaItemNoCarrinhoComReplayEGetPreservaAuditoria() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra lista = listaComItens(ana, familia, "Lista");
        String compra = compraUrl(familia, lista);
        var inicio = mockMvc.perform(post(compra).header("Authorization", bearer(ana)))
                .andExpect(status().isCreated()).andReturn();
        String itemId = com.jayway.jsonpath.JsonPath.read(inicio.getResponse().getContentAsString(), "$.itens[0].id");
        String url = compra + "/itens/" + itemId + "/colocar-no-carrinho";

        var primeira = mockMvc.perform(post(url).header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_CARRINHO"))
                .andExpect(jsonPath("$.adicionadoPor").doesNotExist())
                .andExpect(jsonPath("$.colocadoNoCarrinhoPor.participanteCompraId").exists())
                .andExpect(jsonPath("$.colocadoNoCarrinhoPor.usuarioId").value(ana.getId().toString()))
                .andExpect(jsonPath("$.colocadoNoCarrinhoPor.nome").value("Ana"))
                .andExpect(jsonPath("$.colocadoNoCarrinhoEm").isNotEmpty())
                .andReturn();
        String momento = com.jayway.jsonpath.JsonPath.read(primeira.getResponse().getContentAsString(), "$.colocadoNoCarrinhoEm");
        String participante = com.jayway.jsonpath.JsonPath.read(primeira.getResponse().getContentAsString(), "$.colocadoNoCarrinhoPor.participanteCompraId");

        mockMvc.perform(post(url).header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colocadoNoCarrinhoEm").value(momento))
                .andExpect(jsonPath("$.colocadoNoCarrinhoPor.participanteCompraId").value(participante));
        mockMvc.perform(get(compra).header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].status").value("NO_CARRINHO"))
                .andExpect(jsonPath("$.itens[0].colocadoNoCarrinhoEm").value(momento))
                .andExpect(jsonPath("$.itens[0].colocadoNoCarrinhoPor.nome").value("Ana"));
    }

    @Test
    void participanteAdicionaItemDuranteCompraComAutoriaEGetPersistido() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra lista = listaComItens(ana, familia, "Lista");
        String compra = compraUrl(familia, lista);
        mockMvc.perform(post(compra).header("Authorization", bearer(ana))).andExpect(status().isCreated());

        var adicao = mockMvc.perform(post(compra + "/itens").header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descricao\":\"Carvao\",\"quantidade\":1,\"unidadeMedida\":\"PACOTE\",\"marca\":null,\"observacoes\":\"5 kg\",\"status\":\"REMOVIDO\",\"ordemExibicao\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemListaOrigemId").doesNotExist())
                .andExpect(jsonPath("$.adicionadoDuranteCompra").value(true))
                .andExpect(jsonPath("$.descricao").value("Carvao"))
                .andExpect(jsonPath("$.unidadeMedida").value("PACOTE"))
                .andExpect(jsonPath("$.ordemExibicao").value(3))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.adicionadoPor.participanteCompraId").exists())
                .andExpect(jsonPath("$.adicionadoPor.usuarioId").value(ana.getId().toString()))
                .andExpect(jsonPath("$.adicionadoPor.nome").value("Ana"))
                .andExpect(jsonPath("$.adicionadoEm").isNotEmpty())
                .andExpect(jsonPath("$.colocadoNoCarrinhoPor").doesNotExist())
                .andReturn();
        String itemId = com.jayway.jsonpath.JsonPath.read(adicao.getResponse().getContentAsString(), "$.id");
        String adicionadoEm = com.jayway.jsonpath.JsonPath.read(adicao.getResponse().getContentAsString(), "$.adicionadoEm");

        mockMvc.perform(get(compra).header("Authorization", bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[?(@.id == '" + itemId + "')].adicionadoDuranteCompra").value(true))
                .andExpect(jsonPath("$.itens[?(@.id == '" + itemId + "')].adicionadoPor.nome").value("Ana"))
                .andExpect(jsonPath("$.itens[?(@.id == '" + itemId + "')].adicionadoEm").value(adicionadoEm));
    }

    @Test
    void mutacoesDeItensDaCompraExigemParticipacaoEValidamDescricao() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra lista = listaComItens(ana, familia, "Lista");
        String compra = compraUrl(familia, lista);
        var inicio = mockMvc.perform(post(compra).header("Authorization", bearer(ana))).andExpect(status().isCreated()).andReturn();
        String itemId = com.jayway.jsonpath.JsonPath.read(inicio.getResponse().getContentAsString(), "$.itens[0].id");
        Usuario observador = usuario("Observador");
        Usuario admin = usuario("Admin");
        entityManager.flush();
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'MEMBRO', 'ATIVO', now(), now())",
                UUID.randomUUID(), familia.getId(), observador.getId());
        jdbcTemplate.update("insert into membro_familia (id, familia_id, usuario_id, papel, status, criado_em, atualizado_em) values (?, ?, ?, 'ADMINISTRADOR', 'ATIVO', now(), now())",
                UUID.randomUUID(), familia.getId(), admin.getId());

        for (Usuario usuarioSemParticipacao : java.util.List.of(observador, admin)) {
            mockMvc.perform(post(compra + "/itens").header("Authorization", bearer(usuarioSemParticipacao))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Cafe\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(compra + "/itens/" + itemId + "/colocar-no-carrinho")
                            .header("Authorization", bearer(usuarioSemParticipacao)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(compra + "/itens").header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mutacoesDeItensDaCompraRejeitamContextoIncompativelEConflitosDeEstado() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Familia Compra");
        ListaCompra listaA = listaComItens(ana, familia, "Lista A");
        ListaCompra listaB = listaComItens(ana, familia, "Lista B");
        String compraA = compraUrl(familia, listaA);
        String compraB = compraUrl(familia, listaB);
        mockMvc.perform(post(compraA).header("Authorization", bearer(ana))).andExpect(status().isCreated());
        var inicioB = mockMvc.perform(post(compraB).header("Authorization", bearer(ana))).andExpect(status().isCreated()).andReturn();
        String itemDaCompraB = com.jayway.jsonpath.JsonPath.read(inicioB.getResponse().getContentAsString(), "$.itens[0].id");
        mockMvc.perform(post(compraA + "/itens/" + itemDaCompraB + "/colocar-no-carrinho").header("Authorization", bearer(ana)))
                .andExpect(status().isNotFound());

        UUID compraAId = jdbcTemplate.queryForObject("select id from compra where lista_compra_id = ?", UUID.class, listaA.getId());
        jdbcTemplate.update("""
                update compra set status = 'FINALIZADA', finalizada_em = CURRENT_TIMESTAMP,
                    finalizada_por_participante_compra_id = (
                        select pc.id from participante_compra pc
                        where pc.compra_id = compra.id order by pc.id limit 1)
                where id = ?
                """, compraAId);
        entityManager.clear();
        mockMvc.perform(post(compraA + "/itens").header("Authorization", bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"descricao\":\"Cafe\"}"))
                .andExpect(status().isConflict());

        UUID compraBId = jdbcTemplate.queryForObject("select id from compra where lista_compra_id = ?", UUID.class, listaB.getId());
        UUID membroAna = jdbcTemplate.queryForObject("select id from membro_familia where familia_id = ? and usuario_id = ?", UUID.class, familia.getId(), ana.getId());
        jdbcTemplate.update("update item_compra set status = 'REMOCAO_SOLICITADA', remocao_solicitada_por_membro_familia_id = ?, remocao_solicitada_em = now() where id = ?",
                membroAna, UUID.fromString(itemDaCompraB));
        mockMvc.perform(post(compraB + "/itens/" + itemDaCompraB + "/colocar-no-carrinho").header("Authorization", bearer(ana)))
                .andExpect(status().isConflict());
        assertThat(compraBId).isNotNull();
    }

    private ListaCompra listaComItens(Usuario usuario, Familia familia, String nome) {
        ListaCompra lista = criarListaCompra.criar(usuario.getId(), familia.getId(), nome, CategoriaCompra.SUPERMERCADO, "Mercado Central");
        adicionarItemLista.adicionar(usuario.getId(), familia.getId(), lista.getId(), "Arroz", BigDecimal.ONE, UnidadeMedida.UNIDADE, "Marca A", null);
        adicionarItemLista.adicionar(usuario.getId(), familia.getId(), lista.getId(), "Feijao", BigDecimal.TWO, UnidadeMedida.UNIDADE, "Marca B", "Organico");
        return lista;
    }

    private String compraUrl(Familia familia, ListaCompra lista) {
        return "/api/familias/" + familia.getId() + "/listas/" + lista.getId() + "/compra";
    }

    private Usuario usuario(String nome) {
        return cadastrarUsuario.cadastrar(nome, nome.toLowerCase().replace(" ", "") + UUID.randomUUID() + "@test.local", "senha-original");
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + autenticarUsuario.autenticar(usuario.getEmail(), "senha-original").token();
    }
}
