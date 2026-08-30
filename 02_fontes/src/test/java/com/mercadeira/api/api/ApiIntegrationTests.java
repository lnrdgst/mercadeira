package com.mercadeira.api.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.domain.StatusSolicitacaoEntradaFamilia;
import com.mercadeira.api.familia.repository.SolicitacaoEntradaFamiliaRepository;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
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
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(
            DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void configurarJwt(DynamicPropertyRegistry registry) {
        registry.add("mercadeira.jwt.secret", () -> JWT_TEST_SECRET);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CadastrarUsuario cadastrarUsuario;

    @Autowired
    private AutenticarUsuario autenticarUsuario;

    @Autowired
    private CriarFamilia criarFamilia;

    @Autowired
    private SolicitacaoEntradaFamiliaRepository solicitacaoRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void configurarMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void cadastraUsuarioValidoSemExporHash() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"email\":\"ana@example.test\",\"senha\":\"senha-original\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("ana@example.test"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void retornaErroDeValidacaoParaEmailInvalido() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"email\":\"invalido\",\"senha\":\"senha-original\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.campos.email").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/usuarios"));
    }

    @Test
    void retornaConflitoParaEmailDuplicado() throws Exception {
        cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outra Ana\",\"email\":\"ana@example.test\",\"senha\":\"senha-original\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CONFLITO_DE_ESTADO"));
    }

    @Test
    void realizaLoginValidoERejeitaCredenciaisInvalidas() throws Exception {
        cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");

        mockMvc.perform(post("/api/autenticacao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.test\",\"senha\":\"senha-original\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiracao").exists());
        mockMvc.perform(post("/api/autenticacao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@example.test\",\"senha\":\"invalida\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("NAO_AUTENTICADO"));
    }

    @Test
    void exigeJwtParaRotasDeFamilia() throws Exception {
        mockMvc.perform(get("/api/familias/ativa"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("NAO_AUTENTICADO"));
    }

    @Test
    void criaFamiliaAutenticadoEConsultaFamiliaAtiva() throws Exception {
        Usuario usuario = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");
        String token = tokenDo(usuario);

        mockMvc.perform(post("/api/familias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Casa da Ana\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andExpect(jsonPath("$.papel").value("ADMINISTRADOR"));
        mockMvc.perform(get("/api/familias/ativa").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Casa da Ana"));
    }

    @Test
    void solicitaEntradaEListaSolicitacoesComoAdministrador() throws Exception {
        Usuario administrador = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");
        Familia familia = criarFamilia.criar(administrador.getId(), "Casa da Ana");
        Usuario solicitante = cadastrarUsuario.cadastrar("Bia", "bia@example.test", "senha-original");

        mockMvc.perform(post("/api/familias/solicitacoes")
                        .header("Authorization", "Bearer " + tokenDo(solicitante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigoIngresso\":\"" + familia.getCodigoIngresso() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.solicitante.id").value(solicitante.getId().toString()))
                .andExpect(jsonPath("$.solicitante.nome").value("Bia"))
                .andExpect(jsonPath("$.solicitante.email").value("bia@example.test"))
                .andExpect(jsonPath("$.solicitante.senhaHash").doesNotExist());
        mockMvc.perform(get("/api/familias/solicitacoes")
                        .header("Authorization", "Bearer " + tokenDo(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0].solicitante.id").value(solicitante.getId().toString()))
                .andExpect(jsonPath("$[0].solicitante.nome").value("Bia"))
                .andExpect(jsonPath("$[0].solicitante.email").value("bia@example.test"))
                .andExpect(jsonPath("$[0].solicitante.senhaHash").doesNotExist());
    }

    @Test
    void impedeListagemPorMembroSemPermissao() throws Exception {
        Usuario administrador = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");
        Familia familia = criarFamilia.criar(administrador.getId(), "Casa da Ana");
        Usuario solicitante = cadastrarUsuario.cadastrar("Bia", "bia@example.test", "senha-original");
        SolicitarEntradaFamiliaResponseHelper.criarPendente(solicitante, familia, solicitacaoRepository);

        mockMvc.perform(get("/api/familias/solicitacoes")
                        .header("Authorization", "Bearer " + tokenDo(solicitante)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ACESSO_NEGADO"));
    }

    @Test
    void aprovaESemelhantementeRejeitaSolicitacoesAutenticado() throws Exception {
        Usuario administrador = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");
        Familia familia = criarFamilia.criar(administrador.getId(), "Casa da Ana");
        Usuario primeiroSolicitante = cadastrarUsuario.cadastrar("Bia", "bia@example.test", "senha-original");
        Usuario segundoSolicitante = cadastrarUsuario.cadastrar("Caio", "caio@example.test", "senha-original");
        UUID aprovacaoId = SolicitarEntradaFamiliaResponseHelper.criarPendente(
                primeiroSolicitante, familia, solicitacaoRepository);
        UUID rejeicaoId = SolicitarEntradaFamiliaResponseHelper.criarPendente(
                segundoSolicitante, familia, solicitacaoRepository);
        String token = tokenDo(administrador);

        mockMvc.perform(post("/api/familias/solicitacoes/{id}/aprovar", aprovacaoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADA"))
                .andExpect(jsonPath("$.solicitante.id").value(primeiroSolicitante.getId().toString()))
                .andExpect(jsonPath("$.solicitante.nome").value("Bia"))
                .andExpect(jsonPath("$.solicitante.email").value("bia@example.test"))
                .andExpect(jsonPath("$.solicitante.senhaHash").doesNotExist());
        mockMvc.perform(post("/api/familias/solicitacoes/{id}/rejeitar", rejeicaoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADA"))
                .andExpect(jsonPath("$.solicitante.id").value(segundoSolicitante.getId().toString()))
                .andExpect(jsonPath("$.solicitante.nome").value("Caio"))
                .andExpect(jsonPath("$.solicitante.email").value("caio@example.test"))
                .andExpect(jsonPath("$.solicitante.senhaHash").doesNotExist());
    }

    private String tokenDo(Usuario usuario) {
        return autenticarUsuario.autenticar(usuario.getEmail(), "senha-original").token();
    }

    private static final class SolicitarEntradaFamiliaResponseHelper {

        private SolicitarEntradaFamiliaResponseHelper() {
        }

        static UUID criarPendente(
                Usuario solicitante,
                Familia familia,
                SolicitacaoEntradaFamiliaRepository solicitacaoRepository) {
            SolicitacaoEntradaFamilia solicitacao = com.mercadeira.api.familia.domain.SolicitacaoEntradaFamilia.criar(
                    familia, solicitante, java.time.Instant.now());
            return solicitacaoRepository.save(solicitacao).getId();
        }
    }
}
