package com.mercadeira.api.familia.application;

import java.time.Clock;
import java.util.UUID;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.NomeFamilia;
import com.mercadeira.api.familia.repository.FamiliaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.usuario.domain.Usuario;
import com.mercadeira.api.usuario.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CriarFamilia {

    private final UsuarioRepository usuarioRepository;
    private final FamiliaRepository familiaRepository;
    private final MembroFamiliaRepository membroFamiliaRepository;
    private final GeradorCodigoIngresso geradorCodigoIngresso;
    private final Clock clock;

    public CriarFamilia(
            UsuarioRepository usuarioRepository,
            FamiliaRepository familiaRepository,
            MembroFamiliaRepository membroFamiliaRepository,
            GeradorCodigoIngresso geradorCodigoIngresso,
            Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.familiaRepository = familiaRepository;
        this.membroFamiliaRepository = membroFamiliaRepository;
        this.geradorCodigoIngresso = geradorCodigoIngresso;
        this.clock = clock;
    }

    @Transactional
    public Familia criar(UUID usuarioId, String nome) {
        String nomeNormalizado = NomeFamilia.normalizar(nome);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsuarioNaoEncontradoException(usuarioId));

        Familia familia = Familia.criar(
                nomeNormalizado,
                geradorCodigoIngresso.gerar(),
                usuario,
                clock.instant());

        familiaRepository.save(familia);

        boolean primeiraFamiliaAtiva = membroFamiliaRepository
                .findByUsuario_IdAndStatusOrderByFamilia_NomeAsc(usuarioId, com.mercadeira.api.familia.domain.StatusMembroFamilia.ATIVO)
                .isEmpty();
        MembroFamilia membro = MembroFamilia.criarAdministrador(familia, usuario, clock.instant());
        if (primeiraFamiliaAtiva) {
            membro.tornarPrincipal(clock.instant());
        }
        membroFamiliaRepository.save(membro);

        return familia;
    }
}
