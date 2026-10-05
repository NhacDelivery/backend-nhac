package br.com.nhac.backend_nhac.domain.avaliacao;

import org.springframework.cache.annotation.CacheEvict;
import static br.com.nhac.backend_nhac.config.cache.CacheNames.*;

import br.com.nhac.backend_nhac.domain.avaliacao.Avaliacao;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoCreateDTO;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoResumoDTO;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.domain.avaliacao.AvaliacaoRepository;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final PedidoRepository pedidoRepository;
    private final LojaRepository lojaRepository;
    private final UsuarioRepository usuarioRepository;

    public AvaliacaoService(AvaliacaoRepository avaliacaoRepository, PedidoRepository pedidoRepository, LojaRepository lojaRepository, UsuarioRepository usuarioRepository) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.pedidoRepository = pedidoRepository;
        this.lojaRepository = lojaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    @CacheEvict(cacheNames = {LOJAS, LOJA, PRODUTO_AVALIACOES}, allEntries = true)
    public AvaliacaoResumoDTO criarAvaliacao(String usuarioId, AvaliacaoCreateDTO dto) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IdNaoEncontradoException("Usuário não encontrado."));

        Pedido pedido = pedidoRepository.findById(dto.pedidoId())
                .orElseThrow(() -> new IdNaoEncontradoException("O pedido com o id: " + dto.pedidoId() + " não foi encontrado."));

        if (!pedido.getUsuarioId().equals(usuarioId)) {
            throw new RegraDeNegocioException("Você só pode avaliar pedidos que pertencem a você.");
        }

        if (pedido.getStatus() != StatusPedido.ENTREGUE) {
            throw new RegraDeNegocioException("Apenas pedidos entregues podem ser avaliados.");
        }

        if (avaliacaoRepository.existsByPedidoId(pedido.getId())) {
            throw new RegraDeNegocioException("Este pedido já foi avaliado.");
        }

        Loja loja = lojaRepository.findLockedById(pedido.getLoja().getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Loja do pedido não encontrada."));

        Avaliacao avaliacao = new Avaliacao(dto.nota(), dto.comentario(), usuario, loja, pedido);
        avaliacaoRepository.saveAndFlush(avaliacao);

        recalcularMediaLoja(loja);

        return new AvaliacaoResumoDTO(avaliacao);
    }

    @Transactional(readOnly = true)
    public Page<AvaliacaoResumoDTO> listarAvaliacoesPorLoja(String lojaId, Pageable pageable) {
        if (!lojaRepository.existsById(lojaId)) {
            throw new IdNaoEncontradoException("A loja com o id: " + lojaId + " não foi encontrada.");
        }
        return avaliacaoRepository.findByLojaId(lojaId, pageable).map(AvaliacaoResumoDTO::new);
    }

    @Transactional(readOnly=true)
    public java.util.Map<String,Object> resumoLoja(String lojaId) {
        if (!lojaRepository.existsById(lojaId)) throw new IdNaoEncontradoException("Loja não encontrada.");
        long total=avaliacaoRepository.countByLojaId(lojaId), positivas=avaliacaoRepository.countByLojaIdAndNotaGreaterThanEqual(lojaId,4);
        return java.util.Map.of("total",total,"positivas",positivas,"percentualPositivo",total==0 ? 0.0 : 100.0*positivas/total);
    }

    private void recalcularMediaLoja(Loja loja) {
        long total = avaliacaoRepository.countByLojaId(loja.getId());
        Double media = avaliacaoRepository.calcularMediaPorLojaId(loja.getId());

        var dados = loja.getDadosOperacionais();
        dados.setTotalAvaliacoes(Math.toIntExact(total));
        float mediaArredondada = media == null
                ? 0.0f
                : Math.round(media.floatValue() * 10.0f) / 10.0f;
        dados.setAvaliacaoMedia(mediaArredondada);

        lojaRepository.save(loja);
    }}
