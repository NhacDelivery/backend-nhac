package br.com.nhac.backend_nhac.domain.pedido;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

import br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository;
import br.com.nhac.backend_nhac.domain.entregador.StatusOperacional;
import br.com.nhac.backend_nhac.domain.loja.FreteService;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoDetalheLojistaDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResponseDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoResumoDTO;
import br.com.nhac.backend_nhac.domain.pedido.dto.ResultadoCriacaoPedido;
import br.com.nhac.backend_nhac.domain.pedido.dto.PagamentoPendenteDTO;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.CampoObrigatorioFaltandoException;
import br.com.nhac.backend_nhac.exceptions.EstoqueInsuficienteException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.IdempotenciaConflitoException;
import br.com.nhac.backend_nhac.exceptions.LojaFechadaException;
import br.com.nhac.backend_nhac.exceptions.PagamentoRecusadoException;
import br.com.nhac.backend_nhac.exceptions.ProdutoInativoException;
import br.com.nhac.backend_nhac.exceptions.ProdutoNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.QuantidadeInvalidaException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.exceptions.PedidoAtivoException;
import br.com.nhac.backend_nhac.exceptions.PagamentoIndisponivelException;

@Service
public class PedidoService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PedidoService.class);

    private static final List<StatusPedido> STATUS_ATIVOS = List.of(
            StatusPedido.PENDENTE, StatusPedido.PAGO, StatusPedido.PREPARANDO, StatusPedido.SAIU_ENTREGA);

    @Value("${nhac.payments.mock-mode:false}")
    private boolean mockMode;

    private final Environment environment;
    private final PedidoReservaService reservaService;

    private final br.com.nhac.backend_nhac.domain.cupom.CupomService cupomService;
    private final PedidoRepository pedidoRepository;
    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaAccessService lojaAccessService;
    private final StripePaymentService stripePaymentService;
    private final AsaasPaymentService asaasPaymentService;
    private final ApplicationEventPublisher eventPublisher;
    private final FreteService freteService;
    private final EntregadorRepository entregadorRepository;
    private final br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregadorService avaliacaoEntregadorService;

    public PedidoService(PedidoRepository pedidoRepository, LojaRepository lojaRepository, ProdutoRepository produtoRepository,
                          UsuarioRepository usuarioRepository, LojaAccessService lojaAccessService,
                          StripePaymentService stripePaymentService, AsaasPaymentService asaasPaymentService,
                          ApplicationEventPublisher eventPublisher, FreteService freteService,
                          EntregadorRepository entregadorRepository,
                          br.com.nhac.backend_nhac.domain.cupom.CupomService cupomService,
                          Environment environment,
                          br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregadorService avaliacaoEntregadorService,
                          PedidoReservaService reservaService) {
        this.reservaService = reservaService;
        this.cupomService = cupomService;
        this.pedidoRepository = pedidoRepository;
        this.lojaRepository = lojaRepository;
        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.lojaAccessService = lojaAccessService;
        this.stripePaymentService = stripePaymentService;
        this.asaasPaymentService = asaasPaymentService;
        this.eventPublisher = eventPublisher;
        this.freteService = freteService;
        this.entregadorRepository = entregadorRepository;
        this.environment = environment;
        this.avaliacaoEntregadorService = avaliacaoEntregadorService;
    }

    // A reserva já está commitada quando o provedor externo é consultado.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public ResultadoCriacaoPedido finalizarPedido(PedidoCreateDTO dto, Usuario usuarioLogado, String idempotencyKey) {
        var reserva = reservaService.reservar(dto, usuarioLogado, idempotencyKey);
        Pedido pedido = reserva.pedido();
        if (reserva.replay())
            return new ResultadoCriacaoPedido(new PedidoCriadoDTO(pedido.getId(), null, null, null), true);
        try {
            if ("PIX".equalsIgnoreCase(pedido.getFormaPagamento()))
                return new ResultadoCriacaoPedido(asaasPaymentService.criarCobrancaPix(
                        pedido, usuarioLogado.getNome(), usuarioLogado.getEmail(), dto.cpfPagador()), false);
            if (List.of("CARTAO", "GOOGLE_PAY", "STRIPE").contains(
                    pedido.getFormaPagamento().toUpperCase(java.util.Locale.ROOT)))
                return new ResultadoCriacaoPedido(stripePaymentService.criarPaymentIntentCartao(pedido), false);
            return new ResultadoCriacaoPedido(new PedidoCriadoDTO(pedido.getId(), null, null, null), false);
        } catch (Exception e) {
            throw new PagamentoIndisponivelException(
                    "O pedido foi reservado. Consulte o pagamento antes de tentar novamente.", pedido.getId());
        }
    }

    @Transactional(readOnly = true)
    public Optional<PedidoResponseDTO> buscarPedidoAtivo(String usuarioId) {
        return pedidoRepository.findFirstByUsuarioIdAndStatusInOrderByCriadoEmDesc(usuarioId, STATUS_ATIVOS)
                .map(this::montarResponseComEntregador);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> buscarPedidosAtivos(String usuarioId) {
        return pedidoRepository.findByUsuarioIdAndStatusInOrderByCriadoEmDesc(usuarioId, STATUS_ATIVOS)
                .stream().map(this::montarResponseComEntregador).toList();
    }

    private PedidoResponseDTO montarResponseComEntregador(Pedido pedido) {
        if (pedido.getEntregador() == null || (pedido.getStatus() != StatusPedido.PREPARANDO && pedido.getStatus() != StatusPedido.SAIU_ENTREGA && pedido.getStatus() != StatusPedido.ENTREGUE)) {
            return new PedidoResponseDTO(pedido);
        }
        var entregadorDto = avaliacaoEntregadorService.obterResumoEntregador(pedido.getEntregador());
        boolean avaliado = avaliacaoEntregadorService.existeAvaliacaoParaPedido(pedido.getId());
        return PedidoResponseDTO.comDetalhesEntregador(pedido, entregadorDto, avaliado);
    }

    public PagamentoPendenteDTO buscarPagamento(String pedidoId, String usuarioId) {
        Pedido pedido = PedidoReservaService.snapshotParaPagamento(pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado.")));
        if (!pedido.getUsuarioId().equals(usuarioId)) {
            throw new AcessoNegadoException("Acesso negado: este pedido pertence a outro usuário.");
        }
        validarPagamentoPendente(pedido);

        String forma = pedido.getFormaPagamento().toUpperCase(java.util.Locale.ROOT);
        String pix = null;
        String clientSecret = null;
        if ("PIX".equals(forma)) {
            try {
                if (pedido.getAsaasPaymentId() == null && !asaasPaymentService.recuperarCobranca(pedido))
                    throw new PagamentoIndisponivelException("O pagamento ainda está sendo confirmado. Tente novamente em instantes.", pedidoId);
                pix = asaasPaymentService.obterCodigoPix(pedido);
            } catch (PagamentoIndisponivelException e) {
                throw e;
            } catch (RuntimeException e) {
                // Não registra CPF, token ou corpo da resposta do provedor.
                Integer status = e instanceof org.springframework.web.client.RestClientResponseException http
                        ? http.getStatusCode().value() : null;
                log.warn("PIX indisponível pedido={} etapa={} asaasStatus={} causa={}",
                        pedidoId, pedido.getAsaasPaymentId() == null ? "recuperar-cobranca" : "codigo-pix",
                        status, e.getClass().getSimpleName());
                throw new PagamentoIndisponivelException(
                        "Não foi possível carregar o PIX agora. Tente novamente em instantes.", pedidoId);
            }
        } else if (List.of("CARTAO", "STRIPE", "GOOGLE_PAY").contains(forma)) {
            clientSecret = pedido.getStripePaymentIntentId() == null
                    ? stripePaymentService.criarPaymentIntentCartao(pedido).clientSecret()
                    : stripePaymentService.obterClientSecret(pedido);
        } else {
            throw new PagamentoIndisponivelException("Este pedido não usa pagamento eletrônico.");
        }
        return new PagamentoPendenteDTO(pedidoId, forma, pedido.getStatus(), pedido.getPagamentoExpiraEm(),
                pedido.getValorTotal(),
                pix, pix, clientSecret, simulacaoDisponivel());
    }

    @Transactional
    public void simularPagamento(String pedidoId, String usuarioId) {
        if (!simulacaoDisponivel()) {
            throw new AcessoNegadoException("Simulação de pagamento indisponível.");
        }
        Pedido pedido = pedidoRepository.findLockedById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
        if (!pedido.getUsuarioId().equals(usuarioId)) {
            throw new AcessoNegadoException("Acesso negado: este pedido pertence a outro usuário.");
        }
        if (!"PIX".equalsIgnoreCase(pedido.getFormaPagamento())) {
            throw new PagamentoIndisponivelException("A simulação está disponível apenas para PIX.");
        }
        validarPagamentoPendente(pedido);
        if (!mockMode) {
            // No sandbox a cobrança é encerrada antes de registrar a simulação,
            // evitando que um PIX de teste possa ser pago depois do pedido avançar.
            String status = asaasPaymentService.consultarStatus(pedido.getAsaasPaymentId());
            if ("DELETED".equals(status)) {
                throw new PagamentoIndisponivelException("A cobrança PIX já foi cancelada.");
            }
            if (!"RECEIVED".equals(status) && !"CONFIRMED".equals(status)) {
                asaasPaymentService.cancelarCobranca(pedido.getAsaasPaymentId());
            }
        }
        pedido.alterarStatus(StatusPedido.PAGO);
        pedidoRepository.save(pedido);
        publicarStatus(pedido);
    }

    private boolean simulacaoDisponivel() {
        List<String> perfis = java.util.Arrays.asList(environment.getActiveProfiles());
        if (mockMode) return perfis.contains("e2e");
        if (!perfis.contains("dev")) return false;
        String url = environment.getProperty("asaas.api.url", "");
        try {
            String host = java.net.URI.create(url).getHost();
            return "sandbox.asaas.com".equals(host) || "api-sandbox.asaas.com".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void validarPagamentoPendente(Pedido pedido) {
        if (pedido.getStatus() != StatusPedido.PENDENTE || pedido.getPagamentoExpiraEm() == null
                || !Instant.now().isBefore(pedido.getPagamentoExpiraEm())) {
            throw new PagamentoIndisponivelException("O pagamento deste pedido não está mais disponível.");
        }
    }

    @Transactional
    public void marcarComoPagoPorPaymentIntentId(String paymentIntentId) {
        Pedido pedido = pedidoRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido com PaymentIntent " + paymentIntentId + " não encontrado."));
        
        if (pedido.getStatus() == StatusPedido.PAGO
                || pedido.getStatus() == StatusPedido.PREPARANDO
                || pedido.getStatus() == StatusPedido.SAIU_ENTREGA
                || pedido.getStatus() == StatusPedido.ENTREGUE) {
            return; // webhook duplicado ou atrasado: estado já reflete pagamento confirmado
        }

        pedido.alterarStatus(StatusPedido.PAGO);
        pedidoRepository.save(pedido);
        publicarStatus(pedido);
    }

    @Transactional
    public void marcarComoPagoPorAsaasPaymentId(String asaasPaymentId) {
        Pedido pedido = pedidoRepository.findByAsaasPaymentId(asaasPaymentId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido com Asaas Payment ID " + asaasPaymentId + " não encontrado."));
        
        if (pedido.getStatus() == StatusPedido.PAGO
                || pedido.getStatus() == StatusPedido.PREPARANDO
                || pedido.getStatus() == StatusPedido.SAIU_ENTREGA
                || pedido.getStatus() == StatusPedido.ENTREGUE) {
            return;
        }

        pedido.alterarStatus(StatusPedido.PAGO);
        pedidoRepository.save(pedido);
        publicarStatus(pedido);
    }

    @Transactional
    public void cancelarPorFalhaPagamentoAsaas(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado para cancelamento por falha de pagamento Asaas."));

        cancelarPorFalhaDePagamentoSePendente(pedido);
    }

    @Transactional(readOnly = true)
    public PedidoResponseDTO buscarPedido(String id, String usuarioIdLogado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));

        if (!pedido.getUsuarioId().equals(usuarioIdLogado)) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para visualizar este pedido.");
        }

        return montarResponseComEntregador(pedido);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResumoDTO> listarMeusPedidos(String usuarioId, Pageable pageable) {
        Page<Pedido> page = pedidoRepository.findByUsuarioId(usuarioId, pageable);
        return page.map(PedidoResumoDTO::new);
    }

    /**
     * Detalhe de pedido do ponto de vista do lojista (dono ou funcionário da loja).
     * Diferente de buscarPedido(): autoriza por posse da LOJA (não por ser o
     * cliente que comprou) e enriquece a resposta com nome/telefone do cliente,
     * que o lojista precisa pra atender/entregar o pedido.
     */
    @Transactional(readOnly = true)
    public PedidoDetalheLojistaDTO buscarPedidoParaLojista(String id, Usuario usuarioLogado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));

        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");
        if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, pedido.getLoja().getId())) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para visualizar este pedido.");
        }

        Usuario cliente = usuarioRepository.findById(pedido.getUsuarioId()).orElse(null);
        String clienteNome = cliente != null ? cliente.getNome() : "Cliente";
        String clienteTelefone = cliente != null ? cliente.getTelefone() : null;

        return new PedidoDetalheLojistaDTO(pedido, clienteNome, clienteTelefone);
    }

    @Transactional
    public void atualizarStatus(String pedidoId, StatusPedido novoStatus, Usuario usuarioLogado) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));

        // ADMIN tem bypass na checagem de ownership; dono ou funcionário da loja também passam
        boolean isAdmin = usuarioLogado.getPapel().name().equals("ADMIN");
        if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, pedido.getLoja().getId())) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para alterar o status deste pedido.");
        }
        
        if (novoStatus == StatusPedido.ENTREGUE) {
            throw new RegraDeNegocioException(
                    "A entrega só pode ser concluída pelo entregador com o código de confirmação.");
        }

        if (novoStatus == StatusPedido.CANCELADO) {
            if (pedido.getStatus() != StatusPedido.PENDENTE) {
                throw new RegraDeNegocioException(
                        "Pedido pago ou em preparo não pode ser cancelado sem um fluxo de estorno.");
            }
            cancelarCobrancaSePendente(pedido);
            cancelarInternamente(pedido);
            return;
        }

        pedido.alterarStatus(novoStatus);
        pedidoRepository.save(pedido);
        publicarStatus(pedido);

        // Despacho automático: quando a loja aceita o pedido e começa a
        // preparar, os motoboys próximos já recebem a oferta. Antes disso,
        // despacharPedido() existia no backend mas nenhum caller chamava —
        // nenhuma oferta era gerada em produção, e o app do motoboy ficava
        // eternamente "procurando chamadas".
        //
        // Publicado como evento (consumido em AFTER_COMMIT pelo
        // DespachoEventListener) em vez de chamada direta ao DespachoService,
        // por dois motivos:
        //   1. se o despacho falhasse dentro desta mesma transação (loja sem
        //      coordenadas, nenhum entregador online), o interceptor do Spring
        //      marcaria a transação como rollback-only e a mudança de status
        //      seria perdida no commit — mesmo com try/catch aqui;
        //   2. a oferta referencia um pedido que precisa já estar commitado.
        if (novoStatus == StatusPedido.PREPARANDO && pedido.getEntregador() == null) {
            eventPublisher.publishEvent(new PedidoPreparandoEvent(pedido.getId()));
        }
    }

    @Transactional
    public void cancelarPedido(String pedidoId, String usuarioIdLogado) {
        Pedido pedido = pedidoRepository.findLockedById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));

        if (!pedido.getUsuarioId().equals(usuarioIdLogado)) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para cancelar este pedido.");
        }

        // Sem fluxo de refund explícito, o cliente só cancela antes do
        // pagamento ser confirmado. Cancelamentos pós-pagamento precisam
        // passar por uma futura operação de estorno.
        if (pedido.getStatus() != StatusPedido.PENDENTE) {
            throw new RegraDeNegocioException(
                    "O cliente só pode cancelar pedidos enquanto o pagamento estiver pendente.");
        }

        // A cobrança precisa deixar de ser pagável antes de liberarmos estoque
        // e permitirmos um novo pedido. Se o provedor falhar, mantenha ativo.
        cancelarCobrancaSePendente(pedido);

        cancelarInternamente(pedido);
    }

    private void cancelarCobrancaSePendente(Pedido pedido) {
        if (pedido.isPagamentoCriacaoIncerta())
            throw new PagamentoIndisponivelException("A cobrança está em conciliação. Aguarde a confirmação antes de cancelar.");
        if (pedido.getAsaasPaymentId() != null) {
            String status = asaasPaymentService.consultarStatus(pedido.getAsaasPaymentId());
            if ("RECEIVED".equals(status) || "CONFIRMED".equals(status)) {
                throw new PagamentoIndisponivelException("O pagamento já foi confirmado. Atualize o pedido.");
            }
            if (!"DELETED".equals(status)) asaasPaymentService.cancelarCobranca(pedido.getAsaasPaymentId());
        } else if (pedido.getStripePaymentIntentId() != null) {
            String status = stripePaymentService.consultarStatus(pedido.getStripePaymentIntentId());
            if ("succeeded".equals(status) || "processing".equals(status)) {
                throw new PagamentoIndisponivelException("O pagamento está em processamento. Atualize o pedido.");
            }
            if (!"canceled".equals(status)) stripePaymentService.cancelarPaymentIntent(pedido.getStripePaymentIntentId());
        }
    }

    @Transactional
    public void marcarComoCanceladoPorFalhaDePagamento(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado para cancelamento por webhook."));

        cancelarPorFalhaDePagamentoSePendente(pedido);
    }

    static String calcularFingerprint(String usuarioId, PedidoCreateDTO dto) {
        String endereco = dto.enderecoEntrega() == null ? "" : String.join("|",
                n(dto.enderecoEntrega().rua()),
                n(dto.enderecoEntrega().numero()),
                n(dto.enderecoEntrega().bairro()),
                n(dto.enderecoEntrega().cidade()),
                n(dto.enderecoEntrega().estado()),
                n(dto.enderecoEntrega().cep()),
                n(dto.enderecoEntrega().complemento()),
                dto.enderecoEntrega().latitude() == null ? "" : dto.enderecoEntrega().latitude().toString(),
                dto.enderecoEntrega().longitude() == null ? "" : dto.enderecoEntrega().longitude().toString());

        String itens = dto.itens().stream()
                .sorted(Comparator.comparing(PedidoCreateDTO.ItemPedidoDTO::produtoId)
                        .thenComparing(PedidoCreateDTO.ItemPedidoDTO::quantidade))
                .map(i -> n(i.produtoId()) + ":" + i.quantidade() + (i.adicionais()==null || i.adicionais().isEmpty() ? "" : ":" + i.adicionais().stream().sorted().collect(java.util.stream.Collectors.joining(";"))))
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        String canonico = String.join("||",
                n(usuarioId), n(dto.lojaId()), n(dto.formaPagamento()),
                n(dto.observacao()), n(dto.cupomId()), endereco, itens);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    private static String n(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private void cancelarPorFalhaDePagamentoSePendente(Pedido pedido) {
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return; // webhook repetido
        }
        if (pedido.getStatus() != StatusPedido.PENDENTE) {
            throw new RegraDeNegocioException(
                    "Falha de pagamento recebida para pedido que já avançou para " + pedido.getStatus());
        }
        cancelarInternamente(pedido);
    }

    /**
     * Único ponto que efetiva CANCELADO + devolução de estoque.
     * A checagem inicial torna replays sequenciais idempotentes; @Version em
     * Pedido garante rollback de uma segunda transação concorrente.
     */
    private boolean cancelarInternamente(Pedido pedido) {
        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            return false;
        }

        pedido.alterarStatus(StatusPedido.CANCELADO);
        devolverEstoque(pedido);
        if (pedido.getCupomId() != null && pedido.getDesconto().signum() > 0) {
            cupomService.devolver(pedido.getUsuarioId(), pedido.getCupomId());
        }

        if (pedido.getEntregador() != null) {
            pedido.getEntregador().setStatusOperacional(StatusOperacional.ONLINE);
            entregadorRepository.save(pedido.getEntregador());
        }

        pedidoRepository.save(pedido);
        publicarStatus(pedido);
        return true;
    }

    @Transactional
    public void cancelarPorExpiracao(String pedidoId) {
        Pedido pedido = pedidoRepository.findLockedById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado."));
        if (pedido.getStatus() == StatusPedido.PENDENTE
                && pedido.getPagamentoExpiraEm() != null
                && !Instant.now().isBefore(pedido.getPagamentoExpiraEm())) {
            cancelarInternamente(pedido);
        }
    }

    private void publicarStatus(Pedido pedido) {
        eventPublisher.publishEvent(new PedidoStatusAtualizadoEvent(pedido.getId(), pedido.getStatus()));
    }

    private void devolverEstoque(Pedido pedido) {
        for (ItemPedido item : pedido.getItens()) {
            Produto produto = item.getProduto();
            if (produto.getEstoque() != null) {
                produto.setEstoque(produto.getEstoque() + item.getQuantidade());
                produtoRepository.save(produto);
            }
        }
    }
}
