package br.com.nhac.backend_nhac.domain.lojista;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoResumoLojistaMapper;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoLojistaDTO;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.produto.dto.ProdutoLojistaDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class LojistaService {

    static final int TAMANHO_MAXIMO_PAGINA = 100;

    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final PedidoResumoLojistaMapper pedidoResumoLojistaMapper;
    private final LojaAccessService lojaAccessService;

    public LojistaService(ProdutoRepository produtoRepository, PedidoRepository pedidoRepository,
                           PedidoResumoLojistaMapper pedidoResumoLojistaMapper, LojaAccessService lojaAccessService) {
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pedidoResumoLojistaMapper = pedidoResumoLojistaMapper;
        this.lojaAccessService = lojaAccessService;
    }

    @Transactional(readOnly = true)
    public Page<ProdutoLojistaDTO> listarProdutos(Usuario usuarioLogado, String categoriaMenu, String nome, Pageable pageable) {
        lojaAccessService.exigirCatalogo(usuarioLogado);
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        String categoriaFiltro = StringUtils.hasText(categoriaMenu) ? categoriaMenu : null;
        String nomeFiltro = StringUtils.hasText(nome) ? nome : null;
        return produtoRepository.findByLoja(loja.getId(), categoriaFiltro, nomeFiltro, limitarPagina(pageable))
                .map(ProdutoLojistaDTO::new);
    }

    /** Busca produto da loja do usuário logado, incluindo inativos para edição. */
    @Transactional(readOnly = true)
    public ProdutoLojistaDTO buscarProdutoPorId(Usuario usuarioLogado, String produtoId) {
        lojaAccessService.exigirCatalogo(usuarioLogado);
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        Produto produto = produtoRepository.findByIdAndLojaId(produtoId, loja.getId())
                .orElseThrow(() -> new br.com.nhac.backend_nhac.exceptions.ProdutoNaoEncontradoException(produtoId));
        return new ProdutoLojistaDTO(produto);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResumoLojistaDTO> listarPedidos(Usuario usuarioLogado, StatusPedido status, Pageable pageable) {
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        Page<Pedido> pagina = pedidoRepository.findByLoja(loja.getId(), status, limitarPagina(pageable));
        return new org.springframework.data.domain.PageImpl<>(
                pedidoResumoLojistaMapper.mapear(pagina.getContent()),
                pagina.getPageable(),
                pagina.getTotalElements()
        );
    }

    Pageable limitarPagina(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), TAMANHO_MAXIMO_PAGINA);
        if (size == pageable.getPageSize()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}
