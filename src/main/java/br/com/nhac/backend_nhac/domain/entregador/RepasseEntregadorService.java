package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.exceptions.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepasseEntregadorService {
  private final RepasseEntregadorRepository repasses;
  private final PedidoRepository pedidos;
  private final EntregadorService entregadores;

  public RepasseEntregadorService(
      RepasseEntregadorRepository repasses,
      PedidoRepository pedidos,
      EntregadorService entregadores) {
    this.repasses = repasses;
    this.pedidos = pedidos;
    this.entregadores = entregadores;
  }

  @Transactional(readOnly = true)
  public org.springframework.data.domain.Page<ExtratoRepasseDTO> extrato(
      br.com.nhac.backend_nhac.domain.usuario.Usuario usuario, int page) {
    var entregador = entregadores.buscarPorUsuario(usuario);
    var historico =
        pedidos.findHistoricoDoEntregador(
            entregador.getId(),
            StatusPedido.ENTREGUE,
            org.springframework.data.domain.PageRequest.of(Math.max(0, page), 20));
    var registros =
        repasses.findAllById(historico.stream().map(Pedido::getId).toList()).stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    RepasseEntregador::getPedidoId, java.util.function.Function.identity()));
    return historico.map(p -> ExtratoRepasseDTO.de(p, registros.get(p.getId())));
  }

  @Transactional
  public RepasseEntregador apurar(String id, BigDecimal devido) {
    var pedido =
        pedidos
            .findLockedById(id)
            .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
    if (pedido.getStatus() != StatusPedido.ENTREGUE || pedido.getEntregador() == null)
      throw new RegraDeNegocioException("Só é possível apurar frete de corrida concluída.");
    var existente = repasses.findById(id);
    if (existente.isPresent()) {
      if (existente.get().getValorDevido().compareTo(devido) != 0)
        throw new RegraDeNegocioException("Este repasse já foi apurado com outro valor.");
      return existente.get();
    }
    var r = new RepasseEntregador();
    r.setPedidoId(id);
    r.setEntregadorId(pedido.getEntregador().getId());
    r.setValorDevido(devido);
    r.setApuradoEm(Instant.now());
    return repasses.save(r);
  }

  @Transactional
  public RepasseEntregador registrarPagamento(
      String id, String referencia, BigDecimal valor, Instant pagoEm) {
    var r =
        repasses
            .findLockedById(id)
            .orElseThrow(
                () ->
                    new IdNaoEncontradoException(
                        "Apure o valor devido antes de registrar o pagamento."));
    if (r.getReferenciaPagamento() != null) {
      if (r.getReferenciaPagamento().equals(referencia)
          && r.getValorPago().compareTo(valor) == 0
          && r.getPagoEm().equals(pagoEm)) return r;
      throw new RegraDeNegocioException("Este repasse já possui um pagamento registrado.");
    }
    if (repasses.existsByReferenciaPagamento(referencia))
      throw new RegraDeNegocioException("Referência de pagamento já utilizada.");
    if (valor.compareTo(r.getValorDevido()) != 0 || pagoEm.isAfter(Instant.now()))
      throw new RegraDeNegocioException("Informe o valor apurado e a data do pagamento efetivo.");
    r.setReferenciaPagamento(referencia);
    r.setValorPago(valor);
    r.setPagoEm(pagoEm);
    return repasses.save(r);
  }
}
