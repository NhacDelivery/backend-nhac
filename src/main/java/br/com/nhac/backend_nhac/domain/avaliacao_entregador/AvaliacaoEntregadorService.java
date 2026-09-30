package br.com.nhac.backend_nhac.domain.avaliacao_entregador;

import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO;
import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorResponseDTO;
import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorResumoDTO;
import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacoesEntregadorPageDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.EntregadorPedidoDTO;
import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AvaliacaoEntregadorService {

    private final AvaliacaoEntregadorRepository repository;
    private final PedidoRepository pedidoRepository;

    public AvaliacaoEntregadorService(AvaliacaoEntregadorRepository repository, PedidoRepository pedidoRepository) {
        this.repository = repository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public AvaliacaoEntregadorResponseDTO criar(String pedidoId, AvaliacaoEntregadorCreateDTO dto, Usuario usuarioLogado) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado"));

        if (!pedido.getUsuarioId().equals(usuarioLogado.getId())) {
            throw new RegraDeNegocioException("Você só pode avaliar os seus próprios pedidos.");
        }

        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new RegraDeNegocioException("Só é possível avaliar após a entrega do pedido.");
        }

        if (pedido.getEntregador() == null) {
            throw new RegraDeNegocioException("Este pedido não possui entregador para avaliar.");
        }

        if (repository.existsByPedidoId(pedidoId)) {
            throw new RegraDeNegocioException("Este entregador já foi avaliado para este pedido.");
        }

        String comentario = dto.comentario() != null ? dto.comentario().trim() : null;

        AvaliacaoEntregador avaliacao = AvaliacaoEntregador.builder()
                .pedido(pedido)
                .entregador(pedido.getEntregador())
                .usuario(usuarioLogado)
                .nota(dto.nota())
                .comentario(comentario)
                .build();

        try {
            avaliacao = repository.save(avaliacao);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new RegraDeNegocioException("Este entregador já foi avaliado para este pedido.");
        }

        return new AvaliacaoEntregadorResponseDTO(avaliacao);
    }

    @Transactional(readOnly = true)
    public Optional<AvaliacaoEntregadorResponseDTO> buscarPorPedido(String pedidoId, String usuarioId) {
        return repository.findByPedidoIdAndUsuarioId(pedidoId, usuarioId)
                .map(AvaliacaoEntregadorResponseDTO::new);
    }

    @Transactional(readOnly = true)
    public AvaliacoesEntregadorPageDTO listarAvaliacoesDoEntregador(String entregadorId, Pageable pageable) {
        Page<AvaliacaoEntregadorResumoDTO> page = repository.findByEntregadorIdOrderByCriadoEmDesc(entregadorId, pageable)
                .map(AvaliacaoEntregadorResumoDTO::new);

        Double media = repository.calcularMediaPorEntregadorId(entregadorId);
        long total = repository.countByEntregadorId(entregadorId);

        return new AvaliacoesEntregadorPageDTO(
                new AvaliacoesEntregadorPageDTO.ResumoDTO(media != null ? media : 0.0, total),
                page
        );
    }

    @Transactional(readOnly = true)
    public EntregadorPedidoDTO obterResumoEntregador(Entregador entregador) {
        if (entregador == null) return null;
        Double media = repository.calcularMediaPorEntregadorId(entregador.getId());
        long total = repository.countByEntregadorId(entregador.getId());
        return EntregadorPedidoDTO.from(entregador, media, total);
    }

    @Transactional(readOnly = true)
    public boolean existeAvaliacaoParaPedido(String pedidoId) {
        return repository.existsByPedidoId(pedidoId);
    }
}
