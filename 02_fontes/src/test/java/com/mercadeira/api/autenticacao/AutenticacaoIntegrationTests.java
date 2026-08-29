package com.mercadeira.api.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.autenticacao.application.CredenciaisInvalidasException;
import com.mercadeira.api.autenticacao.application.TokenAutenticacao;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
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
class AutenticacaoIntegrationTests {

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
    private CadastrarUsuario cadastrarUsuario;

    @Autowired
    private AutenticarUsuario autenticarUsuario;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UsuarioAutenticado usuarioAutenticado;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    void autenticaComCredenciaisCorretasESemExporHash() {
        Usuario usuario = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");

        TokenAutenticacao resultado = autenticarUsuario.autenticar("ana@example.test", "senha-original");

        assertThat(resultado.token()).isNotBlank();
        assertThat(resultado.expiraEm()).isAfter(Instant.now());
        assertThat(resultado.getClass().getRecordComponents())
                .extracting(componente -> componente.getName())
                .doesNotContain("senhaHash");
        assertThat(jwtDecoder.decode(resultado.token()).getSubject()).isEqualTo(usuario.getId().toString());
    }

    @Test
    void rejeitaSenhaIncorretaSemDistinguirDeEmailInexistente() {
        cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");

        assertThatThrownBy(() -> autenticarUsuario.autenticar("ana@example.test", "senha-incorreta"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha invalidos.");
        assertThatThrownBy(() -> autenticarUsuario.autenticar("ausente@example.test", "senha-original"))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha invalidos.");
    }

    @Test
    void tokenPossuiSubjectExpiracaoEAssinaturaValida() {
        Usuario usuario = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");

        TokenAutenticacao resultado = autenticarUsuario.autenticar("ana@example.test", "senha-original");
        Jwt jwt = jwtDecoder.decode(resultado.token());

        assertThat(jwt.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isEqualTo(resultado.expiraEm());
    }

    @Test
    void rejeitaTokenAdulteradoEExpirado() {
        Usuario usuario = cadastrarUsuario.cadastrar("Ana", "ana@example.test", "senha-original");
        TokenAutenticacao resultado = autenticarUsuario.autenticar("ana@example.test", "senha-original");
        String tokenAdulterado = resultado.token().substring(0, resultado.token().length() - 1) + "x";
        String tokenExpirado = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .issuedAt(Instant.now().minusSeconds(180))
                .expiresAt(Instant.now().minusSeconds(120))
                .build())).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(tokenAdulterado)).isNotNull();
        assertThatThrownBy(() -> jwtDecoder.decode(tokenExpirado)).isNotNull();
    }

    @Test
    void disponibilizaUuidAutenticadoParaFuturasCamadasWeb() {
        UUID usuarioId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token-de-teste")
                .header("alg", "HS256")
                .subject(usuarioId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));

        assertThat(usuarioAutenticado.getId()).isEqualTo(usuarioId);
        assertThat(securityFilterChain).isNotNull();
    }
}
