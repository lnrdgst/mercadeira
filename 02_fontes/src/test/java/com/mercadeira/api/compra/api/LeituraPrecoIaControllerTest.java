package com.mercadeira.api.compra.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.mercadeira.api.api.ApiExceptionHandler;
import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.compra.application.AdicionarItemDuranteCompra;
import com.mercadeira.api.compra.application.AprovarRemocaoItemCompra;
import com.mercadeira.api.compra.application.ColocarItemNoCarrinho;
import com.mercadeira.api.compra.application.ConsultarCompraDaLista;
import com.mercadeira.api.compra.application.ContinuarCompra;
import com.mercadeira.api.compra.application.EncerrarCompraProlongada;
import com.mercadeira.api.compra.application.FinalizarCompra;
import com.mercadeira.api.compra.application.FluxoPresencaCompra;
import com.mercadeira.api.compra.application.GerenciarRegistrosFinanceirosCompra;
import com.mercadeira.api.compra.application.IniciarCompra;
import com.mercadeira.api.compra.application.LerPrecoIaItemCompra;
import com.mercadeira.api.compra.application.PresencaOperacionalObrigatoriaException;
import com.mercadeira.api.compra.application.RegistrarDadosItemCompra;
import com.mercadeira.api.compra.application.RejeitarRemocaoItemCompra;
import com.mercadeira.api.compra.application.RestaurarItemNoCarrinho;
import com.mercadeira.api.compra.application.SolicitarRemocaoItemCompra;
import com.mercadeira.api.compra.application.UsuarioNaoParticipaDaCompraException;
import com.mercadeira.api.compra.leiturapreco.LeituraPrecoIaIndisponivelException;
import com.mercadeira.api.compra.leiturapreco.LeituraPrecoIaProvedorException;
import com.mercadeira.api.compra.leiturapreco.LeituraPrecoIaRespostaInvalidaException;
import com.mercadeira.api.compra.leiturapreco.LeituraPrecoIaTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LeituraPrecoIaControllerTest {
    private final UUID usuarioId = UUID.randomUUID();
    private final UUID familiaId = UUID.randomUUID();
    private final UUID listaId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private LerPrecoIaItemCompra leitor;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        UsuarioAutenticado usuario = mock(UsuarioAutenticado.class);
        when(usuario.getId()).thenReturn(usuarioId);
        leitor = mock(LerPrecoIaItemCompra.class);
        CompraController controller = new CompraController(usuario, mock(IniciarCompra.class), mock(ConsultarCompraDaLista.class),
                mock(ColocarItemNoCarrinho.class), mock(AdicionarItemDuranteCompra.class), mock(GerenciarRegistrosFinanceirosCompra.class),
                mock(SolicitarRemocaoItemCompra.class), mock(AprovarRemocaoItemCompra.class), mock(RejeitarRemocaoItemCompra.class),
                mock(FinalizarCompra.class), mock(RestaurarItemNoCarrinho.class),
                mock(com.mercadeira.api.compra.application.AlterarMinhaPresencaCompra.class), mock(FluxoPresencaCompra.class),
                mock(ContinuarCompra.class), mock(RegistrarDadosItemCompra.class), mock(EncerrarCompraProlongada.class), leitor);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler(Clock.systemUTC())).build();
    }

    @Test
    void aceitaPngValidoERetornaPrecoSemAlterarItem() throws Exception {
        when(leitor.executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/png")))
                .thenReturn(List.of(new BigDecimal("2.50")));

        mvc.perform(requisicao("image/png", new byte[] { 1, 2 }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precos[0]").value(2.50));

        verify(leitor).executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/png"));
    }

    @Test
    void aceitaJpegValidoERetornaMultiplosPrecos() throws Exception {
        when(leitor.executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/jpeg")))
                .thenReturn(List.of(new BigDecimal("13.98"), new BigDecimal("14.90")));

        mvc.perform(requisicao("image/jpeg", new byte[] { 1, 2 }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precos.length()").value(2));
    }

    @Test
    void listaVaziaEUmSucesso() throws Exception {
        when(leitor.executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/png")))
                .thenReturn(List.of());

        mvc.perform(requisicao("image/png", new byte[] { 1 }))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precos").isEmpty());
    }

    @Test
    void rejeitaArquivoVazioETipoNaoPermitido() throws Exception {
        mvc.perform(multipart("/api/familias/{familiaId}/listas/{listaId}/compra/itens/{itemId}/leitura-preco-ia", familiaId,
                listaId, itemId)).andExpect(status().isBadRequest());
        mvc.perform(requisicao("image/png", new byte[0])).andExpect(status().isBadRequest());
        mvc.perform(requisicao("image/gif", new byte[] { 1 })).andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaImagemMaiorQueDoisMegabytes() throws Exception {
        mvc.perform(requisicao("image/png", new byte[2 * 1024 * 1024 + 1])).andExpect(status().isBadRequest());
    }

    @Test
    void mapeiaParticipanteAusenteESemCapacidadeOperacionalComMesmoPadraoExistente() throws Exception {
        doThrow(new PresencaOperacionalObrigatoriaException()).when(leitor)
                .executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/png"));
        mvc.perform(requisicao("image/png", new byte[] { 1 })).andExpect(status().isConflict());

        doThrow(new UsuarioNaoParticipaDaCompraException()).when(leitor)
                .executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/png"));
        mvc.perform(requisicao("image/png", new byte[] { 1 })).andExpect(status().isForbidden());
    }

    @Test
    void mapeiaIndisponibilidadeTimeoutProvedorERespostaInvalidaSemDetalheInterno() throws Exception {
        assertErro(new LeituraPrecoIaIndisponivelException("chave secreta"), 503, "LEITURA_PRECO_IA_INDISPONIVEL");
        assertErro(new LeituraPrecoIaTimeoutException("detalhe", new RuntimeException()), 504, "LEITURA_PRECO_IA_TIMEOUT");
        assertErro(new LeituraPrecoIaProvedorException("detalhe", new RuntimeException()), 502, "LEITURA_PRECO_IA_FALHOU");
        assertErro(new LeituraPrecoIaRespostaInvalidaException("detalhe"), 502, "LEITURA_PRECO_IA_FALHOU");
    }

    private void assertErro(RuntimeException exception, int status, String erro) throws Exception {
        doThrow(exception).when(leitor).executar(eq(usuarioId), eq(familiaId), eq(listaId), eq(itemId), any(), eq("image/png"));
        mvc.perform(requisicao("image/png", new byte[] { 1 }))
                .andExpect(status().is(status)).andExpect(jsonPath("$.erro").value(erro))
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalhe"))));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder requisicao(String contentType,
            byte[] conteudo) {
        return multipart("/api/familias/{familiaId}/listas/{listaId}/compra/itens/{itemId}/leitura-preco-ia", familiaId,
                listaId, itemId).file(new MockMultipartFile("imagem", "etiqueta", contentType, conteudo));
    }
}
