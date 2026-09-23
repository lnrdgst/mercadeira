package com.mercadeira.api.familia.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.mercadeira.api.familia.domain.Familia;
import com.mercadeira.api.familia.domain.MembroFamilia;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.usuario.application.CadastrarUsuario;
import com.mercadeira.api.usuario.domain.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class TransferirAdministracaoFamiliaIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @Autowired private CadastrarUsuario cadastrarUsuario;
    @Autowired private CriarFamilia criarFamilia;
    @Autowired private MembroFamiliaRepository membros;
    @Autowired private TransferirAdministracaoFamilia transferir;

    @Test
    void transferenciasConcorrentesMantemExatamenteUmAdministrador() throws Exception {
        Usuario ana = usuario("Ana");
        Familia familia = criarFamilia.criar(ana.getId(), "Oliveira");
        MembroFamilia bia = membros.saveAndFlush(MembroFamilia.criarMembro(familia, usuario("Bia"), java.time.Instant.now()));
        MembroFamilia carla = membros.saveAndFlush(MembroFamilia.criarMembro(familia, usuario("Carla"), java.time.Instant.now()));
        CountDownLatch iniciar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var paraBia = executor.submit(() -> executarQuandoLiberado(iniciar, ana.getId(), familia.getId(), bia.getId()));
            var paraCarla = executor.submit(() -> executarQuandoLiberado(iniciar, ana.getId(), familia.getId(), carla.getId()));
            iniciar.countDown();
            assertThat(paraBia.get(20, TimeUnit.SECONDS) + paraCarla.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        var administradores = membros.findByFamilia_IdAndStatusOrderByUsuario_NomeAscIdAsc(familia.getId(), StatusMembroFamilia.ATIVO)
                .stream().filter(membro -> membro.getPapel() == PapelMembroFamilia.ADMINISTRADOR).toList();
        assertThat(administradores).hasSize(1);
        assertThat(administradores.getFirst().getId()).isIn(bia.getId(), carla.getId());
    }

    private int executarQuandoLiberado(CountDownLatch iniciar, UUID executorId, UUID familiaId, UUID destinoId) {
        try {
            if (!iniciar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Teste não iniciou.");
            transferir.transferir(familiaId, executorId, destinoId);
            return 1;
        } catch (TransferenciaAdministracaoInvalidaException exception) {
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Usuario usuario(String nome) {
        return cadastrarUsuario.cadastrar(nome, nome.toLowerCase() + UUID.randomUUID() + "@test.local", "senha-original");
    }
}
