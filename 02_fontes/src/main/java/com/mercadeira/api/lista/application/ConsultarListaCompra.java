package com.mercadeira.api.lista.application;

import java.util.UUID;

import com.mercadeira.api.lista.domain.ListaCompra;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultarListaCompra {
    private final ValidadorAcessoListaCompra acesso;
    public ConsultarListaCompra(ValidadorAcessoListaCompra acesso) { this.acesso = acesso; }
    @Transactional(readOnly = true)
    public ListaCompra consultar(UUID usuarioId, UUID familiaId, UUID listaId) {
        ListaCompra lista = acesso.lista(familiaId, listaId);
        acesso.validarMembroDaFamilia(usuarioId, lista);
        return lista;
    }
}
