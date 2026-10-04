package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entrega.dto.EntregaAtivaResponseDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.OfertaEntregaDTO;
import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorService;
import br.com.nhac.backend_nhac.domain.entregador.StatusOperacional;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaInvalidoException;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaBloqueadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DespachoService {

    private static final Logger log = LoggerFactory.getLogger(DespachoService.class);

    private final PedidoRepository pedidoRepository;
    private final OfertaEntregaRepository ofertaEntregaRepository;
    private final EntregadorService entregadorService;
    private final EntregadorRepository entregadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final CodigoEntregaService codigoEntregaService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public DespachoService(
            PedidoRepository pedidoRepository,
            OfertaEntregaRepository ofertaEntregaRepository,
            EntregadorService entregadorService,
            EntregadorRepository entregadorRepository,
            UsuarioRepository usuarioRepository,
            SimpMessagingTemplate messagingTemplate,
            CodigoEntregaService codigoEntregaService,
            org.springframework.context.ApplicationEventPublisher eventPublisher
    ) {
        this.pedidoRepository = pedidoRepository;
        this.ofertaEntregaRepository = ofertaEntregaRepository;
        this.entregadorService = entregadorService;
        this.entregadorRepository = entregadorRepository;
        this.usuarioRepository = usuarioRepository;
        this.messagingTemplate = messagingTemplate;
        this.codigoEntregaService = codigoEntregaService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public List<OfertaEntregaDTO> despacharPedido(String pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido " + pedidoId + " não encontrado para despacho."));

        if (pedido.getEntregador() != null) {
            throw new RegraDeNegocioException("Este pedido já possui um entregador vinculado.");
        }

        if (pedido.getStatus() != StatusPedido.PREPARANDO) {
            throw new RegraDeNegocioException(
                    "Só é possível despachar pedidos em PREPARANDO. Status atual: " + pedido.getStatus());
        }

        if (pedido.getLoja() == null || pedido.getLoja().getGeoLocalizacao() == null
                || (pedido.getLoja().getGeoLocalizacao().getGeoLat() == 0
                    && pedido.getLoja().getGeoLocalizacao().getGeoLng() == 0)) {
            throw new RegraDeNegocioException("Configure a localização da loja em Endereço da loja antes de buscar entregadores.");
        }

        double lojaLat = pedido.getLoja().getGeoLocalizacao().getGeoLat();
        double lojaLng = pedido.getLoja().getGeoLocalizacao().getGeoLng();

        // Busca entregadores online em um raio de até 7 km
        var entregadoresProximos = entregadorService.buscarEntregadoresProximos(lojaLat, lojaLng, 7.0);

        if (entregadoresProximos.isEmpty()) {
            log.warn("Nenhum entregador online disponível próximo à loja {} para o pedido {}.", pedido.getLoja().getNome(), pedidoId);
            return List.of();
        }

        List<OfertaEntregaDTO> ofertasCriadas = new ArrayList<>();
        Instant agora = Instant.now();
        Instant expiraEm = agora.plusSeconds(90); // tempo para polling, leitura e resposta

        for (var item : entregadoresProximos) {
            Entregador entregador = item.entregador();

            // Defesa adicional: status ONLINE sozinho não basta. Se por alguma
            // inconsistência o entregador já estiver vinculado a corrida ativa,
            // ele não recebe uma segunda oferta.
            if (pedidoRepository.existsByEntregadorIdAndStatusIn(
                    entregador.getId(),
                    List.of(StatusPedido.PREPARANDO, StatusPedido.SAIU_ENTREGA))) {
                continue;
            }

            var ofertaPendente = ofertaEntregaRepository
                    .findByPedidoIdAndEntregadorIdAndStatus(
                            pedido.getId(), entregador.getId(), StatusOferta.PENDENTE);
            if (ofertaPendente.isPresent()) {
                OfertaEntrega existente = ofertaPendente.get();
                if (!existente.isExpirada()) {
                    continue;
                }
                existente.setStatus(StatusOferta.EXPIRADA);
                ofertaEntregaRepository.save(existente);
            }

            OfertaEntrega oferta = OfertaEntrega.builder()
                    .id(UUID.randomUUID().toString())
                    .pedido(pedido)
                    .entregador(entregador)
                    .status(StatusOferta.PENDENTE)
                    .criadoEm(agora)
                    .expiraEm(expiraEm)
                    .build();

            ofertaEntregaRepository.save(oferta);
            OfertaEntregaDTO dto = new OfertaEntregaDTO(oferta);
            ofertasCriadas.add(dto);

            // Dispara notificação WebSocket em tempo real para o canal específico do motoboy
            try {
                messagingTemplate.convertAndSend("/topic/entregador/" + entregador.getId() + "/ofertas", dto);
            } catch (Exception e) {
                log.error("Erro ao enviar WebSocket de oferta para o entregador {}: {}", entregador.getId(), e.getMessage());
            }
        }

        return ofertasCriadas;
    }

    @Transactional
    public EntregaAtivaResponseDTO aceitarOferta(String ofertaId, Usuario usuarioLogado) {
        // Serializa aceites de pedidos diferentes para o mesmo entregador.
        Entregador entregador = entregadorService.buscarPorUsuarioComBloqueio(usuarioLogado);
        if (!entregador.isAtivo() || entregador.getStatusOperacional() != StatusOperacional.ONLINE
                || pedidoRepository.existsByEntregadorIdAndStatusIn(entregador.getId(),
                    List.of(StatusPedido.PREPARANDO, StatusPedido.SAIU_ENTREGA))) {
            throw new RegraDeNegocioException("Você não está disponível para aceitar outra corrida.");
        }

        OfertaEntrega oferta = ofertaEntregaRepository.findByIdAndEntregadorId(ofertaId, entregador.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Oferta não encontrada para este entregador."));

        if (oferta.getStatus() != StatusOferta.PENDENTE) {
            throw new RegraDeNegocioException("Esta oferta já foi respondida ou processada anteriormente.");
        }

        if (oferta.isExpirada()) {
            oferta.setStatus(StatusOferta.EXPIRADA);
            ofertaEntregaRepository.save(oferta);
            throw new RegraDeNegocioException("Esta oferta expirou.");
        }

        String pedidoId = oferta.getPedido().getId();

        // A disputa é resolvida pelo banco em uma única instrução condicional.
        // Apenas uma transação consegue trocar entregador_id de NULL para um
        // entregador; as demais recebem 0 linhas atualizadas.
        int atualizados = pedidoRepository.atribuirEntregadorSeDisponivel(pedidoId, entregador);
        if (atualizados == 0) {
            OfertaEntrega ofertaPerdedora = ofertaEntregaRepository
                    .findByIdAndEntregadorId(ofertaId, entregador.getId())
                    .orElseThrow(() -> new IdNaoEncontradoException("Oferta não encontrada para este entregador."));
            ofertaPerdedora.setStatus(StatusOferta.EXPIRADA);
            ofertaEntregaRepository.save(ofertaPerdedora);
            throw new RegraDeNegocioException("Outro entregador já aceitou esta corrida antes de você.");
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado após aceite da oferta."));
        OfertaEntrega ofertaAceita = ofertaEntregaRepository
                .findByIdAndEntregadorId(ofertaId, entregador.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Oferta não encontrada após aceite."));

        // O status do pedido NÃO muda aqui: aceitar é só atribuição da corrida.
        ofertaAceita.setStatus(StatusOferta.ACEITA);
        ofertaEntregaRepository.save(ofertaAceita);

        // Atualiza status operacional do entregador para EM_ENTREGA
        entregador.setStatusOperacional(StatusOperacional.EM_ENTREGA);
        entregadorRepository.save(entregador);

        // Expira as demais ofertas pendentes concorrentes deste pedido
        List<OfertaEntrega> concorrentes = ofertaEntregaRepository.findByPedidoIdAndStatus(pedido.getId(), StatusOferta.PENDENTE);
        for (OfertaEntrega conc : concorrentes) {
            if (!conc.getId().equals(ofertaAceita.getId())) {
                conc.setStatus(StatusOferta.EXPIRADA);
                ofertaEntregaRepository.save(conc);
            }
        }

        // Notifica via WebSocket que o pedido foi assumido
        notificarStatus(pedido);

        return montarEntregaAtiva(pedido);
    }

    @Transactional
    public void recusarOferta(String ofertaId, Usuario usuarioLogado) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);

        OfertaEntrega oferta = ofertaEntregaRepository.findByIdAndEntregadorId(ofertaId, entregador.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Oferta não encontrada."));

        if (oferta.getStatus() == StatusOferta.PENDENTE) {
            oferta.setStatus(StatusOferta.RECUSADA);
            ofertaEntregaRepository.save(oferta);
        }
    }

    @Transactional(readOnly = true)
    public List<OfertaEntregaDTO> listarOfertasPendentes(Usuario usuarioLogado) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);
        List<OfertaEntrega> pendentes = ofertaEntregaRepository.findByEntregadorIdAndStatus(entregador.getId(), StatusOferta.PENDENTE);

        return pendentes.stream()
                .filter(o -> !o.isExpirada())
                .map(OfertaEntregaDTO::new)
                .toList();
    }

    /**
     * Confirma que o entregador retirou o pedido na loja. É aqui que o pedido
     * vira SAIU_ENTREGA (antes isso acontecia no aceite da oferta).
     */
    @Transactional
    public EntregaAtivaResponseDTO coletarPedido(String pedidoId, Usuario usuarioLogado) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);
        Pedido pedido = buscarPedidoDoEntregador(pedidoId, entregador);

        if (pedido.getStatus() == StatusPedido.SAIU_ENTREGA) {
            // Idempotente: o app pode reenviar depois de um timeout de rede.
            return montarEntregaAtiva(pedido);
        }

        if (pedido.getStatus() != StatusPedido.PREPARANDO) {
            throw new RegraDeNegocioException(
                    "Só é possível coletar um pedido que esteja em preparo. Status atual: " + pedido.getStatus());
        }

        pedido.alterarStatus(StatusPedido.SAIU_ENTREGA);
        pedido.setColetadoEm(Instant.now());

        // Garante que o entregador fique marcado como EM_ENTREGA mesmo se o
        // aceite tiver acontecido antes desta versão do código.
        if (entregador.getStatusOperacional() != StatusOperacional.EM_ENTREGA) {
            entregador.setStatusOperacional(StatusOperacional.EM_ENTREGA);
            entregadorRepository.save(entregador);
        }

        pedidoRepository.save(pedido);
        notificarStatus(pedido);

        return montarEntregaAtiva(pedido);
    }

    /**
     * Baixa da entrega pelo entregador. Exige código de confirmação.
     */
    // A rejeição do código deve confirmar somente o contador/bloqueio. O lock
    // continua na mesma transação; outras falhas ainda provocam rollback.
    @Transactional(noRollbackFor = {CodigoEntregaInvalidoException.class, CodigoEntregaBloqueadoException.class})
    public void concluirEntrega(String pedidoId, Usuario usuarioLogado, String codigoEntrega) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);
        // A primeira leitura deve adquirir o lock. Uma leitura anterior sem lock
        // mantém contadores antigos no contexto JPA mesmo depois de esperar pelo lock.
        Pedido pedido = pedidoRepository.findLockedById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido não encontrado"));
        if (pedido.getEntregador() == null || !pedido.getEntregador().getId().equals(entregador.getId())) {
            throw new AcessoNegadoException("Este pedido não está atribuído a você.");
        }

        if (pedido.getStatus() == StatusPedido.ENTREGUE) {
            liberarEntregador(entregador);
            return; // idempotente
        }

        if (pedido.getStatus() != StatusPedido.SAIU_ENTREGA) {
            throw new RegraDeNegocioException(
                    "Só é possível concluir um pedido que já saiu para entrega. Status atual: " + pedido.getStatus());
        }

        codigoEntregaService.validarCodigo(pedido, codigoEntrega);

        pedido.alterarStatus(StatusPedido.ENTREGUE);
        pedido.setEntregueEm(Instant.now());
        pedidoRepository.save(pedido);

        liberarEntregador(entregador);
        notificarStatus(pedido);
    }

    /**
     * Devolve o entregador para a fila de disponíveis. Só volta pra ONLINE se
     * ele estava EM_ENTREGA — se ele tiver ficado OFFLINE de propósito no meio
     * da corrida, respeita a escolha dele.
     */
    private void liberarEntregador(Entregador entregador) {
        if (entregador.getStatusOperacional() == StatusOperacional.EM_ENTREGA) {
            entregador.setStatusOperacional(StatusOperacional.ONLINE);
            entregadorRepository.save(entregador);
        }
    }

    private Pedido buscarPedidoDoEntregador(String pedidoId, Entregador entregador) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido " + pedidoId + " não encontrado."));

        if (pedido.getEntregador() == null || !pedido.getEntregador().getId().equals(entregador.getId())) {
            throw new AcessoNegadoException("Este pedido não está atribuído a você.");
        }
        return pedido;
    }

    private EntregaAtivaResponseDTO montarEntregaAtiva(Pedido pedido) {
        Usuario cliente = usuarioRepository.findById(pedido.getUsuarioId()).orElse(null);
        return new EntregaAtivaResponseDTO(
                pedido,
                cliente != null ? cliente.getNome() : "Cliente",
                cliente != null ? cliente.getTelefone() : null
        );
    }

    private void notificarStatus(Pedido pedido) {
        eventPublisher.publishEvent(new br.com.nhac.backend_nhac.domain.pedido.PedidoStatusAtualizadoEvent(pedido.getId(), pedido.getStatus()));
    }

    /**
     * Marca como EXPIRADA toda oferta PENDENTE cujo prazo já passou. Chamado
     * pelo OfertaExpiracaoScheduler: sem isso, uma oferta recusada por silêncio
     * fica PENDENTE pra sempre e reaparece no GET /ofertas/pendentes assim que
     * o filtro de isExpirada() for relaxado, além de sujar o índice.
     */
    @Transactional
    public int expirarOfertasVencidas() {
        List<OfertaEntrega> vencidas =
                ofertaEntregaRepository.findByStatusAndExpiraEmBefore(StatusOferta.PENDENTE, Instant.now());
        if (vencidas.isEmpty()) {
            return 0;
        }
        vencidas.forEach(o -> o.setStatus(StatusOferta.EXPIRADA));
        ofertaEntregaRepository.saveAll(vencidas);
        return vencidas.size();
    }

    @Transactional(readOnly = true)
    public EntregaAtivaResponseDTO obterEntregaAtiva(Usuario usuarioLogado) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);

        Pedido pedidoAtivo = pedidoRepository.findFirstByEntregadorIdAndStatusIn(
                entregador.getId(),
                List.of(StatusPedido.PREPARANDO, StatusPedido.SAIU_ENTREGA)
        ).orElseThrow(() -> new IdNaoEncontradoException("Você não possui nenhuma entrega ativa no momento."));

        Usuario cliente = usuarioRepository.findById(pedidoAtivo.getUsuarioId()).orElse(null);
        String clienteNome = cliente != null ? cliente.getNome() : "Cliente";
        String clienteTelefone = cliente != null ? cliente.getTelefone() : null;

        return new EntregaAtivaResponseDTO(pedidoAtivo, clienteNome, clienteTelefone);
    }
}
