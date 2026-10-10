package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.domain.auth.VerificacaoTelefoneService;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelefoneContaServiceTest {
    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final VerificacaoTelefoneService sms = mock(VerificacaoTelefoneService.class);
    private final TelefoneContaService service = new TelefoneContaService(repository, sms);
    private final ValidarCodigoSmsDTO dto = new ValidarCodigoSmsDTO("+5511999991234", "123456", null);
    private Usuario usuario;

    @BeforeEach void preparar() {
        usuario = new Usuario();
        usuario.setId("minha-conta");
        usuario.setTelefone("+5511988881234");
        when(repository.findLockedById("minha-conta")).thenReturn(Optional.of(usuario));
    }

    @Test void telefoneDeOutraContaNaoConsomeCodigo() {
        var outra = new Usuario(); outra.setId("outra-conta");
        when(repository.findByTelefone(dto.telefone())).thenReturn(Optional.of(outra));
        assertThrows(RegraDeNegocioException.class, () -> service.atualizar("minha-conta", dto));
        verifyNoInteractions(sms);
        verify(repository, never()).saveAndFlush(any());
        assertEquals("+5511988881234", usuario.getTelefone());
    }

    @Test void codigoInvalidoNaoAlteraTelefone() {
        doThrow(new RegraDeNegocioException("Código inválido")).when(sms).validarCodigo(dto);
        assertThrows(RegraDeNegocioException.class, () -> service.atualizar("minha-conta", dto));
        assertEquals("+5511988881234", usuario.getTelefone());
        assertFalse(usuario.isTelefoneVerificado());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void alteraSomenteDepoisDeValidarPosse() {
        service.atualizar("minha-conta", dto);
        var ordem = inOrder(sms, repository);
        ordem.verify(sms).validarCodigo(dto);
        ordem.verify(repository).saveAndFlush(usuario);
        assertEquals(dto.telefone(), usuario.getTelefone());
        assertTrue(usuario.isTelefoneVerificado());
    }
}
