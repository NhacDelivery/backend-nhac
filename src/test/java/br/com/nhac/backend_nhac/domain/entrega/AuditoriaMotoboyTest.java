package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entregador.*;
import br.com.nhac.backend_nhac.domain.entregador.dto.CadastroEntregadorDTO;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuditoriaMotoboyTest {
    @Test
    void cadastroDevePersistirCpfSemTrocarPapel() {
        var repository = mock(EntregadorRepository.class);
        var users = mock(UsuarioRepository.class);
        var service = new EntregadorService(repository, users);
        var user = new Usuario();
        user.setId("u"); user.setPapel(Papel.CLIENTE);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.cadastrar(new CadastroEntregadorDTO("12345678900", "ABC1D23",
            TipoVeiculo.MOTO, "52998224725", "Preta", "Honda"), user);
        assertEquals("52998224725", user.getCpf());
        assertEquals(Papel.CLIENTE, user.getPapel());
        verify(users).save(user);
    }

    @Test
    void entregadorOcupadoNaoPodeAceitarOutraOferta() {
        verificarBloqueio(StatusOperacional.EM_ENTREGA, true);
    }

    @Test
    void entregadorOfflineNaoPodeAceitarOfertaAntiga() {
        verificarBloqueio(StatusOperacional.OFFLINE, true);
    }

    @Test
    void entregadorInativoNaoPodeAceitarOferta() {
        verificarBloqueio(StatusOperacional.ONLINE, false);
    }

    private void verificarBloqueio(StatusOperacional status, boolean ativo) {
        var pedidos = mock(PedidoRepository.class);
        var ofertas = mock(OfertaEntregaRepository.class);
        var serviceEntregador = mock(EntregadorService.class);
        var entregadores = mock(EntregadorRepository.class);
        var users = mock(UsuarioRepository.class);
        var service = new DespachoService(pedidos, ofertas, serviceEntregador,
            entregadores, users, mock(SimpMessagingTemplate.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));
        var user = new Usuario(); user.setId("u");
        var entregador = Entregador.builder().id("e").usuario(user).ativo(ativo).statusOperacional(status).build();
        when(serviceEntregador.buscarPorUsuarioComBloqueio(user)).thenReturn(entregador);
        var pedido = new Pedido(); pedido.setId("p"); pedido.setStatus(StatusPedido.PREPARANDO);
        var oferta = OfertaEntrega.builder().id("o").entregador(entregador).pedido(pedido)
            .status(StatusOferta.PENDENTE).expiraEm(Instant.now().plusSeconds(40)).build();
        when(ofertas.findByIdAndEntregadorId("o", "e")).thenReturn(Optional.of(oferta));
        assertThrows(RegraDeNegocioException.class, () -> service.aceitarOferta("o", user));
        verify(pedidos, never()).atribuirEntregadorSeDisponivel(anyString(), any());
    }

    @Test
    void rotaSemCoordenadasDoClienteNaoDeveInventarDestino() {
        var loja = new Loja();
        loja.setGeoLocalizacao(new GeoLocalizacao(-23.5, -46.6, "x"));
        var pedido = new Pedido(); pedido.setId("p"); pedido.setLoja(loja);
        assertThrows(RegraDeNegocioException.class, () -> new RotaService().calcularRota(pedido));
    }
}
