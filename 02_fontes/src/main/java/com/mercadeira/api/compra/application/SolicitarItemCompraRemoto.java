package com.mercadeira.api.compra.application;
import java.time.Clock; import java.util.UUID;
import com.mercadeira.api.compra.domain.*; import com.mercadeira.api.compra.repository.*; import com.mercadeira.api.familia.domain.StatusMembroFamilia; import com.mercadeira.api.familia.repository.MembroFamiliaRepository;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class SolicitarItemCompraRemoto {
 private final CompraRepository compras; private final MembroFamiliaRepository membros; private final ParticipanteCompraRepository participantes; private final SolicitacaoItemCompraRepository solicitacoes; private final Clock clock;
 public SolicitarItemCompraRemoto(CompraRepository c,MembroFamiliaRepository m,ParticipanteCompraRepository p,SolicitacaoItemCompraRepository s,Clock k){compras=c;membros=m;participantes=p;solicitacoes=s;clock=k;}
 @Transactional public SolicitacaoItemCompra solicitar(UUID usuarioId, UUID compraId, SolicitarItemCompraRemotoCommand dados){
  var compra=compras.findById(compraId).orElseThrow(() -> new CompraNaoEncontradaException(compraId)); if(compra.getStatus()!=StatusCompra.EM_ANDAMENTO) throw new CompraForaDeAndamentoException();
  var familiaId=compra.getListaCompra().getFamilia().getId(); var membro=membros.findByFamilia_IdAndUsuario_IdAndStatus(familiaId,usuarioId,StatusMembroFamilia.ATIVO).orElseThrow(UsuarioNaoParticipaDaCompraException::new);
  var participante=participantes.findByCompra_IdAndMembroFamilia_Id(compraId,membro.getId()).orElseThrow(UsuarioNaoParticipaDaCompraException::new);
  if(participante.getPresencaOperacional()!=PresencaOperacional.NAO_PRESENTE) throw new PresencaOperacionalObrigatoriaException();
  return solicitacoes.save(SolicitacaoItemCompra.criar(compra,participante,dados.descricao(),dados.quantidade(),dados.unidadeMedida(),clock.instant())); }
}
