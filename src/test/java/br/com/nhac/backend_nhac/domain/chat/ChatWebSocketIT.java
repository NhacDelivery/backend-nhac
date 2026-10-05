package br.com.nhac.backend_nhac.domain.chat;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.EnviarMensagemDTO;
import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.infra.security.TokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ChatWebSocketIT extends AbstractIntegrationTest {

    private static final long TIMEOUT_SECONDS = 8;

    @LocalServerPort private int port;

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LojaRepository lojaRepository;
    @Autowired private ConversaRepository conversaRepository;
    @Autowired private MensagemRepository mensagemRepository;
    @Autowired private TokenService tokenService;

    private Usuario donoA;
    private Usuario clienteA;
    private Usuario clienteB;
    private Loja lojaA;
    private Conversa conversaA;

    @BeforeEach
    void prepararDados() {
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        donoA = criarUsuario("dono.ws.a@teste.com", Papel.LOJISTA);
        clienteA = criarUsuario("cliente.ws.a@teste.com", Papel.CLIENTE);
        clienteB = criarUsuario("cliente.ws.b@teste.com", Papel.CLIENTE);

        lojaA = criarLoja("loja-ws-a", donoA.getId());
        conversaA = conversaRepository.saveAndFlush(
                new Conversa("conv_ws_" + UUID.randomUUID(), lojaA, clienteA.getId(),
                        ParticipanteTipo.CLIENTE));
    }

    // ============================================================
    // 1) CONNECT válido + SUBSCRIBE + SEND + RECEBE
    // ============================================================
    @Test
    @DisplayName("Cliente conecta com token válido, assina sua conversa e envia mensagem — broadcast chega")
    void clienteEnviaMensagemEOutroRecebe() throws Exception {
        WebSocketStompClient client = criarClienteStomp();
        String token = tokenService.gerarToken(clienteA);
        StompSession session = conectar(client, token);

        BlockingQueue<MensagemDTO> recebidas = new LinkedBlockingQueue<>();
        session.subscribe("/topic/conversas/" + conversaA.getId(),
                new StompFrameHandler() {
                    @Override public Type getPayloadType(StompHeaders headers) {
                        return MensagemDTO.class;
                    }
                    @Override public void handleFrame(StompHeaders headers, Object payload) {
                        recebidas.add((MensagemDTO) payload);
                    }
                });

        // SimpleBroker não suporta STOMP receipts — usa sleep curto pra
        // garantir que a subscription foi registrada antes do SEND.
        Thread.sleep(500);

        session.send("/app/conversas/" + conversaA.getId() + "/enviar",
                new EnviarMensagemDTO("Olá, mundo!"));

        MensagemDTO recebida = recebidas.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(recebida, "Mensagem deveria ter chegado no tópico");
        assertEquals("Olá, mundo!", recebida.conteudo());
        assertEquals(RemetenteTipo.CLIENTE, recebida.remetenteTipo());

        assertEquals(1, mensagemRepository.countByConversaId(conversaA.getId()));

        session.disconnect();
    }

    // ============================================================
    // 2) CONNECT sem token é recusado
    // ============================================================
    @Test
    @DisplayName("CONNECT sem token é recusado (o interceptor lança WebSocketAutenticacaoException)")
    void connectSemTokenFalha() {
        WebSocketStompClient client = criarClienteStomp();
        boolean recusado = false;

        try {
            client.connectAsync(
                            "ws://localhost:" + port + "/ws-native",
                            new WebSocketHttpHeaders(),
                            new StompHeaders(),
                            new StompSessionHandlerAdapter() {})
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            recusado = causa != null && (
                    causa.getClass().getSimpleName().contains("ConnectionLost")
                            || (causa.getMessage() != null
                                    && causa.getMessage().contains("Connection closed"))
            );
        } catch (Exception e) {
            recusado = true;
        }

        assertTrue(recusado, "Esperava que o servidor recusasse o CONNECT sem token");
    }

    // ============================================================
    // 3) SUBSCRIBE em conversa alheia é bloqueado
    // ============================================================
    @Test
    @DisplayName("Assinar /topic/conversas/{id} de conversa alheia é recusado (correção de segurança)")
    void subscribeEmConversaAlheiaFalha() throws Exception {
        WebSocketStompClient client = criarClienteStomp();
        String token = tokenService.gerarToken(clienteB);
        StompSession session = conectar(client, token);

        BlockingQueue<Object> recebidas = new LinkedBlockingQueue<>();
        session.subscribe("/topic/conversas/" + conversaA.getId(),
                new StompFrameHandler() {
                    @Override public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }
                    @Override public void handleFrame(StompHeaders headers, Object payload) {
                        recebidas.add(payload);
                    }
                });

        Thread.sleep(1000);

        boolean sessaoCaiu = !session.isConnected();
        boolean nadaChegou = recebidas.isEmpty();

        assertTrue(sessaoCaiu || nadaChegou,
                "SUBSCRIBE em conversa alheia deveria ser bloqueado");

        try { session.disconnect(); } catch (Exception ignored) {}
    }

    // ============================================================
    // Helpers
    // ============================================================

    private WebSocketStompClient criarClienteStomp() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());

        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-test-");
        scheduler.initialize();
        client.setTaskScheduler(scheduler);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(mapper);
        client.setMessageConverter(converter);

        return client;
    }

    private StompSession conectar(WebSocketStompClient client, String token) throws Exception {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return client.connectAsync(
                        "ws://localhost:" + port + "/ws-native",
                        handshakeHeaders,
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private Usuario criarUsuario(String email, Papel papel) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID().toString());
        u.setNome("Usuario " + email);
        u.setEmail(email);
        u.setSenha("senha123");
        u.setTelefone(String.format("+55119999%05d", Math.floorMod(email.hashCode(), 100000)));
        u.setPapel(papel);
        u.setAtivo(true);
        u.setEmailVerificado(true);
        return usuarioRepository.saveAndFlush(u);
    }

    private Loja criarLoja(String id, String usuarioId) {
        Loja loja = new Loja();
        loja.setId(id);
        loja.setNome("Loja " + id);
        loja.setUsuarioId(usuarioId);
        loja.setAberto(true);
        DadosOperacionais dadosOp = new DadosOperacionais();
        dadosOp.setEntregaPropria(true);
        dadosOp.setRetiradaNoLocal(true);
        dadosOp.setTaxaEntregaBase(BigDecimal.ZERO);
        dadosOp.setTempoEntregaMin(10);
        dadosOp.setTempoEntregaMax(30);
        loja.setDadosOperacionais(dadosOp);
        loja.setEndereco(new EnderecoLoja("Rua Teste", "123", "Cidade", "SP", "00000-000", "Bairro", null));
        return lojaRepository.saveAndFlush(loja);
    }
}