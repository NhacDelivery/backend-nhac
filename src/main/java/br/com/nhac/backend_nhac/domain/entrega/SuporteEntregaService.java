package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuporteEntregaService {
  private final br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository entregadores;
  private final OfertaEntregaRepository ofertas;
  private final org.springframework.context.ApplicationEventPublisher eventos;
  private final PedidoRepository pedidos;
  private final SolicitacaoSuporteRepository suporte;

  public SuporteEntregaService(
      PedidoRepository pedidos,
      SolicitacaoSuporteRepository suporte,
      br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository entregadores,
      OfertaEntregaRepository ofertas,
      org.springframework.context.ApplicationEventPublisher eventos) {
    this.entregadores = entregadores;
    this.ofertas = ofertas;
    this.eventos = eventos;
    this.pedidos = pedidos;
    this.suporte = suporte;
  }

  @Transactional
  public SolicitacaoSuporte abrir(
      String pedidoId, Usuario usuario, SuporteEntregaController.AbrirDTO dto) {
    Pedido pedido =
        pedidos
            .findLockedById(pedidoId)
            .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
    String id = dto.id();
    var anterior = suporte.findById(id);
    if (anterior.isPresent()) {
      var ticket = anterior.get();
      if (!ticket.getUsuarioId().equals(usuario.getId()) || !ticket.getPedidoId().equals(pedidoId))
        throw new AcessoNegadoException("Protocolo de outra solicitação.");
      if (!ticket.getMotivo().equals(dto.motivo())
          || !ticket.getDescricao().equals(dto.descricao().trim()))
        throw new RegraDeNegocioException(
            "Protocolo já usado com outros dados. Consulte a solicitação existente.");
      return ticket;
    }
    if (pedido.getEntregador() == null
        || !pedido.getEntregador().getUsuario().getId().equals(usuario.getId()))
      throw new AcessoNegadoException("Esta corrida não pertence a você.");
    if (pedido.getStatus() != StatusPedido.PREPARANDO
        && pedido.getStatus() != StatusPedido.SAIU_ENTREGA)
      throw new RegraDeNegocioException("Abra o atendimento durante uma corrida ativa.");
    var ticket = new SolicitacaoSuporte();
    ticket.setId(id);
    ticket.setPedidoId(pedidoId);
    ticket.setUsuarioId(usuario.getId());
    ticket.setMotivo(dto.motivo());
    ticket.setDescricao(dto.descricao().trim());
    ticket.setEtapa(pedido.getColetadoEm() == null ? "ANTES_COLETA" : "APOS_COLETA");
    ticket.setStatus("ABERTO");
    ticket.setCriadoEm(Instant.now());
    ticket.setResposta(
        pedido.getColetadoEm() == null
            ? "Solicitação recebida. Você continua responsável até a confirmação do atendimento;"
                  + " não colete se não puder seguir."
            : "Solicitação recebida. O pedido continua sob sua responsabilidade; preserve-o e"
                  + " aguarde instruções para transferência ou devolução.");
    return suporte.save(ticket);
  }

  @Transactional(readOnly = true)
  public List<SolicitacaoSuporte> listar(String pedidoId, Usuario usuario) {
    return suporte.findByPedidoIdAndUsuarioIdOrderByCriadoEmDesc(pedidoId, usuario.getId());
  }

  @Transactional
  public SolicitacaoSuporte executar(String id, SuporteEntregaController.AcaoDTO dto) {
    var ticket =
        suporte
            .findLockedById(id)
            .orElseThrow(() -> new IdNaoEncontradoException("Protocolo não encontrado."));
    if ("RETIRADO".equals(ticket.getStatus()) || "TRANSFERIDO".equals(ticket.getStatus())) {
      if (!dto.acao().equals(ticket.getAcao())
          || !java.util.Objects.equals(dto.novoUsuarioId(), ticket.getResponsavelUsuarioId()))
        throw new RegraDeNegocioException("Este protocolo já foi concluído com outra ação.");
      return ticket;
    }
    var usuarios = new java.util.TreeSet<String>();
    usuarios.add(ticket.getUsuarioId());
    if (dto.novoUsuarioId() != null) usuarios.add(dto.novoUsuarioId());
    for (String usuarioId : usuarios)
      entregadores
          .findLockedByUsuarioId(usuarioId)
          .orElseThrow(() -> new IdNaoEncontradoException("Entregador não encontrado."));
    var pedido =
        pedidos
            .findLockedById(ticket.getPedidoId())
            .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
    if (pedido.getEntregador() == null
        || !pedido.getEntregador().getUsuario().getId().equals(ticket.getUsuarioId()))
      throw new RegraDeNegocioException(
          "O responsável pela corrida já mudou. Atualize o atendimento.");
    if (pedido.getStatus() != StatusPedido.PREPARANDO
        && pedido.getStatus() != StatusPedido.SAIU_ENTREGA)
      throw new RegraDeNegocioException("A corrida já foi encerrada.");
    var anterior = pedido.getEntregador();
    if ("RETIRAR".equals(dto.acao())) {
      if (dto.novoUsuarioId() != null)
        throw new RegraDeNegocioException("Retirada não recebe um novo responsável.");
      if (pedido.getColetadoEm() != null)
        throw new RegraDeNegocioException(
            "Após a coleta é necessária transferência com confirmação da entrega física.");
      pedido.setEntregador(null);
      ticket.setStatus("RETIRADO");
      ticket.setResposta(
          "Retirada confirmada. Você não é mais responsável. A loja acompanha a busca de outro"
              + " entregador.");
      eventos.publishEvent(
          new br.com.nhac.backend_nhac.domain.pedido.PedidoPreparandoEvent(pedido.getId()));
    } else {
      if (dto.novoUsuarioId() == null || dto.novoUsuarioId().equals(ticket.getUsuarioId()))
        throw new RegraDeNegocioException("Informe outro entregador.");
      if (pedido.getColetadoEm() != null && !dto.entregaFisicaConfirmada())
        throw new RegraDeNegocioException(
            "Confirme a entrega física do pedido ao novo responsável.");
      var novo =
          entregadores
              .findByUsuarioId(dto.novoUsuarioId())
              .orElseThrow(() -> new IdNaoEncontradoException("Novo entregador não encontrado."));
      if (!novo.isAtivo()
          || novo.getStatusOperacional()
              != br.com.nhac.backend_nhac.domain.entregador.StatusOperacional.ONLINE
          || pedidos.existsByEntregadorIdAndStatusIn(
              novo.getId(), List.of(StatusPedido.PREPARANDO, StatusPedido.SAIU_ENTREGA)))
        throw new RegraDeNegocioException("Novo entregador indisponível.");
      pedido.setEntregador(novo);
      novo.setStatusOperacional(
          br.com.nhac.backend_nhac.domain.entregador.StatusOperacional.EM_ENTREGA);
      ticket.setStatus("TRANSFERIDO");
      ticket.setResponsavelUsuarioId(dto.novoUsuarioId());
      ticket.setResposta(
          "Transferência confirmada. Responsável: "
              + novo.getUsuario().getNome()
              + ". Você foi liberado da corrida.");
    }
    anterior.setStatusOperacional(
        br.com.nhac.backend_nhac.domain.entregador.StatusOperacional.OFFLINE);
    for (var oferta : ofertas.findByPedidoIdAndStatus(pedido.getId(), StatusOferta.ACEITA))
      oferta.setStatus(StatusOferta.RECUSADA);
    for (var oferta : ofertas.findByPedidoIdAndStatus(pedido.getId(), StatusOferta.PENDENTE))
      oferta.setStatus(StatusOferta.EXPIRADA);
    ticket.setAcao(dto.acao());
    ticket.setRespondidoEm(Instant.now());
    pedidos.save(pedido);
    return suporte.save(ticket);
  }

  @Transactional
  public SolicitacaoSuporte responder(String id, String resposta) {
    var ticket =
        suporte
            .findLockedById(id)
            .orElseThrow(() -> new IdNaoEncontradoException("Protocolo não encontrado."));
    ticket.setResposta(resposta.trim());
    if (!"RETIRADO".equals(ticket.getStatus()) && !"TRANSFERIDO".equals(ticket.getStatus()))
      ticket.setStatus("RESPONDIDO");
    ticket.setRespondidoEm(Instant.now());
    return suporte.save(ticket);
  }
}
