package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entrega.dto.EntregaAtivaResponseDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.OfertaEntregaDTO;
import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorService;
import br.com.nhac.backend_nhac.domain.entregador.StatusOperacional;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.GeoLocalizacao;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DespachoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private OfertaEntregaRepository ofertaEntregaRepository;

    @Mock
    private EntregadorService entregadorService;

    @Mock
    private EntregadorRepository entregadorRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private CodigoEntregaService codigoEntregaService;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DespachoService despachoService;

    private Pedido pedido;
    private Loja loja;
    private Entregador entregador;
    private Usuario usuarioEntregador;

    @BeforeEach
    void setUp() {
        loja = new Loja();
        loja.setId("loja_1");
        loja.setNome("Pizzaria do Bairro");
        loja.setGeoLocalizacao(new GeoLocalizacao(-23.55052, -46.63330, "6fz2h3k"));
        loja.setEndereco(new EnderecoLoja("Rua das Flores", "100", "São Paulo", "SP", "01001-000", "Centro", null));

        pedido = new Pedido();
        pedido.setId("ped_1");
        pedido.setLoja(loja);
        pedido.setUsuarioId("cli_1");
        pedido.setStatus(StatusPedido.PREPARANDO);
        pedido.setTaxaFrete(new BigDecimal("7.50"));
        pedido.setValorTotal(new BigDecimal("50.00"));
        pedido.setFormaPagamento("PIX");

        usuarioEntregador = new Usuario();
        usuarioEntregador.setId("user_ent_1");
        usuarioEntregador.setNome("Marcos Motoboy");

        entregador = Entregador.builder()
                .id("ent_1")
                .usuario(usuarioEntregador)
                .statusOperacional(StatusOperacional.ONLINE)
                .latitudeAtual(-23.55100)
                .longitudeAtual(-46.63400)
                .ativo(true)
                .build();
    }

    @Test
    @DisplayName("Deve despachar pedido criando ofertas para entregadores proximos")
    void deveDespacharPedidoComSucesso() {
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));
        when(entregadorService.buscarEntregadoresProximos(-23.55052, -46.63330, 7.0))
                .thenReturn(List.of(new EntregadorService.EntregadorComDistancia(entregador, 0.5)));
        when(ofertaEntregaRepository.save(any(OfertaEntrega.class))).thenAnswer(i -> i.getArgument(0));

        List<OfertaEntregaDTO> ofertas = despachoService.despacharPedido("ped_1");

        assertNotNull(ofertas);
        assertEquals(1, ofertas.size());
        assertEquals("ped_1", ofertas.get(0).pedidoId());
        assertEquals("Pizzaria do Bairro", ofertas.get(0).lojaNome());

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/entregador/ent_1/ofertas"),
                any(OfertaEntregaDTO.class)
        );
    }

    @Test
    void naoDespachaComCoordenadasPadraoDaLoja() {
        loja.setGeoLocalizacao(new GeoLocalizacao(0, 0, null));
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));

        assertThrows(RegraDeNegocioException.class, () -> despachoService.despacharPedido("ped_1"));
        verifyNoInteractions(entregadorService, ofertaEntregaRepository);
    }

    @Test
    @DisplayName("Deve aceitar oferta, atribuir pedido ao entregador e manter status PREPARANDO até a retirada")
    void deveAceitarOfertaComSucesso() {
        OfertaEntrega oferta = OfertaEntrega.builder()
                .id("ofe_1")
                .pedido(pedido)
                .entregador(entregador)
                .status(StatusOferta.PENDENTE)
                .criadoEm(Instant.now())
                .expiraEm(Instant.now().plusSeconds(40))
                .build();

        when(entregadorService.buscarPorUsuarioComBloqueio(usuarioEntregador)).thenReturn(entregador);
        when(ofertaEntregaRepository.findByIdAndEntregadorId("ofe_1", "ent_1")).thenReturn(Optional.of(oferta));
        when(pedidoRepository.atribuirEntregadorSeDisponivel("ped_1", entregador)).thenAnswer(invocation -> {
            pedido.setEntregador(entregador);
            return 1;
        });
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));
        when(ofertaEntregaRepository.findByPedidoIdAndStatus("ped_1", StatusOferta.PENDENTE)).thenReturn(List.of(oferta));
        when(usuarioRepository.findById("cli_1")).thenReturn(Optional.of(new Usuario()));

        EntregaAtivaResponseDTO resposta = despachoService.aceitarOferta("ofe_1", usuarioEntregador);

        assertNotNull(resposta);
        assertEquals("ped_1", resposta.pedidoId());
        assertEquals(StatusPedido.PREPARANDO, pedido.getStatus());          // ← era SAIU_ENTREGA
        assertSame(entregador, pedido.getEntregador());                     // ← novo: valida a vinculação
        assertEquals(StatusOperacional.EM_ENTREGA, entregador.getStatusOperacional());
        assertEquals(StatusOferta.ACEITA, oferta.getStatus());

        verify(pedidoRepository, times(1)).atribuirEntregadorSeDisponivel("ped_1", entregador);
        verify(pedidoRepository, never()).save(pedido);
        verify(entregadorRepository, times(1)).save(entregador);
        verify(ofertaEntregaRepository, times(1)).save(oferta);
    }



    @Test
    @DisplayName("Deve perder a disputa de aceite sem prender o entregador em EM_ENTREGA")
    void deveTratarPerdaDaDisputaDeAceite() {
        OfertaEntrega oferta = OfertaEntrega.builder()
                .id("ofe_1")
                .pedido(pedido)
                .entregador(entregador)
                .status(StatusOferta.PENDENTE)
                .criadoEm(Instant.now())
                .expiraEm(Instant.now().plusSeconds(40))
                .build();

        when(entregadorService.buscarPorUsuarioComBloqueio(usuarioEntregador)).thenReturn(entregador);
        when(ofertaEntregaRepository.findByIdAndEntregadorId("ofe_1", "ent_1"))
                .thenReturn(Optional.of(oferta));
        when(pedidoRepository.atribuirEntregadorSeDisponivel("ped_1", entregador)).thenReturn(0);

        assertThrows(RegraDeNegocioException.class,
                () -> despachoService.aceitarOferta("ofe_1", usuarioEntregador));

        assertEquals(StatusOferta.EXPIRADA, oferta.getStatus());
        assertEquals(StatusOperacional.ONLINE, entregador.getStatusOperacional());
        verify(entregadorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Não deve duplicar oferta pendente no re-despacho")
    void naoDeveDuplicarOfertaPendente() {
        OfertaEntrega existente = OfertaEntrega.builder()
                .id("ofe_existente")
                .pedido(pedido)
                .entregador(entregador)
                .status(StatusOferta.PENDENTE)
                .criadoEm(Instant.now())
                .expiraEm(Instant.now().plusSeconds(40))
                .build();

        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));
        when(entregadorService.buscarEntregadoresProximos(-23.55052, -46.63330, 7.0))
                .thenReturn(List.of(new EntregadorService.EntregadorComDistancia(entregador, 0.5)));
        when(ofertaEntregaRepository.findByPedidoIdAndEntregadorIdAndStatus(
                "ped_1", "ent_1", StatusOferta.PENDENTE)).thenReturn(Optional.of(existente));

        List<OfertaEntregaDTO> ofertas = despachoService.despacharPedido("ped_1");

        assertTrue(ofertas.isEmpty());
        verify(ofertaEntregaRepository, never()).save(argThat(o -> !o.getId().equals("ofe_existente")));
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Despacho deve aceitar somente pedido PREPARANDO")
    void deveRecusarDespachoForaDePreparando() {
        pedido.setStatus(StatusPedido.PAGO);
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));

        assertThrows(RegraDeNegocioException.class,
                () -> despachoService.despacharPedido("ped_1"));

        verifyNoInteractions(entregadorService);
    }

    @Test
    @DisplayName("Deve marcar pedido como SAIU_ENTREGA quando o entregador coletar o pedido na loja")
    void deveMarcarSaiuEntregaAoColetarPedido() {
        pedido.setEntregador(entregador);   // simula pedido já aceito

        when(entregadorService.buscarPorUsuario(usuarioEntregador)).thenReturn(entregador);
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));
        when(usuarioRepository.findById("cli_1")).thenReturn(Optional.of(new Usuario()));

        EntregaAtivaResponseDTO resposta = despachoService.coletarPedido("ped_1", usuarioEntregador);

        assertNotNull(resposta);
        assertEquals(StatusPedido.SAIU_ENTREGA, pedido.getStatus());
        verify(pedidoRepository, times(1)).save(pedido);
    }

    @Test
    @DisplayName("Deve recusar aceite caso a oferta esteja expirada")
    void deveLancarExcecaoAoAceitarOfertaExpirada() {
        OfertaEntrega ofertaExpirada = OfertaEntrega.builder()
                .id("ofe_exp")
                .pedido(pedido)
                .entregador(entregador)
                .status(StatusOferta.PENDENTE)
                .criadoEm(Instant.now().minusSeconds(100))
                .expiraEm(Instant.now().minusSeconds(10))
                .build();

        when(entregadorService.buscarPorUsuarioComBloqueio(usuarioEntregador)).thenReturn(entregador);
        when(ofertaEntregaRepository.findByIdAndEntregadorId("ofe_exp", "ent_1")).thenReturn(Optional.of(ofertaExpirada));

        assertThrows(RegraDeNegocioException.class, () -> despachoService.aceitarOferta("ofe_exp", usuarioEntregador));
        assertEquals(StatusOferta.EXPIRADA, ofertaExpirada.getStatus());
    }

    @Test
    @DisplayName("Deve recusar oferta com sucesso")
    void deveRecusarOfertaComSucesso() {
        OfertaEntrega oferta = OfertaEntrega.builder()
                .id("ofe_1")
                .pedido(pedido)
                .entregador(entregador)
                .status(StatusOferta.PENDENTE)
                .criadoEm(Instant.now())
                .expiraEm(Instant.now().plusSeconds(40))
                .build();

        when(entregadorService.buscarPorUsuario(usuarioEntregador)).thenReturn(entregador);
        when(ofertaEntregaRepository.findByIdAndEntregadorId("ofe_1", "ent_1")).thenReturn(Optional.of(oferta));

        despachoService.recusarOferta("ofe_1", usuarioEntregador);

        assertEquals(StatusOferta.RECUSADA, oferta.getStatus());
        verify(ofertaEntregaRepository, times(1)).save(oferta);
    }

    @Test
    @DisplayName("Não deve oferecer nova corrida a entregador que já possui pedido ativo")
    void naoDeveOferecerSegundaCorridaParaEntregadorComPedidoAtivo() {
        when(pedidoRepository.findById("ped_1")).thenReturn(Optional.of(pedido));
        when(entregadorService.buscarEntregadoresProximos(-23.55052, -46.63330, 7.0))
                .thenReturn(List.of(new EntregadorService.EntregadorComDistancia(entregador, 0.5)));
        when(pedidoRepository.existsByEntregadorIdAndStatusIn(
                eq("ent_1"), anyList())).thenReturn(true);

        List<OfertaEntregaDTO> ofertas = despachoService.despacharPedido("ped_1");

        assertTrue(ofertas.isEmpty());
        verify(ofertaEntregaRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Deve concluir a entrega com o código correto")
    void deveConcluirEntregaComCodigoCorreto() {
        pedido.setEntregador(entregador);
        pedido.setStatus(StatusPedido.SAIU_ENTREGA);
        entregador.setStatusOperacional(StatusOperacional.EM_ENTREGA);

        when(entregadorService.buscarPorUsuario(usuarioEntregador)).thenReturn(entregador);
        when(pedidoRepository.findLockedById("ped_1")).thenReturn(Optional.of(pedido));

        despachoService.concluirEntrega("ped_1", usuarioEntregador, "1234");

        verify(codigoEntregaService).validarCodigo(pedido, "1234");
        assertEquals(StatusPedido.ENTREGUE, pedido.getStatus());
        assertEquals(StatusOperacional.ONLINE, entregador.getStatusOperacional());
        verify(pedidoRepository).save(pedido);
        verify(entregadorRepository).save(entregador);
    }

    @Test
    @DisplayName("concluirEntrega deve ser idempotente se já estiver ENTREGUE")
    void concluirEntregaIdempotente() {
        pedido.setEntregador(entregador);
        pedido.setStatus(StatusPedido.ENTREGUE);
        entregador.setStatusOperacional(StatusOperacional.EM_ENTREGA);

        when(entregadorService.buscarPorUsuario(usuarioEntregador)).thenReturn(entregador);
        when(pedidoRepository.findLockedById("ped_1")).thenReturn(Optional.of(pedido));

        despachoService.concluirEntrega("ped_1", usuarioEntregador, "1234");

        verifyNoInteractions(codigoEntregaService);
        assertEquals(StatusOperacional.ONLINE, entregador.getStatusOperacional());
        verify(pedidoRepository, never()).save(pedido);
    }

    @Test
    @DisplayName("concluirEntrega deve falhar se não estiver em SAIU_ENTREGA")
    void concluirEntregaFalhaSeStatusIncorreto() {
        pedido.setEntregador(entregador);
        pedido.setStatus(StatusPedido.PREPARANDO);

        when(entregadorService.buscarPorUsuario(usuarioEntregador)).thenReturn(entregador);
        when(pedidoRepository.findLockedById("ped_1")).thenReturn(Optional.of(pedido));

        assertThrows(RegraDeNegocioException.class, () ->
                despachoService.concluirEntrega("ped_1", usuarioEntregador, "1234"));

        verifyNoInteractions(codigoEntregaService);
    }

}
