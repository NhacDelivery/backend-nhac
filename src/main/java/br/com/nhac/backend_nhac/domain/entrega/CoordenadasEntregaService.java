package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordenadasEntregaService {
  private final PedidoRepository pedidos;
  private final LojaAccessService lojas;

  public CoordenadasEntregaService(PedidoRepository pedidos, LojaAccessService lojas) {
    this.pedidos = pedidos;
    this.lojas = lojas;
  }

  @Transactional
  public void corrigir(String id, Usuario usuario, double lat, double lng) {
    if (usuario == null || !usuario.isAtivo()) {
      throw new AcessoNegadoException("É necessário estar autenticado.");
    }
    var pedido =
        pedidos
            .findLockedById(id)
            .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
    boolean motoboy =
        pedido.getEntregador() != null
            && pedido.getEntregador().getUsuario().getId().equals(usuario.getId());
    if (!motoboy
        && !usuario.getId().equals(pedido.getUsuarioId())
        && usuario.getPapel() != Papel.ADMIN
        && !lojas.temAcessoALoja(usuario, pedido.getLoja().getId()))
      throw new AcessoNegadoException("Sem acesso ao destino.");
    if (pedido.getStatus() != StatusPedido.PREPARANDO
        && pedido.getStatus() != StatusPedido.SAIU_ENTREGA)
      throw new RegraDeNegocioException(
          "O destino só pode ser corrigido durante uma entrega ativa.");
    if (!Double.isFinite(lat)
        || !Double.isFinite(lng)
        || Math.abs(lat) > 90
        || Math.abs(lng) > 180
        || (lat == 0 && lng == 0)) throw new RegraDeNegocioException("Coordenadas inválidas.");
    pedido.setEntregaLatitude(lat);
    pedido.setEntregaLongitude(lng);
    pedidos.save(pedido);
  }
}
