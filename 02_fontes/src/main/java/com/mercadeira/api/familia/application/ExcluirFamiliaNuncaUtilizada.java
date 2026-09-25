package com.mercadeira.api.familia.application;
import java.util.UUID;
import com.mercadeira.api.compra.repository.CompraRepository;
import com.mercadeira.api.familia.domain.PapelMembroFamilia;
import com.mercadeira.api.familia.domain.StatusMembroFamilia;
import com.mercadeira.api.familia.repository.*;
import com.mercadeira.api.lista.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class ExcluirFamiliaNuncaUtilizada {
 private final FamiliaRepository familias; private final MembroFamiliaRepository membros; private final SolicitacaoEntradaFamiliaRepository solicitacoes; private final ListaCompraRepository listas; private final ItemListaRepository itens; private final ParticipanteListaRepository participantes; private final CompraRepository compras;
 public ExcluirFamiliaNuncaUtilizada(FamiliaRepository f,MembroFamiliaRepository m,SolicitacaoEntradaFamiliaRepository s,ListaCompraRepository l,ItemListaRepository i,ParticipanteListaRepository p,CompraRepository c){familias=f;membros=m;solicitacoes=s;listas=l;itens=i;participantes=p;compras=c;}
 @Transactional public void excluir(UUID familiaId, UUID usuarioId){
  var familia=familias.findByIdForUpdate(familiaId).orElseThrow(ExclusaoFamiliaInvalidaException::new);
  var admins=membros.findByFamilia_IdAndStatusAndPapel(familiaId,StatusMembroFamilia.ATIVO,PapelMembroFamilia.ADMINISTRADOR);
  if(admins.size()!=1||!admins.getFirst().getUsuario().getId().equals(usuarioId)||compras.existsByListaCompra_Familia_Id(familiaId)) throw new ExclusaoFamiliaInvalidaException();
  for(var lista:listas.findByFamiliaIdForUpdate(familiaId)){ if(compras.findByListaCompra_Id(lista.getId()).isPresent()) throw new ExclusaoFamiliaInvalidaException(); participantes.deleteByListaCompra_Id(lista.getId()); itens.deleteByListaCompra_Id(lista.getId()); }
  solicitacoes.deleteByFamilia_Id(familiaId); listas.deleteByFamilia_Id(familiaId); membros.deleteByFamilia_Id(familiaId); familias.delete(familia);
 }
}
