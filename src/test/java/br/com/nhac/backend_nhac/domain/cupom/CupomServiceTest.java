package br.com.nhac.backend_nhac.domain.cupom;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CupomServiceTest {
    @Mock CupomRepository repository;
    @Mock UsuarioRepository usuarios;
    CupomService service;
    Cupom cupom;

    @BeforeEach
    void setup() {
        service = new CupomService(repository, usuarios, new br.com.nhac.backend_nhac.domain.loja.FreteService(), new BigDecimal("5.00"), new BigDecimal("25.00"), 30);
        cupom = new Cupom();
        cupom.setId("cupom");
        cupom.setUsuarioId("cliente");
        cupom.setTipoDesconto("FIXO");
        cupom.setValorDesconto(new BigDecimal("5.00"));
        cupom.setValorMinimoPedido(new BigDecimal("25.00"));
        cupom.setDataValidade(LocalDateTime.now().plusDays(30));
    }

    @Test
    void resgateRepetidoRetornaMesmoCupomSemRenovar() {
        when(usuarios.findLockedById("cliente")).thenReturn(Optional.of(new Usuario()));
        when(repository.findByUsuarioIdAndOrigem("cliente", "BOAS_VINDAS")).thenReturn(Optional.of(cupom));
        var validade = cupom.getDataValidade();
        assertEquals("cupom", service.ganharBoasVindas("cliente").id());
        assertEquals(validade, cupom.getDataValidade());
        verify(repository, never()).save(any());
    }

    @Test
    void primeiroResgateCriaCupomPessoalComRegrasConfiguradas() {
        when(usuarios.findLockedById("cliente")).thenReturn(Optional.of(new Usuario()));
        when(repository.findByUsuarioIdAndOrigem("cliente", "BOAS_VINDAS")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.ganharBoasVindas("cliente");
        assertEquals(new BigDecimal("5.00"), result.desconto());
        assertEquals(new BigDecimal("25.00"), result.usoMinimo());
        verify(repository).save(argThat(c -> "cliente".equals(c.getUsuarioId()) && "BOAS_VINDAS".equals(c.getOrigem())));
    }

    @Test
    void previaNaoConsomeMasPedidoConsomeECancelamentoLibera() {
        when(repository.findLockedById("cupom")).thenReturn(Optional.of(cupom));
        assertEquals(new BigDecimal("5.00"), service.validar("cliente", "cupom", new BigDecimal("25")).descontoAplicado());
        assertEquals(0, cupom.getUsosAtuais());
        assertEquals(new BigDecimal("5.00"), service.consumir("cliente", "cupom", new BigDecimal("25")));
        assertThrows(RegraDeNegocioException.class, () -> service.consumir("cliente", "cupom", new BigDecimal("25")));
        service.devolver("cliente", "cupom");
        assertEquals("DISPONIVEL", service.validar("cliente", "cupom", new BigDecimal("25")).status());
    }

    @Test
    void rejeitaOutraContaMinimoEExpiracao() {
        when(repository.findLockedById("cupom")).thenReturn(Optional.of(cupom));
        assertThrows(RegraDeNegocioException.class, () -> service.validar("outro", "cupom", new BigDecimal("25")));
        assertThrows(RegraDeNegocioException.class, () -> service.validar("cliente", "cupom", new BigDecimal("24.99")));
        cupom.setDataValidade(LocalDateTime.now().minusSeconds(1));
        assertThrows(RegraDeNegocioException.class, () -> service.validar("cliente", "cupom", new BigDecimal("25")));
        assertEquals(0, cupom.getUsosAtuais());
    }

    @Test
    void promocaoExigeLojaCorretaEAplicaLimitePorCliente() {
        var loja = new br.com.nhac.backend_nhac.domain.loja.Loja();
        loja.setId("loja-a"); loja.setNome("Loja A");
        cupom.setLoja(loja); cupom.setUsuarioId(null); cupom.setUsosAtuais(10);
        when(repository.findLockedById("cupom")).thenReturn(Optional.of(cupom));
        assertThrows(RegraDeNegocioException.class, () -> service.consumir("cliente","cupom",BigDecimal.TEN,"outra"));
        assertEquals(new BigDecimal("5.00"),service.consumir("cliente","cupom",new BigDecimal("30"),"loja-a"));
        when(repository.usosDoCliente("cliente","cupom")).thenReturn(1L);
        assertThrows(RegraDeNegocioException.class, () -> service.consumir("cliente","cupom",new BigDecimal("30"),"loja-a"));
    }
}
