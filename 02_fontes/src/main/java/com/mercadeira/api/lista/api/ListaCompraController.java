package com.mercadeira.api.lista.api;

import java.util.List;
import java.util.UUID;

import com.mercadeira.api.autenticacao.security.UsuarioAutenticado;
import com.mercadeira.api.lista.application.AdicionarItemLista;
import com.mercadeira.api.lista.application.AdicionarParticipanteLista;
import com.mercadeira.api.lista.application.ConsultarListaCompra;
import com.mercadeira.api.lista.application.CriarListaCompra;
import com.mercadeira.api.lista.application.EditarItemLista;
import com.mercadeira.api.lista.application.ListarItensLista;
import com.mercadeira.api.lista.application.ListarListasFamilia;
import com.mercadeira.api.lista.application.ListarParticipantesLista;
import com.mercadeira.api.lista.application.RemoverItemLista;
import com.mercadeira.api.lista.application.RemoverParticipanteLista;
import com.mercadeira.api.lista.application.ReordenarItensLista;
import com.mercadeira.api.lista.repository.ListaCompraRepository;
import com.mercadeira.api.lista.repository.ParticipanteListaRepository;
import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/familias/{familiaId}/listas")
public class ListaCompraController {
    private final UsuarioAutenticado usuario;
    private final CriarListaCompra criar; private final ListarListasFamilia listar;
    private final ConsultarListaCompra consultar; private final ListarParticipantesLista participantes;
    private final AdicionarParticipanteLista adicionarParticipante; private final RemoverParticipanteLista removerParticipante;
    private final ListarItensLista itens; private final AdicionarItemLista adicionarItem;
    private final EditarItemLista editarItem; private final RemoverItemLista removerItem; private final ReordenarItensLista reordenar;
    private final ListaCompraRepository listaRepository; private final MembroFamiliaRepository membroRepository; private final ParticipanteListaRepository participanteRepository;

    public ListaCompraController(UsuarioAutenticado usuario, CriarListaCompra criar, ListarListasFamilia listar,
            ConsultarListaCompra consultar, ListarParticipantesLista participantes, AdicionarParticipanteLista adicionarParticipante,
            RemoverParticipanteLista removerParticipante, ListarItensLista itens, AdicionarItemLista adicionarItem,
            EditarItemLista editarItem, RemoverItemLista removerItem, ReordenarItensLista reordenar, ListaCompraRepository listaRepository, MembroFamiliaRepository membroRepository, ParticipanteListaRepository participanteRepository) {
        this.usuario = usuario; this.criar = criar; this.listar = listar; this.consultar = consultar;
        this.participantes = participantes; this.adicionarParticipante = adicionarParticipante; this.removerParticipante = removerParticipante;
        this.itens = itens; this.adicionarItem = adicionarItem; this.editarItem = editarItem; this.removerItem = removerItem; this.reordenar = reordenar;
        this.listaRepository = listaRepository; this.membroRepository = membroRepository; this.participanteRepository = participanteRepository;
    }

    @GetMapping public List<ListaCompraResponse> listar(@PathVariable UUID familiaId) {
        return listar.listar(usuario.getId(), familiaId).stream().map(ListaCompraResponse::from).toList();
    }
    @PostMapping public ResponseEntity<ListaCompraResponse> criar(@PathVariable UUID familiaId, @Valid @RequestBody ListaCompraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ListaCompraResponse.from(
                criar.criar(usuario.getId(), familiaId, request.nome(), request.categoria(), request.estabelecimento())));
    }
    @GetMapping("/{listaId}") public ListaCompraDetalheResponse consultar(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        consultar.consultar(usuario.getId(), familiaId, listaId);
        var lista = listaRepository.findDetalheById(listaId).orElseThrow();
        var membro = membroRepository.findByFamilia_IdAndUsuario_IdAndStatus(familiaId, usuario.getId(), StatusMembroFamilia.ATIVO).orElseThrow();
        boolean participanteAtivo = participanteRepository.findByListaCompra_IdAndMembroFamilia_Id(listaId, membro.getId()).map(p -> p.getSaiuEm() == null).orElse(false);
        return ListaCompraDetalheResponse.from(lista, membro, participanteAtivo);
    }
    @GetMapping("/{listaId}/participantes") public List<ParticipanteListaResponse> participantes(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        return participantes.listar(usuario.getId(), familiaId, listaId).stream().map(ParticipanteListaResponse::from).toList();
    }
    @PostMapping("/{listaId}/participantes") public ResponseEntity<ParticipanteListaResponse> adicionarParticipante(@PathVariable UUID familiaId, @PathVariable UUID listaId, @Valid @RequestBody ParticipanteListaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ParticipanteListaResponse.from(adicionarParticipante.adicionar(usuario.getId(), familiaId, listaId, request.membroFamiliaId())));
    }
    @DeleteMapping("/{listaId}/participantes/{membroFamiliaId}") public ResponseEntity<Void> removerParticipante(@PathVariable UUID familiaId, @PathVariable UUID listaId, @PathVariable UUID membroFamiliaId) {
        removerParticipante.remover(usuario.getId(), familiaId, listaId, membroFamiliaId); return ResponseEntity.noContent().build();
    }
    @GetMapping("/{listaId}/itens") public List<ItemListaResponse> itens(@PathVariable UUID familiaId, @PathVariable UUID listaId) {
        return itens.listar(usuario.getId(), familiaId, listaId).stream().map(ItemListaResponse::from).toList();
    }
    @PostMapping("/{listaId}/itens") public ResponseEntity<ItemListaResponse> adicionarItem(@PathVariable UUID familiaId, @PathVariable UUID listaId, @Valid @RequestBody ItemListaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ItemListaResponse.from(adicionarItem.adicionar(usuario.getId(), familiaId, listaId, request.descricao(), request.quantidade(), request.unidadeMedida(), request.marca(), request.observacoes())));
    }
    @PutMapping("/{listaId}/itens/{itemId}") public ItemListaResponse editarItem(@PathVariable UUID familiaId, @PathVariable UUID listaId, @PathVariable UUID itemId, @Valid @RequestBody ItemListaRequest request) {
        return ItemListaResponse.from(editarItem.editar(usuario.getId(), familiaId, listaId, itemId, request.descricao(), request.quantidade(), request.unidadeMedida(), request.marca(), request.observacoes()));
    }
    @DeleteMapping("/{listaId}/itens/{itemId}") public ResponseEntity<Void> removerItem(@PathVariable UUID familiaId, @PathVariable UUID listaId, @PathVariable UUID itemId) {
        removerItem.remover(usuario.getId(), familiaId, listaId, itemId); return ResponseEntity.noContent().build();
    }
    @PutMapping("/{listaId}/itens/ordem") public ResponseEntity<Void> reordenar(@PathVariable UUID familiaId, @PathVariable UUID listaId, @Valid @RequestBody ReordenarItensRequest request) {
        reordenar.reordenar(usuario.getId(), familiaId, listaId, request.itens()); return ResponseEntity.noContent().build();
    }
}
