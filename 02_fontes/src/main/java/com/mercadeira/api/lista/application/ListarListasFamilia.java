package com.mercadeira.api.lista.application;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.lista.domain.ListaCompra;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListarListasFamilia {
    private final ValidadorAcessoListaCompra acesso;
    private final ListaCompraRepository listaRepository;
    public ListarListasFamilia(ValidadorAcessoListaCompra acesso, ListaCompraRepository listaRepository) {
        this.acesso = acesso; this.listaRepository = listaRepository;
    }
    @Transactional(readOnly = true)
    public List<ListaCompra> listar(UUID usuarioId) {
        MembroFamilia membro = acesso.membroAtivoDoUsuario(usuarioId);
        return listaRepository.findByFamilia_IdOrderByAtualizadaEmDesc(membro.getFamilia().getId());
    }
}
