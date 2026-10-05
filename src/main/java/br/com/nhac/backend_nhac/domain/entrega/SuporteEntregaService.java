package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class SuporteEntregaService {
    private final PedidoRepository pedidos;
    private final SolicitacaoSuporteRepository suporte;
    public SuporteEntregaService(PedidoRepository pedidos, SolicitacaoSuporteRepository suporte) {
        this.pedidos=pedidos; this.suporte=suporte;
    }
    @Transactional
    public SolicitacaoSuporte abrir(String pedidoId, Usuario usuario, SuporteEntregaController.AbrirDTO dto) {
        Pedido pedido = pedidos.findLockedById(pedidoId).orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
        if (pedido.getEntregador()==null || !pedido.getEntregador().getUsuario().getId().equals(usuario.getId()))
            throw new AcessoNegadoException("Esta corrida não pertence a você.");
        if (pedido.getStatus()!=StatusPedido.PREPARANDO && pedido.getStatus()!=StatusPedido.SAIU_ENTREGA)
            throw new RegraDeNegocioException("Abra o atendimento durante uma corrida ativa.");
        String id = dto.id();
        var anterior = suporte.findById(id);
        if (anterior.isPresent()) {
            var ticket = anterior.get();
            if (!ticket.getUsuarioId().equals(usuario.getId()) || !ticket.getPedidoId().equals(pedidoId))
                throw new AcessoNegadoException("Protocolo de outra solicitação.");
            return ticket;
        }
        var ticket = new SolicitacaoSuporte();
        ticket.setId(id); ticket.setPedidoId(pedidoId); ticket.setUsuarioId(usuario.getId());
        ticket.setMotivo(dto.motivo()); ticket.setDescricao(dto.descricao().trim());
        ticket.setEtapa(pedido.getColetadoEm()==null ? "ANTES_COLETA" : "APOS_COLETA");
        ticket.setStatus("ABERTO"); ticket.setCriadoEm(Instant.now());
        ticket.setResposta(pedido.getColetadoEm()==null
                ? "Solicitação recebida. Você continua responsável até a confirmação do atendimento; não colete se não puder seguir."
                : "Solicitação recebida. O pedido continua sob sua responsabilidade; preserve-o e aguarde instruções para transferência ou devolução.");
        return suporte.save(ticket);
    }
    @Transactional(readOnly=true)
    public List<SolicitacaoSuporte> listar(String pedidoId, Usuario usuario) {
        return suporte.findByPedidoIdAndUsuarioIdOrderByCriadoEmDesc(pedidoId, usuario.getId());
    }
    @Transactional
    public SolicitacaoSuporte responder(String id, String resposta) {
        var ticket=suporte.findById(id).orElseThrow(() -> new IdNaoEncontradoException("Protocolo não encontrado."));
        ticket.setResposta(resposta.trim()); ticket.setStatus("RESPONDIDO"); ticket.setRespondidoEm(Instant.now());
        return suporte.save(ticket);
    }
}
