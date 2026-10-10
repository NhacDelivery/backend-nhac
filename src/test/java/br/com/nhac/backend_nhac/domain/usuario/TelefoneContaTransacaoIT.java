package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.*;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class TelefoneContaTransacaoIT extends AbstractIntegrationTest {
    @Autowired TelefoneContaService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired CodigoVerificacaoRepository codigos;
    @Autowired PlatformTransactionManager transactions;
    private static final String TELEFONE = "+5511999991234";

    private CodigoVerificacao preparar() {
        usuarios.saveAndFlush(Usuario.builder().id("conta").nome("Conta").papel(Papel.CLIENTE)
                .telefone("+5511988881234").build());
        return codigos.saveAndFlush(CodigoVerificacao.builder().telefone(TELEFONE).codigo("123456")
                .dataExpiracao(LocalDateTime.now().plusMinutes(5)).tentativas(0).utilizado(false).build());
    }

    @Test void erroDeCodigoPreservaTentativasSemAlterarConta() {
        var codigo = preparar();
        for (int i = 0; i < 4; i++) {
            assertThrows(RegraDeNegocioException.class, () -> service.atualizar("conta",
                    new ValidarCodigoSmsDTO(TELEFONE, "000000", null)));
        }
        var salvo = codigos.findById(codigo.getId()).orElseThrow();
        assertEquals(3, salvo.getTentativas());
        assertTrue(salvo.isUtilizado());
        assertEquals("+5511988881234", usuarios.findById("conta").orElseThrow().getTelefone());
    }

    @Test void erroPosteriorDesfazConsumoDoCodigoEAlteracaoDaConta() {
        var codigo = preparar();
        assertThrows(RegraDeNegocioException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.atualizar("conta", new ValidarCodigoSmsDTO(TELEFONE, "123456", null));
            throw new RegraDeNegocioException("Falha posterior à atualização");
        }));
        assertFalse(codigos.findById(codigo.getId()).orElseThrow().isUtilizado());
        var usuario = usuarios.findById("conta").orElseThrow();
        assertEquals("+5511988881234", usuario.getTelefone());
        assertFalse(usuario.isTelefoneVerificado());
    }
}
