package br.com.nhac.backend_nhac.domain.chat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.ConversaResumoDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
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

/**
 * Ponto único de regra de negócio do chat (item 7 da spec, arquitetura
 * decidida como WebSocket em vez de polling).
 *
 * Uma Conversa é sempre entre UMA loja e UM participante — cliente ou
 * entregador (V039) — nunca por pedido, é um canal contínuo. Do lado da loja,
 * "quem enviou" pode ser o dono ou qualquer funcionário — pro participante,
 * toda mensagem do lado loja aparece igual (remetenteTipo=LOJA), só guardamos
 * remetenteUsuarioId internamente pra rastreabilidade.
 */
@Service
public class ChatService {

    private static final int PREVIEW_MAX_CHARS = 120;

    private final ConversaRepository conversaRepository;
    private final MensagemRepository mensagemRepository;
    private final LojaRepository lojaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaAccessService lojaAccessService;
    private final EntregadorService entregadorService;

    public ChatService(ConversaRepository conversaRepository, MensagemRepository mensagemRepository,
                        LojaRepository lojaRepository, UsuarioRepository usuarioRepository,
                        LojaAccessService lojaAccessService, EntregadorService entregadorService) {
        this.conversaRepository = conversaRepository;
        this.mensagemRepository = mensagemRepository;
        this.lojaRepository = lojaRepository;
        this.usuarioRepository = usuarioRepository;
        this.lojaAccessService = lojaAccessService;
        this.entregadorService = entregadorService;
    }

    // ---------- Lado CLIENTE ----------

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
        if (usuario == null) return false;
        return conversaRepository.findById(conversaId)
                .map(c -> usuario.getId().equals(c.getClienteId())
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
            return new MensagemDTO(anterior);
        }
        Mensagem mensagem = new Mensagem(id, conversa, tipo, remetente.getId(), conteudo);
        mensagemRepository.save(mensagem);

        conversa.registrarNovaMensagem(tipo, truncarPreview(conteudo));
        conversaRepository.save(conversa);

        return new MensagemDTO(mensagem);
    }

    private RemetenteTipo resolverTipoRemetente(Conversa conversa, Usuario usuario) {
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
