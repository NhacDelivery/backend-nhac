package br.com.nhac.backend_nhac.domain.entrega;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.nhac.backend_nhac.domain.entregador.*;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SuporteEntregaServiceTest {
  @Mock PedidoRepository pedidos;
  @Mock SolicitacaoSuporteRepository suporte;
  @Mock EntregadorRepository entregadores;
  @Mock OfertaEntregaRepository ofertas;
  @Mock ApplicationEventPublisher eventos;
  @InjectMocks SuporteEntregaService service;
  SolicitacaoSuporte ticket;
  Pedido pedido;
  Entregador original;

  Entregador entregador(String id) {
    var usuario = new Usuario();
    usuario.setId(id);
    usuario.setNome(id);
    var e = new Entregador();
    e.setId(id);
    e.setUsuario(usuario);
    e.setAtivo(true);
    e.setStatusOperacional(StatusOperacional.ONLINE);
    return e;
  }

  @BeforeEach
  void preparar() {
    ticket = new SolicitacaoSuporte();
    ticket.setId("protocolo");
    ticket.setUsuarioId("original");
    ticket.setPedidoId("pedido");
    ticket.setStatus("ABERTO");
    original = entregador("original");
    original.setStatusOperacional(StatusOperacional.EM_ENTREGA);
    pedido = new Pedido();
    pedido.setId("pedido");
    pedido.setEntregador(original);
    pedido.setStatus(StatusPedido.PREPARANDO);
    when(suporte.findLockedById("protocolo")).thenReturn(Optional.of(ticket));
  }

  void corridaAtiva() {
    when(entregadores.findLockedByUsuarioId("original")).thenReturn(Optional.of(original));
    when(pedidos.findLockedById("pedido")).thenReturn(Optional.of(pedido));
  }

  @Test
  void retiraAntesDaColetaEReabreDespacho() {
    corridaAtiva();
    when(suporte.save(ticket)).thenReturn(ticket);
    service.executar("protocolo", new SuporteEntregaController.AcaoDTO("RETIRAR", null, false));
    assertNull(pedido.getEntregador());
    assertEquals("RETIRADO", ticket.getStatus());
    assertEquals(StatusOperacional.OFFLINE, original.getStatusOperacional());
    verify(eventos).publishEvent(any(PedidoPreparandoEvent.class));
    service.executar("protocolo", new SuporteEntregaController.AcaoDTO("RETIRAR", null, false));
    verify(eventos, times(1)).publishEvent(any(PedidoPreparandoEvent.class));
  }

  @Test
  void naoPermiteAbandonarPedidoColetado() {
    corridaAtiva();
    pedido.setColetadoEm(Instant.now());
    assertThrows(
        RegraDeNegocioException.class,
        () ->
            service.executar(
                "protocolo", new SuporteEntregaController.AcaoDTO("RETIRAR", null, false)));
    assertSame(original, pedido.getEntregador());
    verifyNoInteractions(eventos);
  }

  @Test
  void exigeEntregaFisicaParaTransferirAposColeta() {
    corridaAtiva();
    pedido.setColetadoEm(Instant.now());
    var novo = entregador("novo");
    when(entregadores.findLockedByUsuarioId("novo")).thenReturn(Optional.of(novo));
    assertThrows(
        RegraDeNegocioException.class,
        () ->
            service.executar(
                "protocolo", new SuporteEntregaController.AcaoDTO("TRANSFERIR", "novo", false)));
    assertSame(original, pedido.getEntregador());
  }

  @Test
  void transferenciaConfirmaNovoResponsavel() {
    corridaAtiva();
    pedido.setColetadoEm(Instant.now());
    var novo = entregador("novo");
    when(entregadores.findLockedByUsuarioId("novo")).thenReturn(Optional.of(novo));
    when(entregadores.findByUsuarioId("novo")).thenReturn(Optional.of(novo));
    when(suporte.save(ticket)).thenReturn(ticket);
    service.executar("protocolo", new SuporteEntregaController.AcaoDTO("TRANSFERIR", "novo", true));
    assertSame(novo, pedido.getEntregador());
    assertEquals("novo", ticket.getResponsavelUsuarioId());
    assertEquals("TRANSFERIDO", ticket.getStatus());
    assertEquals(StatusOperacional.EM_ENTREGA, novo.getStatusOperacional());
  }

  @Test
  void respostaNaoApagaConclusaoDaTransferencia() {
    ticket.setStatus("TRANSFERIDO");
    when(suporte.save(ticket)).thenReturn(ticket);
    service.responder("protocolo", "Atendimento concluído.");
    assertEquals("TRANSFERIDO", ticket.getStatus());
  }
}
