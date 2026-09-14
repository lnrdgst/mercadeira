package com.mercadeira.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import com.mercadeira.api.autenticacao.application.AutenticarUsuario;
import com.mercadeira.api.familia.application.CriarFamilia;
import com.mercadeira.api.lista.application.*;
import com.mercadeira.api.lista.domain.*;
import com.mercadeira.api.compra.application.IniciarCompra;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
class SugestoesItensIntegrationTests {
    @Container @ServiceConnection static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));
    @DynamicPropertySource static void jwt(DynamicPropertyRegistry r) {
        r.add("mercadeira.jwt.secret", () -> "c2VncmVkby1leGNsdXNpdm8tZGUtdGVzdGUtY29tLTMyLWJ5dGVzLW91LW1haXM=");
    }
    @Autowired WebApplicationContext context;
    @Autowired CadastrarUsuario cadastrar;
    @Autowired AutenticarUsuario autenticar;
    @Autowired CriarFamilia familias;
    @Autowired CriarListaCompra listas;
    @Autowired AdicionarItemLista itens;
    @Autowired IniciarCompra iniciar;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    Usuario usuario;
    UUID familia, lista;
    String url, auth;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        usuario = novoUsuario();
        familia = familias.criar(usuario.getId(), "Casa").getId();
        lista = listas.criar(usuario.getId(), familia, "Lista", CategoriaCompra.SUPERMERCADO, null).getId();
        url = "/api/familias/" + familia + "/itens/sugestoes";
        auth = token(usuario);
    }
    Usuario novoUsuario() { return cadastrar.cadastrar("Pessoa", UUID.randomUUID()+"@test.local", "senha-original"); }
    String token(Usuario u) { return "Bearer "+autenticar.autenticar(u.getEmail(), "senha-original").token(); }
    UUID item(String descricao, UnidadeMedida unidade) {
        return itens.adicionar(usuario.getId(), familia, lista, descricao, null, unidade, "Marca privada", "Observacao privada").getId();
    }
    @Test void deduplicaCaixaEspacosEUsaUnidadeMaisRecenteComBuscaParcial() throws Exception {
        item("Arroz integral", UnidadeMedida.UNIDADE);
        item("  ARROZ   integral  ", UnidadeMedida.KG);
        item("Feijao", null);
        mvc.perform(get(url).param("termo"," arrOZ  int ").header("Authorization",auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].descricao").value("ARROZ integral"))
            .andExpect(jsonPath("$[0].unidadeMedida").value("KG"))
            .andExpect(jsonPath("$[0].quantidade").doesNotExist())
            .andExpect(jsonPath("$[0].marca").doesNotExist()).andExpect(jsonPath("$[0].observacoes").doesNotExist());
    }
    @Test void recentesLimitadosOrdenadosEVazioSemResultado() throws Exception {
        for (int n=0; n<12; n++) {
            UUID id = item("Item "+n, null);
            jdbc.update("update item_lista set atualizado_em = timestamptz '2026-01-01 00:00:00+00' + ? * interval '1 minute' where id=?", n, id);
        }
        mvc.perform(get(url).header("Authorization",auth)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(10)).andExpect(jsonPath("$[0].descricao").value("Item 11"))
            .andExpect(jsonPath("$[9].descricao").value("Item 2"));
        mvc.perform(get(url).param("termo","inexistente").header("Authorization",auth)).andExpect(jsonPath("$").isEmpty());
    }
    @Test void excluiRemovidosDaPreparacaoEListasCanceladas() throws Exception {
        UUID removido = item("Removido", null);
        jdbc.update("update item_lista set removido_em=now() where id=?", removido);
        mvc.perform(get(url).header("Authorization",auth)).andExpect(jsonPath("$").isEmpty());
        item("Cancelado", null);
        jdbc.update("update lista_compra set status='CANCELADA' where id=?", lista);
        mvc.perform(get(url).header("Authorization",auth)).andExpect(jsonPath("$").isEmpty());
    }
    @Test void recuperaSnapshotsFinalizadosEItensIncluidosDuranteCompra() throws Exception {
        item("Arroz", UnidadeMedida.KG);
        UUID compra = iniciar.iniciar(usuario.getId(), familia, lista).compra().getId();
        UUID participante = jdbc.queryForObject("select id from participante_compra where compra_id=? limit 1", UUID.class, compra);
        jdbc.update("""
            insert into item_compra (id,compra_id,adicionado_durante_compra,descricao_snapshot,
                unidade_medida_snapshot,status,ordem_exibicao,adicionado_por_participante_compra_id,adicionado_em)
            values (?,?,true,'Cafe','PACOTE','PENDENTE',2,?,now())
            """, UUID.randomUUID(), compra, participante);
        mvc.perform(get(url).header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        jdbc.update("update compra set status='FINALIZADA',finalizada_em=now(),finalizada_por_participante_compra_id=? where id=?", participante, compra);
        jdbc.update("update lista_compra set status='FINALIZADA' where id=?", lista);
        mvc.perform(get(url).param("termo","cafe").header("Authorization",auth))
            .andExpect(jsonPath("$[0].descricao").value("Cafe")).andExpect(jsonPath("$[0].unidadeMedida").value("PACOTE"));
        UUID membro = jdbc.queryForObject("select id from membro_familia where usuario_id=?", UUID.class, usuario.getId());
        jdbc.update("""
            update item_compra set status='REMOVIDO', marcado_por_membro_familia_id=?,marcado_em=now(),
            remocao_solicitada_por_membro_familia_id=?,remocao_solicitada_em=now(),
            remocao_resolvida_por_membro_familia_id=?,remocao_resolvida_em=now(),decisao_remocao='APROVADA'
            where compra_id=? and descricao_snapshot='Cafe'
            """, membro,membro,membro,compra);
        mvc.perform(get(url).param("termo","cafe").header("Authorization",auth)).andExpect(jsonPath("$").isEmpty());
    }
    @Test void exigeVinculoAtivoMasNaoAutoriaOuParticipacaoEIsolaFamilias() throws Exception {
        item("Arroz", null);
        Usuario outro = novoUsuario();
        UUID outraFamilia = familias.criar(outro.getId(),"Outra").getId();
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
        mvc.perform(get(url).header("Authorization",token(outro))).andExpect(status().isForbidden());
        mvc.perform(get("/api/familias/"+outraFamilia+"/itens/sugestoes").header("Authorization",token(outro)))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        UUID membro = UUID.randomUUID();
        jdbc.update("insert into membro_familia(id,familia_id,usuario_id,papel,status,criado_em,atualizado_em) values (?,?,?,'MEMBRO','ATIVO',now(),now())", membro, familia, outro.getId());
        mvc.perform(get(url).header("Authorization",token(outro))).andExpect(status().isOk()).andExpect(jsonPath("$[0].descricao").value("Arroz"));
        jdbc.update("update membro_familia set status='INATIVO' where id=?", membro);
        mvc.perform(get(url).header("Authorization",token(outro))).andExpect(status().isForbidden());
    }
    @Test void limitaTermoETrataCaracteresComoTextoLiteral() throws Exception {
        item("Leite 100%", null);
        item("Leite integral", null);
        mvc.perform(get(url).param("termo","%").header("Authorization",auth)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(url).param("termo","' OR 1=1 --").header("Authorization",auth)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(url).param("termo","x".repeat(201)).header("Authorization",auth)).andExpect(status().isBadRequest());
    }
}