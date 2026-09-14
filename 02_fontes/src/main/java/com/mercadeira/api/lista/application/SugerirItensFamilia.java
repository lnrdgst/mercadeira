package com.mercadeira.api.lista.application;

import java.util.List;
import java.util.UUID;
import com.mercadeira.api.lista.repository.SugestoesItensRepository;
import com.mercadeira.api.lista.repository.SugestoesItensRepository.Sugestao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SugerirItensFamilia {
    private final ValidadorAcessoListaCompra acesso;
    private final SugestoesItensRepository repository;
    public SugerirItensFamilia(ValidadorAcessoListaCompra acesso, SugestoesItensRepository repository) {
        this.acesso = acesso; this.repository = repository;
    }
    @Transactional(readOnly = true)
    public List<Sugestao> buscar(UUID usuarioId, UUID familiaId, String termo) {
        acesso.membroAtivoNaFamilia(usuarioId, familiaId);
        if (termo == null || termo.length() > 200) throw new IllegalArgumentException("Busca limitada a 200 caracteres.");
        return repository.buscar(familiaId, termo);
    }
}
