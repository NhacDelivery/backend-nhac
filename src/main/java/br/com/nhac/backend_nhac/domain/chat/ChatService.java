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
import br.com.nhac.backend_nhac.domain.chat.MensagemEnviadaEvent;

/**
 * Ponto único de regra de negócio do chat.
 */
@Service
public class ChatService {

    private static final int PREVIEW_MAX_CHARS = 120;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.context.ApplicationEventPublisher avisoPublisher;
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
    public Page<MensagemDTO> listarMensagensDoCliente(String conversaId, Usuario usuarioLogado, Pageable pageable) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado.");
        }
        Conversa conversa = buscarConversaDoParticipante(conversaId, usuarioLogado.getId(), ParticipanteTipo.CLIENTE);
        return mensagemRepository.findByConversaIdOrderByEnviadaEmDesc(conversa.getId(), pageable)
                .map(MensagemDTO::new);
    }

    @Transactional
    public void marcarComoLidaPeloCliente(String conversaId, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado.");
        }
        Conversa conversa = buscarConversaDoParticipante(conversaId, usuarioLogado.getId(), ParticipanteTipo.CLIENTE);
        conversa.marcarComoLidaPeloCliente();
        conversaRepository.save(conversa);
    }

    // ---------- Lado ENTREGADOR (V039) ----------

    @Transactional
    public Conversa obterOuCriarConversaEntregador(String lojaId, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado para abrir uma conversa.");
        }
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
        conversa.marcarComoLidaPeloCliente();
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
        if (conversa.getParticipanteTipo() != tipoEsperado || !participanteId.equals(conversa.getClienteId())) {
            throw new AcessoNegadoException("Você não faz parte desta conversa.");
        }
        return conversa;
    }

    // ---------- Lado LOJA (lojista / funcionário) ----------

    @Transactional(readOnly = true)
    public Page<ConversaResumoDTO> listarConversasDaLoja(Usuario usuarioLogado, Pageable pageable) {
        return listarConversasDaLoja(usuarioLogado, null, pageable);
    }

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

    @Transactional(readOnly = true)
    public boolean podeAcessarConversa(String conversaId, Usuario usuario) {
        if (usuario == null) return false;
        return conversaRepository.findById(conversaId)
                .map(c -> usuario.getId().equals(c.getClienteId())
                        || lojaAccessService.temAcessoALoja(usuario, c.getLoja().getId())
                        || (c.isEntreClientes() && c.temCliente(usuario.getId())))
                .orElse(false);
    }

    private Conversa buscarConversaAcessivelPelaLoja(String conversaId, Usuario usuarioLogado) {
        Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
        return conversaRepository.findByIdAndLojaId(conversaId, loja.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Conversa não encontrada."));
    }

    // ---------- Envio (usado pelo controller WebSocket) ----------

    @Transactional
    public MensagemDTO enviarMensagem(String conversaId, Usuario remetente, String conteudo) {
        return enviarMensagem(conversaId, remetente, conteudo, null);
    }

    @Transactional
    public MensagemDTO enviarMensagem(String conversaId, Usuario remetente, String conteudo, String clientMessageId) {
        Conversa conversa = conversaRepository.findById(conversaId)
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

        if (avisoPublisher != null && tipo == RemetenteTipo.LOJA && conversa.getParticipanteTipo() == ParticipanteTipo.ENTREGADOR) {
            avisoPublisher.publishEvent(new br.com.nhac.backend_nhac.domain.notificacao.AvisoEntregadorEvent(
                "mensagem_"+mensagem.getId(),conversa.getClienteId(),"MENSAGEM","Você recebeu uma mensagem da loja.",
                null,conversa.getLoja().getId(),conversa.getLoja().getNome(),null));
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

    private String truncarPreview(String texto) {
        if (texto == null) return null;
        int total = texto.codePointCount(0, texto.length());
        if (total <= PREVIEW_MAX_CHARS) {
            return texto;
        }
        int limite = texto.offsetByCodePoints(0, PREVIEW_MAX_CHARS - 3);
        return texto.substring(0, limite) + "...";
    }
}
