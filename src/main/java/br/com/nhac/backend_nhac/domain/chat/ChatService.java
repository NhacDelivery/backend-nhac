package br.com.nhac.backend_nhac.domain.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.ConversaResumoDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.ConversaClienteResumoDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.InterlocutorDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.TipoConversa;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorService;
import br.com.nhac.backend_nhac.domain.notificacao.AvisoEntregadorEvent;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;

/**
 * Regras compartilhadas de chat REST/STOMP: canais contínuos de loja e chats
 * privados entre clientes. A identidade do remetente sempre vem da autenticação.
 */
@Service
public class ChatService {

    private static final int PREVIEW_MAX_CHARS = 120;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final LojaRepository lojaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaAccessService lojaAccessService;
    private final EntregadorService entregadorService;
    private final ApplicationEventPublisher eventPublisher;

    public ChatService(ConversaRepository conversaRepository, MensagemRepository mensagemRepository,
                        LojaRepository lojaRepository, UsuarioRepository usuarioRepository,
                        LojaAccessService lojaAccessService, EntregadorService entregadorService,
                        ApplicationEventPublisher eventPublisher) {
        this.conversaRepository = conversaRepository;
        this.mensagemRepository = mensagemRepository;
        this.lojaRepository = lojaRepository;
        this.usuarioRepository = usuarioRepository;
        this.lojaAccessService = lojaAccessService;
        this.entregadorService = entregadorService;
        this.eventPublisher = eventPublisher;
    }

    // ---------- Lado CLIENTE ----------

    private void exigirCliente(Usuario usuario) {
        if (usuario == null || !usuario.isAtivo() || usuario.getPapel() != Papel.CLIENTE) {
            throw new AcessoNegadoException("Apenas clientes ativos podem acessar este chat.");
        }
    }

    @Transactional
    public Conversa obterOuCriarConversaEntreClientes(String outroClienteId, Usuario usuario) {
        exigirCliente(usuario);
        // Usa o ID armazenado: a collation do MariaDB pode aceitar UUID em outra caixa.
        outroClienteId = usuarioRepository.findById(outroClienteId)
                .orElseThrow(() -> new IdNaoEncontradoException("Cliente não encontrado.")).getId();
        if (usuario.getId().equals(outroClienteId)) {
            throw new RegraDeNegocioException("Não é possível abrir uma conversa consigo mesmo.");
        }
        String primeiroId = usuario.getId().compareTo(outroClienteId) < 0 ? usuario.getId() : outroClienteId;
        String segundoId = primeiroId.equals(usuario.getId()) ? outroClienteId : usuario.getId();
        // A mesma ordem nos dois sentidos evita deadlocks e duplicação na abertura simultânea.
        Usuario primeiro = usuarioRepository.findLockedById(primeiroId)
                .orElseThrow(() -> new IdNaoEncontradoException("Cliente não encontrado."));
        Usuario segundo = usuarioRepository.findLockedById(segundoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Cliente não encontrado."));
        if (primeiro.getPapel() != Papel.CLIENTE || segundo.getPapel() != Papel.CLIENTE
                || !primeiro.isAtivo() || !segundo.isAtivo()) {
            throw new RegraDeNegocioException("O destinatário deve ser um cliente ativo.");
        }
        return conversaRepository.findByClienteIdAndSegundoClienteId(primeiroId, segundoId)
                .orElseGet(() -> conversaRepository.saveAndFlush(
                        Conversa.entreClientes("conv_" + UUID.randomUUID(), primeiroId, segundoId)));
    }

    @Transactional(readOnly = true)
    public Page<ConversaClienteResumoDTO> listarConversasDoCliente(Usuario usuario, Pageable pageable) {
        exigirCliente(usuario);
        return listarConversasDoParticipante(usuario, ParticipanteTipo.CLIENTE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ConversaClienteResumoDTO> listarConversasDoEntregador(Usuario usuario, Pageable pageable) {
        if (usuario == null) throw new AcessoNegadoException("É necessário estar autenticado.");
        entregadorService.buscarPorUsuario(usuario);
        return listarConversasDoParticipante(usuario, ParticipanteTipo.ENTREGADOR, pageable);
    }

    private Page<ConversaClienteResumoDTO> listarConversasDoParticipante(
            Usuario usuario, ParticipanteTipo tipo, Pageable pageable) {
        Pageable ordenada = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100),
                Sort.by(Sort.Order.desc("ultimaMensagemEm"), Sort.Order.desc("id")));
        Page<Conversa> pagina = conversaRepository.listarDoParticipante(usuario.getId(), tipo, ordenada);
        List<String> ids = pagina.stream().filter(Conversa::isEntreClientes)
                .map(c -> c.getClienteId().equals(usuario.getId()) ? c.getSegundoClienteId() : c.getClienteId())
                .distinct().toList();
        Map<String, Usuario> interlocutores = usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, u -> u));
        return pagina.map(c -> {
            InterlocutorDTO interlocutor;
            if (c.isEntreClientes()) {
                String id = c.getClienteId().equals(usuario.getId()) ? c.getSegundoClienteId() : c.getClienteId();
                Usuario outro = interlocutores.get(id);
                interlocutor = new InterlocutorDTO(id, outro == null ? "Usuário" : outro.getNome(),
                        outro == null ? null : outro.getImagemUrl());
            } else {
                interlocutor = new InterlocutorDTO(c.getLoja().getId(), c.getLoja().getNome(), c.getLoja().getImagemUrl());
            }
            return new ConversaClienteResumoDTO(c.getId(), c.isEntreClientes() ? TipoConversa.CLIENTE : TipoConversa.LOJA,
                    interlocutor, c.getUltimaMensagemPreview(), c.getUltimaMensagemEm(), c.naoLidasPara(usuario.getId()));
        });
    }

    @Transactional
    public Conversa obterOuCriarConversa(String lojaId, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado para abrir uma conversa.");
        }
        if (usuarioLogado.getPapel() != Papel.CLIENTE) {
            throw new AcessoNegadoException("Apenas clientes podem iniciar conversas com lojas.");
        }
        return obterOuCriarConversaInterna(lojaId, usuarioLogado.getId(), ParticipanteTipo.CLIENTE);
    }

    @Transactional(readOnly = true)
    public void validarConversaDoCliente(String conversaId, Usuario usuario) {
        exigirCliente(usuario);
        buscarConversaDoParticipante(conversaId, usuario.getId(), ParticipanteTipo.CLIENTE);
    }

    @Transactional(readOnly = true)
    public Page<MensagemDTO> listarMensagensDoCliente(String conversaId, Usuario usuarioLogado, Pageable pageable) {
        exigirCliente(usuarioLogado);
        Conversa conversa = buscarConversaDoParticipante(conversaId, usuarioLogado.getId(), ParticipanteTipo.CLIENTE);
        return mensagemRepository.findByConversaIdOrderByEnviadaEmDesc(conversa.getId(), pageable)
                .map(MensagemDTO::new);
    }

    @Transactional
    public void marcarComoLidaPeloCliente(String conversaId, Usuario usuarioLogado) {
        exigirCliente(usuarioLogado);
        Conversa conversa = conversaRepository.findLockedById(conversaId)
                .orElseThrow(() -> new IdNaoEncontradoException("Conversa não encontrada."));
        if (conversa.getParticipanteTipo() != ParticipanteTipo.CLIENTE || !conversa.temCliente(usuarioLogado.getId())) {
            throw new AcessoNegadoException("Você não faz parte desta conversa.");
        }
        conversa.marcarComoLidaPor(usuarioLogado.getId());
        conversaRepository.save(conversa);
    }

    // ---------- Lado ENTREGADOR (V039) ----------

    /**
     * Espelha obterOuCriarConversa, mas pro app do motoboy: uma conversa por
     * par (loja, entregador), independente das conversas que a mesma loja tem
     * com seus clientes.
     */
    @Transactional
    public Conversa obterOuCriarConversaEntregador(String lojaId, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado para abrir uma conversa.");
        }
        // O papel principal pode continuar CLIENTE; ROLE_ENTREGADOR é
        // concedida dinamicamente pelo vínculo ativo em tb_entregadores.
        // A regra de domínio correta, portanto, é exigir o perfil real.
        entregadorService.buscarPorUsuario(usuarioLogado);
        return obterOuCriarConversaInterna(lojaId, usuarioLogado.getId(), ParticipanteTipo.ENTREGADOR);
    }

    @Transactional(readOnly = true)
    public Page<MensagemDTO> listarMensagensDoEntregador(String conversaId, Usuario usuarioLogado, Pageable pageable) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado.");
        }
        Conversa conversa = buscarConversaDoParticipante(conversaId, usuarioLogado.getId(), ParticipanteTipo.ENTREGADOR);
        return mensagemRepository.findByConversaIdOrderByEnviadaEmDesc(conversa.getId(), pageable)
                .map(MensagemDTO::new);
    }

    @Transactional
    public void marcarComoLidaPeloEntregador(String conversaId, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado.");
        }
        Conversa conversa = buscarConversaDoParticipante(conversaId, usuarioLogado.getId(), ParticipanteTipo.ENTREGADOR);
        conversa.marcarComoLidaPeloCliente(); // campo genérico do lado "participante" — ver Conversa
        conversaRepository.save(conversa);
    }

    private Conversa obterOuCriarConversaInterna(String lojaId, String participanteId, ParticipanteTipo tipo) {
        return conversaRepository.findByLojaIdAndClienteIdAndParticipanteTipo(lojaId, participanteId, tipo)
                .orElseGet(() -> {
                    try {
                        Loja loja = lojaRepository.findById(lojaId)
                                .orElseThrow(() -> new LojaNaoEncontradaException(lojaId));
                        Conversa nova = new Conversa("conv_" + UUID.randomUUID(), loja, participanteId, tipo);
                        return conversaRepository.saveAndFlush(nova);
                    } catch (DataIntegrityViolationException e) {
                        return conversaRepository.findByLojaIdAndClienteIdAndParticipanteTipo(lojaId, participanteId, tipo)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Conversa deveria existir após violação de unique", e));
                    }
                });
    }

    private Conversa buscarConversaDoParticipante(String conversaId, String participanteId, ParticipanteTipo tipoEsperado) {
        Conversa conversa = conversaRepository.findById(conversaId)
                .orElseThrow(() -> new IdNaoEncontradoException("Conversa não encontrada."));
        if (conversa.getParticipanteTipo() != tipoEsperado || !conversa.temCliente(participanteId)) {
            throw new AcessoNegadoException("Você não faz parte desta conversa.");
        }
        return conversa;
    }

    // ---------- Lado LOJA (lojista / funcionário) ----------

    @Transactional(readOnly = true)
    public Page<ConversaResumoDTO> listarConversasDaLoja(Usuario usuarioLogado, Pageable pageable) {
        return listarConversasDaLoja(usuarioLogado, null, pageable);
    }

    /**
     * tipoFiltro null lista os dois canais juntos (clientes e entregadores),
     * ordenados só pela última mensagem — útil pra uma caixa de entrada única
     * no painel. Passar CLIENTE ou ENTREGADOR filtra só aquele canal.
     */
    @Transactional(readOnly = true)
    public Page<ConversaResumoDTO> listarConversasDaLoja(Usuario usuarioLogado, ParticipanteTipo tipoFiltro, Pageable pageable) {
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        Page<Conversa> pagina = tipoFiltro == null
                ? conversaRepository.findByLojaIdOrderByUltimaMensagemEmDesc(loja.getId(), pageable)
                : conversaRepository.findByLojaIdAndParticipanteTipoOrderByUltimaMensagemEmDesc(loja.getId(), tipoFiltro, pageable);

        List<String> participanteIds = pagina.getContent().stream().map(Conversa::getClienteId).distinct().toList();
        Map<String, String> nomesPorId = usuarioRepository.findAllById(participanteIds).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome, (a, b) -> a));

        return pagina.map(conversa -> new ConversaResumoDTO(conversa, nomesPorId.getOrDefault(conversa.getClienteId(), "Usuário")));
    }

    @Transactional(readOnly = true)
    public Page<MensagemDTO> listarMensagens(String conversaId, Usuario usuarioLogado, Pageable pageable) {
        Conversa conversa = buscarConversaAcessivelPelaLoja(conversaId, usuarioLogado);
        return mensagemRepository.findByConversaIdOrderByEnviadaEmDesc(conversa.getId(), pageable).map(MensagemDTO::new);
    }

    @Transactional
    public void marcarComoLidaPelaLoja(String conversaId, Usuario usuarioLogado) {
        Conversa conversa = buscarConversaAcessivelPelaLoja(conversaId, usuarioLogado);
        conversa.marcarComoLidaPelaLoja();
        conversaRepository.save(conversa);
    }

    // ---------- Consulta usada pelo interceptor WebSocket ----------

    /**
     * Verifica se o usuário autenticado pode acessar a conversa, tanto pra
     * SUBSCRIBE no /topic/conversas/{id} quanto pra enviar mensagem.
     *
     * Acesso permitido:
     *  - participante dono da conversa (cliente OU entregador — usuario.getId() == conversa.clienteId)
     *  - Dono ou funcionário da loja da conversa (via LojaAccessService)
     *  - ADMIN (bypass via LojaAccessService.temAcessoALoja)
     */
    @Transactional(readOnly = true)
    public boolean podeAcessarConversa(String conversaId, Usuario usuario) {
        if (usuario == null || !usuario.isAtivo()) return false;
        return conversaRepository.findById(conversaId)
                .map(c -> c.isEntreClientes()
                        ? usuario.getPapel() == Papel.CLIENTE && c.temCliente(usuario.getId())
                        : usuario.getId().equals(c.getClienteId())
                                || lojaAccessService.temAcessoALoja(usuario, c.getLoja().getId()))
                .orElse(false);
    }

    private Conversa buscarConversaAcessivelPelaLoja(String conversaId, Usuario usuarioLogado) {
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        return conversaRepository.findByIdAndLojaId(conversaId, loja.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Conversa não encontrada."));
    }

    // ---------- Envio (usado pelo controller WebSocket) ----------

    /**
     * Envia uma mensagem em nome de quem estiver autenticado na sessão WS.
     * Determina remetenteTipo pela relação do usuário com a conversa:
     * participante dono da conversa manda como CLIENTE ou ENTREGADOR (o que
     * for o participanteTipo da conversa); dono/funcionário/admin da loja da
     * conversa manda como LOJA. Qualquer outro usuário toma AcessoNegadoException.
     */
    @Transactional
    public MensagemDTO enviarMensagem(String conversaId, Usuario remetente, String conteudo) {
        return enviarMensagem(conversaId, remetente, conteudo, null);
    }

    @Transactional
    public MensagemDTO enviarMensagem(String conversaId, Usuario remetente, String conteudo, String clientMessageId) {
        if (remetente == null || !remetente.isAtivo()) {
            throw new AcessoNegadoException("É necessário estar autenticado.");
        }
        if (conteudo == null || conteudo.isBlank() || conteudo.length() > 4000) {
            throw new RegraDeNegocioException("Mensagem deve conter de 1 a 4000 caracteres e não pode ser vazia.");
        }
        if (clientMessageId != null && !UUID_PATTERN.matcher(clientMessageId).matches()) {
            throw new RegraDeNegocioException("clientMessageId deve ser um UUID válido.");
        }
        Conversa conversa = conversaRepository.findLockedById(conversaId)
                .orElseThrow(() -> new IdNaoEncontradoException("Conversa não encontrada."));

        RemetenteTipo tipo = resolverTipoRemetente(conversa, remetente);

        String id = "msg_" + (clientMessageId == null ? UUID.randomUUID() : UUID.fromString(clientMessageId));
        var existente = mensagemRepository.findById(id);
        if (existente.isPresent()) {
            Mensagem anterior = existente.get();
            if (!anterior.getConversa().getId().equals(conversaId)
                    || !anterior.getRemetenteUsuarioId().equals(remetente.getId())
                    || !anterior.getConteudo().equals(conteudo)) {
                throw new AcessoNegadoException("Identificador de mensagem já utilizado.");
            }
            MensagemDTO dto = new MensagemDTO(anterior);
            eventPublisher.publishEvent(new MensagemEnviadaEvent(dto));
            return dto;
        }
        Mensagem mensagem = new Mensagem(id, conversa, tipo, remetente.getId(), conteudo);
        mensagemRepository.save(mensagem);

        conversa.registrarNovaMensagem(tipo, remetente.getId(), truncarPreview(conteudo));
        conversaRepository.save(conversa);

        if (tipo == RemetenteTipo.LOJA && conversa.getParticipanteTipo() == ParticipanteTipo.ENTREGADOR) {
            eventPublisher.publishEvent(new AvisoEntregadorEvent(
                    "mensagem_" + mensagem.getId(), conversa.getClienteId(), "MENSAGEM",
                    "Você recebeu uma mensagem da loja.", null, conversa.getLoja().getId(),
                    conversa.getLoja().getNome(), null));
        }
        MensagemDTO dto = new MensagemDTO(mensagem);
        eventPublisher.publishEvent(new MensagemEnviadaEvent(dto));
        return dto;
    }

    private RemetenteTipo resolverTipoRemetente(Conversa conversa, Usuario usuario) {
        if (conversa.isEntreClientes()) {
            exigirCliente(usuario);
            if (conversa.temCliente(usuario.getId())) return RemetenteTipo.CLIENTE;
            throw new AcessoNegadoException("Acesso negado: você não faz parte desta conversa.");
        }
        if (usuario.getId().equals(conversa.getClienteId())) {
            return conversa.getParticipanteTipo() == ParticipanteTipo.ENTREGADOR
                    ? RemetenteTipo.ENTREGADOR
                    : RemetenteTipo.CLIENTE;
        }
        if (lojaAccessService.temAcessoALoja(usuario, conversa.getLoja().getId())) {
            return RemetenteTipo.LOJA;
        }
        throw new AcessoNegadoException("Acesso negado: você não faz parte desta conversa.");
    }

    /**
     * Corta a string em até PREVIEW_MAX_CHARS caracteres "visíveis",
     * respeitando code points (emoji não é cortado no meio de um surrogate pair).
     */
    private String truncarPreview(String texto) {
        if (texto == null) return null;
        int total = texto.codePointCount(0, texto.length());
        if (total <= PREVIEW_MAX_CHARS) {
            return texto;
        }
        // Reserva 3 chars pro "..."
        int limite = texto.offsetByCodePoints(0, PREVIEW_MAX_CHARS - 3);
        return texto.substring(0, limite) + "...";
    }
}
