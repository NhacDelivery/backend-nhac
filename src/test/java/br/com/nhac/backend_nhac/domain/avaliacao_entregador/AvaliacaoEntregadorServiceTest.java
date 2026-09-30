package br.com.nhac.backend_nhac.domain.avaliacao_entregador;

import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO;
import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliacaoEntregadorServiceTest {

    @Mock
    private AvaliacaoEntregadorRepository repository;

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private AvaliacaoEntregadorService service;

    private Pedido pedido;
    private Usuario cliente;
    private Entregador entregador;

    @BeforeEach
    void setUp() {
        cliente = new Usuario();
        cliente.setId("cli-1");

        entregador = new Entregador();
        entregador.setId("ent-1");

        pedido = new Pedido();
        pedido.setId("ped-1");
        pedido.setUsuarioId("cli-1");
        pedido.setStatus(StatusPedido.ENTREGUE);
        pedido.setEntregador(entregador);
    }

    @Test
    void deveCriarAvaliacaoComSucesso() {
        AvaliacaoEntregadorCreateDTO dto = new AvaliacaoEntregadorCreateDTO(5, "Ótimo!");
        when(pedidoRepository.findById("ped-1")).thenReturn(Optional.of(pedido));
        when(repository.existsByPedidoId("ped-1")).thenReturn(false);
        when(repository.save(any())).thenAnswer(i -> {
            AvaliacaoEntregador a = i.getArgument(0);
            a.setId("av-1");
            return a;
        });

        var res = service.criar("ped-1", dto, cliente);

        assertNotNull(res);
        assertEquals("av-1", res.id());
        verify(repository).save(any());
        verify(repository).flush();
    }

    @Test
    void deveRejeitarSePedidoNaoEntregue() {
        pedido.setStatus(StatusPedido.SAIU_ENTREGA);
        when(pedidoRepository.findById("ped-1")).thenReturn(Optional.of(pedido));

        assertThrows(RegraDeNegocioException.class, () -> 
            service.criar("ped-1", new AvaliacaoEntregadorCreateDTO(5, null), cliente));
    }

    @Test
    void deveTratarDuplicidadePorDataIntegrity() {
        when(pedidoRepository.findById("ped-1")).thenReturn(Optional.of(pedido));
        when(repository.existsByPedidoId("ped-1")).thenReturn(false);
        when(repository.save(any())).thenThrow(DataIntegrityViolationException.class);

        assertThrows(RegraDeNegocioException.class, () -> 
            service.criar("ped-1", new AvaliacaoEntregadorCreateDTO(5, null), cliente));
    }
}
