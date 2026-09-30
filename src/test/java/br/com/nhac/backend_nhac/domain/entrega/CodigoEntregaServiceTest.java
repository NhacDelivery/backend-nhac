package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaBloqueadoException;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaInvalidoException;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaObrigatorioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CodigoEntregaServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    private CodigoEntregaService service;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        service = new CodigoEntregaService(pedidoRepository);
        ReflectionTestUtils.setField(service, "maxTentativas", 5);
        ReflectionTestUtils.setField(service, "bloqueioMinutos", 10);

        pedido = new Pedido();
        pedido.setId("ped-123");
        ReflectionTestUtils.setField(pedido, "codigoEntrega", "1234");
        ReflectionTestUtils.setField(pedido, "codigoEntregaTentativas", 0);
    }

    @Test
    void validarCodigo_Correto_DeveResetarTentativas() {
        service.validarCodigo(pedido, "1234");

        verify(pedidoRepository).resetarTentativasCodigoEntrega("ped-123");
        verify(pedidoRepository, never()).incrementarTentativasCodigoEntrega(any(), any());
    }

    @Test
    void validarCodigo_EmBranco_DeveLancarExcecao() {
        assertThrows(CodigoEntregaObrigatorioException.class, () -> service.validarCodigo(pedido, ""));
        assertThrows(CodigoEntregaObrigatorioException.class, () -> service.validarCodigo(pedido, null));
    }

    @Test
    void validarCodigo_Incorreto_DeveLancarInvalidoEIncrementar() {
        CodigoEntregaInvalidoException ex = assertThrows(CodigoEntregaInvalidoException.class,
                () -> service.validarCodigo(pedido, "9999"));

        assertEquals(4, ex.getDetails().get("tentativasRestantes"));
        verify(pedidoRepository).incrementarTentativasCodigoEntrega(eq("ped-123"), isNull());
    }

    @Test
    void validarCodigo_UltimaTentativaFalha_DeveLancarBloqueadoEMarcarTempo() {
        ReflectionTestUtils.setField(pedido, "codigoEntregaTentativas", 4);

        assertThrows(CodigoEntregaBloqueadoException.class, () -> service.validarCodigo(pedido, "9999"));

        verify(pedidoRepository).incrementarTentativasCodigoEntrega(eq("ped-123"), any(Instant.class));
    }

    @Test
    void validarCodigo_JaBloqueado_DeveLancarBloqueadoSemValidar() {
        ReflectionTestUtils.setField(pedido, "codigoEntregaBloqueadoAte", Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThrows(CodigoEntregaBloqueadoException.class, () -> service.validarCodigo(pedido, "1234"));

        verify(pedidoRepository, never()).incrementarTentativasCodigoEntrega(any(), any());
        verify(pedidoRepository, never()).resetarTentativasCodigoEntrega(any());
    }

    @Test
    void validarCodigo_BloqueioExpirou_DevePermitirNovaTentativa() {
        ReflectionTestUtils.setField(pedido, "codigoEntregaBloqueadoAte", Instant.now().minus(1, ChronoUnit.MINUTES));
        ReflectionTestUtils.setField(pedido, "codigoEntregaTentativas", 5);

        service.validarCodigo(pedido, "1234");

        verify(pedidoRepository).resetarTentativasCodigoEntrega("ped-123");
    }
}
