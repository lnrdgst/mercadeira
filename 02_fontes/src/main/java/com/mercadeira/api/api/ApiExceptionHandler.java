package com.mercadeira.api.api;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mercadeira.api.compra.domain.FinalizacaoCompraInvalidaException;
import com.mercadeira.api.compra.application.CompraComRemocaoPendenteException;
import com.mercadeira.api.compra.application.UsuarioNaoPodeDecidirRemocaoItemCompraException;
import com.mercadeira.api.compra.application.ResponsavelRemocaoItemCompraInvalidoException;
import com.mercadeira.api.autenticacao.application.CredenciaisInvalidasException;
import com.mercadeira.api.autenticacao.security.UsuarioNaoAutenticadoException;
import com.mercadeira.api.familia.application.CodigoFamiliaInvalidoException;
import com.mercadeira.api.familia.application.FamiliaInativaException;
import com.mercadeira.api.familia.application.MembroSemPermissaoException;
import com.mercadeira.api.familia.application.RemocaoIntegranteInvalidaException;
import com.mercadeira.api.familia.application.SaidaFamiliaInvalidaException;
import com.mercadeira.api.familia.application.ExclusaoFamiliaInvalidaException;
import com.mercadeira.api.familia.application.SolicitacaoNaoEncontradaException;
import com.mercadeira.api.familia.application.SolicitacaoNaoPendenteException;
import com.mercadeira.api.familia.application.SolicitacaoPendenteJaExisteException;
import com.mercadeira.api.familia.application.SolicitanteJaPossuiVinculoAtivoException;
import com.mercadeira.api.familia.application.UsuarioNaoEncontradoException;
import com.mercadeira.api.familia.application.TransferenciaAdministracaoInvalidaException;
import com.mercadeira.api.usuario.application.DadosUsuarioInvalidosException;
import com.mercadeira.api.usuario.application.EmailJaCadastradoException;
import com.mercadeira.api.lista.application.ItemListaNaoEncontradoException;
import com.mercadeira.api.lista.application.ListaCompraNaoEncontradaException;
import com.mercadeira.api.lista.application.MembroFamiliaInvalidoException;
import com.mercadeira.api.lista.application.UsuarioNaoParticipaDaListaException;
import com.mercadeira.api.lista.application.ItemListaJaRemovidoException;
import com.mercadeira.api.lista.application.ListaCompraForaDePreparacaoException;
import com.mercadeira.api.lista.application.ListaCompraJaUtilizadaException;
import com.mercadeira.api.lista.application.CriadorListaNaoPodeSerRemovidoException;
import com.mercadeira.api.lista.application.ParticipanteListaNaoEncontradoException;
import com.mercadeira.api.lista.application.OrdemItensInvalidaException;
import com.mercadeira.api.lista.application.ItensForaCompraInvalidosException;
import com.mercadeira.api.compra.application.CompraListaInconsistenteException;
import com.mercadeira.api.compra.application.CompraNaoEncontradaException;
import com.mercadeira.api.compra.application.CompraForaDeAndamentoException;
import com.mercadeira.api.compra.application.ItemCompraNaoEncontradoException;
import com.mercadeira.api.compra.application.ListaCompraSemItensException;
import com.mercadeira.api.compra.application.ListaCompraSemParticipantesException;
import com.mercadeira.api.compra.application.UsuarioNaoParticipaDaCompraException;
import com.mercadeira.api.compra.application.AutoridadePresencaException;
import com.mercadeira.api.compra.application.ConflitoPresencaException;
import com.mercadeira.api.compra.domain.TransicaoStatusItemCompraInvalidaException;
import com.mercadeira.api.compra.domain.RestauracaoItemCompraInvalidaException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroApiResponse> tratarValidacao(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : exception.getBindingResult().getFieldErrors()) {
            campos.put(erro.getField(), erro.getDefaultMessage());
        }
        return resposta(HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA", "Payload invalido.", request, campos);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, DadosUsuarioInvalidosException.class,
            IllegalArgumentException.class, CodigoFamiliaInvalidoException.class })
    ResponseEntity<ErroApiResponse> tratarRequisicaoInvalida(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", "Requisicao invalida.", request, Map.of());
    }

    @ExceptionHandler({ CredenciaisInvalidasException.class, UsuarioNaoAutenticadoException.class })
    ResponseEntity<ErroApiResponse> tratarNaoAutenticado(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Credenciais invalidas.", request, Map.of());
    }

    @ExceptionHandler({ MembroSemPermissaoException.class, MembroFamiliaInvalidoException.class,
            UsuarioNaoParticipaDaListaException.class, UsuarioNaoParticipaDaCompraException.class, UsuarioNaoPodeDecidirRemocaoItemCompraException.class, AutoridadePresencaException.class })
    ResponseEntity<ErroApiResponse> tratarSemPermissao(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Acesso negado.", request, Map.of());
    }

    @ExceptionHandler({ UsuarioNaoEncontradoException.class, SolicitacaoNaoEncontradaException.class,
            ListaCompraNaoEncontradaException.class, ItemListaNaoEncontradoException.class,
            CompraNaoEncontradaException.class, ItemCompraNaoEncontradoException.class })
    ResponseEntity<ErroApiResponse> tratarNaoEncontrado(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", "Recurso nao encontrado.", request, Map.of());
    }

    @ExceptionHandler({ EmailJaCadastradoException.class, SolicitacaoPendenteJaExisteException.class,
            SolicitacaoNaoPendenteException.class,
            SolicitanteJaPossuiVinculoAtivoException.class,
            FamiliaInativaException.class, ListaCompraForaDePreparacaoException.class,
            ItemListaJaRemovidoException.class, CriadorListaNaoPodeSerRemovidoException.class,
            ParticipanteListaNaoEncontradoException.class, ListaCompraSemItensException.class,
            ListaCompraSemParticipantesException.class, CompraListaInconsistenteException.class,
            ListaCompraJaUtilizadaException.class,
            com.mercadeira.api.compra.application.PresencaOperacionalObrigatoriaException.class, RestauracaoItemCompraInvalidaException.class, FinalizacaoCompraInvalidaException.class, CompraComRemocaoPendenteException.class, CompraForaDeAndamentoException.class, TransicaoStatusItemCompraInvalidaException.class, ResponsavelRemocaoItemCompraInvalidoException.class, ConflitoPresencaException.class, TransferenciaAdministracaoInvalidaException.class, RemocaoIntegranteInvalidaException.class, SaidaFamiliaInvalidaException.class, ExclusaoFamiliaInvalidaException.class, ItensForaCompraInvalidosException.class })
    ResponseEntity<ErroApiResponse> tratarConflito(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, "CONFLITO_DE_ESTADO", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(OrdemItensInvalidaException.class)
    ResponseEntity<ErroApiResponse> tratarOrdemInvalida(OrdemItensInvalidaException exception, HttpServletRequest request) {
        return resposta(HttpStatus.BAD_REQUEST, "REQUISICAO_INVALIDA", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroApiResponse> tratarErroInesperado(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Ocorreu um erro interno.", request, Map.of());
    }

    private ResponseEntity<ErroApiResponse> resposta(
            HttpStatus status,
            String erro,
            String mensagem,
            HttpServletRequest request,
            Map<String, String> campos) {
        return ResponseEntity.status(status).body(new ErroApiResponse(
                clock.instant(), status.value(), erro, mensagem, request.getRequestURI(), campos));
    }
}
