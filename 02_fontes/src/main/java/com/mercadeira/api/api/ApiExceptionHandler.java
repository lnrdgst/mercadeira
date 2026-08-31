package com.mercadeira.api.api;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mercadeira.api.autenticacao.application.CredenciaisInvalidasException;
import com.mercadeira.api.autenticacao.security.UsuarioNaoAutenticadoException;
import com.mercadeira.api.familia.application.CodigoFamiliaInvalidoException;
import com.mercadeira.api.familia.application.FamiliaInativaException;
import com.mercadeira.api.familia.application.MembroSemPermissaoException;
import com.mercadeira.api.familia.application.SolicitacaoNaoEncontradaException;
import com.mercadeira.api.familia.application.SolicitacaoNaoPendenteException;
import com.mercadeira.api.familia.application.SolicitacaoPendenteJaExisteException;
import com.mercadeira.api.familia.application.SolicitanteJaPossuiVinculoAtivoException;
import com.mercadeira.api.familia.application.UsuarioNaoEncontradoException;
import com.mercadeira.api.usuario.application.DadosUsuarioInvalidosException;
import com.mercadeira.api.usuario.application.EmailJaCadastradoException;
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

    @ExceptionHandler(MembroSemPermissaoException.class)
    ResponseEntity<ErroApiResponse> tratarSemPermissao(MembroSemPermissaoException exception, HttpServletRequest request) {
        return resposta(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", "Acesso negado.", request, Map.of());
    }

    @ExceptionHandler({ UsuarioNaoEncontradoException.class, SolicitacaoNaoEncontradaException.class })
    ResponseEntity<ErroApiResponse> tratarNaoEncontrado(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", "Recurso nao encontrado.", request, Map.of());
    }

    @ExceptionHandler({ EmailJaCadastradoException.class, SolicitacaoPendenteJaExisteException.class,
            SolicitacaoNaoPendenteException.class,
            SolicitanteJaPossuiVinculoAtivoException.class,
            FamiliaInativaException.class })
    ResponseEntity<ErroApiResponse> tratarConflito(Exception exception, HttpServletRequest request) {
        return resposta(HttpStatus.CONFLICT, "CONFLITO_DE_ESTADO", exception.getMessage(), request, Map.of());
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
