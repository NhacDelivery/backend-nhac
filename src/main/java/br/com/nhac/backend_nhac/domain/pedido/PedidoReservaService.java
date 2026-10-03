package br.com.nhac.backend_nhac.domain.pedido;
import java.math.BigDecimal;
import java.util.Map;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.produto.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.domain.cupom.CupomService;
import br.com.nhac.backend_nhac.domain.pedido.dto.*;
import br.com.nhac.backend_nhac.exceptions.*;
@Service
public class PedidoReservaService {
    private final PedidoRepository pedidoRepository;
    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CupomService cupomService;
    public PedidoReservaService(PedidoRepository pedidos, LojaRepository lojas,
            ProdutoRepository produtos, UsuarioRepository usuarios, CupomService cupons) {
        this.pedidoRepository = pedidos; this.lojaRepository = lojas;
        this.produtoRepository = produtos; this.usuarioRepository = usuarios; this.cupomService = cupons;
    }
    public record ReservaPedido(Pedido pedido, boolean replay) {}
    @Transactional
    public ReservaPedido reservar(PedidoCreateDTO dto, Usuario usuarioLogado, String idempotencyKey) {
        if ("PIX".equalsIgnoreCase(dto.formaPagamento()) &&
                (dto.cpfPagador() == null || dto.cpfPagador().isBlank()))
            throw new RegraDeNegocioException("O CPF do pagador é obrigatório para pagamento via PIX.");
        // A mesma trava protege chaves diferentes e chamadas sem chave.
        usuarioRepository.findLockedById(usuarioLogado.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Usuário autenticado não encontrado."));

        String idempotencyFingerprint = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyFingerprint = PedidoService.calcularFingerprint(usuarioLogado.getId(), dto);

            var existente = pedidoRepository.findByUsuarioIdAndIdempotencyKey(
                    usuarioLogado.getId(), idempotencyKey);
            if (existente.isPresent()) {
                Pedido pedidoExistente = existente.get();
                if (pedidoExistente.getIdempotencyFingerprint() != null
                        && !pedidoExistente.getIdempotencyFingerprint().equals(idempotencyFingerprint)) {
                    throw new IdempotenciaConflitoException(
                            "A Idempotency-Key já foi usada com um pedido diferente.");
                }
                return new ReservaPedido(pedidoExistente, true);
            }
        }


        Loja loja = lojaRepository.findByIdAndIsAbertoTrue(dto.lojaId())
                .orElseThrow(() -> new LojaFechadaException(dto.lojaId()));

        if (!loja.isAberto()) {
            throw new LojaFechadaException(dto.lojaId());
        }

        Pedido pedido = dto.toEntity(loja);
        pedido.setIdempotencyKey(idempotencyKey);
        pedido.setIdempotencyFingerprint(idempotencyFingerprint);
        pedido.setUsuarioId(usuarioLogado.getId());
        if ("PIX".equalsIgnoreCase(pedido.getFormaPagamento())
                || "CARTAO".equalsIgnoreCase(pedido.getFormaPagamento())
                || "STRIPE".equalsIgnoreCase(pedido.getFormaPagamento())
                || "GOOGLE_PAY".equalsIgnoreCase(pedido.getFormaPagamento())) {
            pedido.setPagamentoExpiraEm(pedido.getCriadoEm().plus(7, ChronoUnit.MINUTES));
            pedido.setPagamentoCriacaoIncerta(true);
        }

        BigDecimal valorTotalItens = BigDecimal.ZERO;

        for (PedidoCreateDTO.ItemPedidoDTO itemDto : dto.itens()) {
            if (itemDto.quantidade() <= 0) {
                throw new QuantidadeInvalidaException("A quantidade deve ser maior que zero", Map.of("produtoId", itemDto.produtoId(), "quantidade", itemDto.quantidade()));
            }

            Produto produtoReal = produtoRepository.findById(itemDto.produtoId())
                    .orElseThrow(() -> new ProdutoNaoEncontradoException(itemDto.produtoId(), loja.getId()));

            if (!produtoReal.getLoja().getId().equals(loja.getId())) {
                throw new RegraDeNegocioException("O produto '" + produtoReal.getNome() + "' não pertence à loja selecionada.");
            }

            if (!produtoReal.isAtivo()) {
                throw new ProdutoInativoException("O produto '" + produtoReal.getNome() + "' está inativo.", Map.of("produtoId", produtoReal.getId()));
            }

            if (produtoReal.getEstoque() == null || produtoReal.getEstoque() < itemDto.quantidade()) {
                throw new EstoqueInsuficienteException(produtoReal.getId(), itemDto.quantidade(), produtoReal.getEstoque() == null ? 0 : produtoReal.getEstoque());
            }

            int atualizados = produtoRepository.decrementarEstoqueSeDisponivel(produtoReal.getId(), itemDto.quantidade());
            if (atualizados == 0) {
                throw new EstoqueInsuficienteException(produtoReal.getId(), itemDto.quantidade(), produtoReal.getEstoque());
            }

            ItemPedido novoItem = itemDto.toEntity(produtoReal);
            // Snapshot histórico sempre vem da fonte canônica do servidor.
            // Nome/imagem enviados pelo app são mantidos no DTO por
            // compatibilidade, mas não são confiados.
            novoItem.setNome(produtoReal.getNome());
            novoItem.setImagemUrl(produtoReal.getImagemUrl());
            BigDecimal precoReal = produtoReal.getPreco();
            novoItem.setPrecoHistorico(precoReal);

            BigDecimal subtotal = precoReal.multiply(BigDecimal.valueOf(novoItem.getQuantidade()));
            valorTotalItens = valorTotalItens.add(subtotal);

            pedido.adicionarItem(novoItem);
        }

        if (pedido.getEnderecoEntrega() == null) {
            throw new CampoObrigatorioFaltandoException("enderecoEntrega");
        }

        BigDecimal taxaFrete = loja.getDadosOperacionais() != null
                && loja.getDadosOperacionais().getTaxaEntregaBase() != null
                ? loja.getDadosOperacionais().getTaxaEntregaBase()
                : new BigDecimal("5.00");
        pedido.setTaxaFrete(taxaFrete);
        BigDecimal desconto = BigDecimal.ZERO;
        if (dto.cupomId() != null && !dto.cupomId().isBlank()) {
            desconto = cupomService.consumir(usuarioLogado.getId(), dto.cupomId(), valorTotalItens);
        } else {
            pedido.setCupomId(null);
        }
        pedido.setDesconto(desconto);
        pedido.setValorTotal(valorTotalItens.subtract(desconto).add(taxaFrete));

        Pedido pedidoSalvo = pedidoRepository.save(pedido);


        return new ReservaPedido(snapshotParaPagamento(pedidoSalvo), false);
    }
    // OSIV pode manter a entidade gerenciada após o commit. O provedor recebe
    // apenas esta cópia, para que seus setters não provoquem flush de estado
    // antigo em uma transação curta de vínculo da cobrança.
    static Pedido snapshotParaPagamento(Pedido original) {
        Pedido snapshot = new Pedido();
        snapshot.setId(original.getId());
        snapshot.setUsuarioId(original.getUsuarioId());
        snapshot.setValorTotal(original.getValorTotal());
        snapshot.setFormaPagamento(original.getFormaPagamento());
        snapshot.setCriadoEm(original.getCriadoEm());
        snapshot.setPagamentoExpiraEm(original.getPagamentoExpiraEm());
        snapshot.setStatus(original.getStatus());
        snapshot.setPagamentoCriacaoIncerta(original.isPagamentoCriacaoIncerta());
        snapshot.setAsaasPaymentId(original.getAsaasPaymentId());
        snapshot.setStripePaymentIntentId(original.getStripePaymentIntentId());
        return snapshot;
    }

}
