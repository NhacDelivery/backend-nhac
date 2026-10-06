package br.com.nhac.backend_nhac.domain.avaliacao_produto;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.produto.*;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.*;
@Service
public class AvaliacaoProdutoService {
    private final AvaliacaoProdutoRepository avaliacoes;
    private final PedidoRepository pedidos;
    private final ProdutoRepository produtos;
    public AvaliacaoProdutoService(AvaliacaoProdutoRepository avaliacoes, PedidoRepository pedidos, ProdutoRepository produtos) {
        this.avaliacoes=avaliacoes; this.pedidos=pedidos; this.produtos=produtos;
    }
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(cacheNames=br.com.nhac.backend_nhac.config.cache.CacheNames.PRODUTO_AVALIACOES, allEntries=true)
    public AvaliacaoProdutoResponseDTO criar(String id, Usuario usuario, AvaliacaoProdutoDTO dto) {
        var pedido=pedidos.findLockedById(dto.pedidoId()).orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
        if (!pedido.getUsuarioId().equals(usuario.getId())) throw new AcessoNegadoException("Você só pode avaliar seus pedidos.");
        if (pedido.getStatus()!=StatusPedido.ENTREGUE) throw new RegraDeNegocioException("Avalie apenas pedidos entregues.");
        var item=pedido.getItens().stream().filter(i -> i.getProduto().getId().equals(id)).findFirst()
            .orElseThrow(() -> new RegraDeNegocioException("O produto não pertence ao pedido."));
        var anterior=avaliacoes.findByPedidoIdAndProdutoId(pedido.getId(),id);
        if (anterior.isPresent()) {
            var a=anterior.get();
            if (!a.getNota().equals(dto.nota()) || !Objects.equals(a.getComentario(),dto.comentario()) || !a.getImagens().equals(dto.imagens()==null ? List.of() : dto.imagens()))
                throw new IdempotenciaConflitoException("Este produto já foi avaliado neste pedido.");
            return new AvaliacaoProdutoResponseDTO(a);
        }
        var a=new AvaliacaoProduto(); a.setId(UUID.randomUUID().toString()); a.setPedido(pedido); a.setProduto(item.getProduto());
        a.setUsuario(usuario); a.setNota(dto.nota()); a.setComentario(dto.comentario());
        if (dto.imagens()!=null) a.getImagens().addAll(dto.imagens());
        avaliacoes.save(a); return new AvaliacaoProdutoResponseDTO(a);
    }
    @Transactional(readOnly=true)
    public java.util.List<AvaliacaoProdutoResponseDTO> minhas(String pedidoId, Usuario usuario) {
        var pedido=pedidos.findById(pedidoId).orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
        if (!pedido.getUsuarioId().equals(usuario.getId())) throw new AcessoNegadoException("Você só pode consultar avaliações dos seus pedidos.");
        return avaliacoes.findByPedidoId(pedidoId).stream().map(AvaliacaoProdutoResponseDTO::new).toList();
    }
    @Transactional(readOnly=true)
    public Page<AvaliacaoProdutoResponseDTO> listar(String id, boolean fotos, boolean positivas, int page, int size) {
        if (!produtos.existsById(id)) throw new IdNaoEncontradoException("Produto não encontrado.");
        return avaliacoes.listar(id,fotos,positivas,PageRequest.of(pagina(page,size).getPageNumber(),size,Sort.by(Sort.Direction.DESC,"criadoEm","id"))).map(AvaliacaoProdutoResponseDTO::new);
    }
    @Transactional(readOnly=true)
    public Page<ProdutoResumoDTO> catalogo(String lojaId, String filtro, int page, int size) {
        var pageable=pagina(page,size);
        return switch(filtro) {
            case "Vendidos" -> produtos.catalogoVendidos(lojaId,pageable).map(p -> new ProdutoResumoDTO(p,false));
            case "Em destaque" -> produtos.catalogoDestaques(lojaId,pageable).map(p -> new ProdutoResumoDTO(p,false));
            case "Todos" -> produtos.findByLojaIdAndIsAtivoTrue(lojaId,pageable).map(p -> new ProdutoResumoDTO(p,false));
            default -> throw new RegraDeNegocioException("Filtro inválido.");
        };
    }
    private Pageable pagina(int page,int size) {
        if(page<0 || size<1 || size>50) throw new RegraDeNegocioException("Paginação inválida.");
        return PageRequest.of(page,size);
    }
}
