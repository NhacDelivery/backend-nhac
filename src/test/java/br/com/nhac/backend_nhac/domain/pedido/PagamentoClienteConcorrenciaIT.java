package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

class PagamentoClienteConcorrenciaIT extends AbstractMariaDbIntegrationTest {
    @Autowired PagamentoClienteRepository clientes;

    @Test
    void bancoPermiteSomenteUmClaimDaMesmaCriacao() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> claim = () -> {
                inicio.await();
                try { clientes.reservarCriacao("mesma-chave"); return true; }
                catch (DataIntegrityViolationException e) { return false; }
            };
            var a = executor.submit(claim); var b = executor.submit(claim); inicio.countDown();
            assertEquals(1, (a.get(15, TimeUnit.SECONDS) ? 1 : 0) + (b.get(15, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals("__CRIACAO_PENDENTE__", clientes.findById("mesma-chave").orElseThrow().getCustomerId());
        assertEquals(1, clientes.concluirCriacao("mesma-chave", "cus_confirmado"));
        assertEquals(0, clientes.concluirCriacao("mesma-chave", "cus_outro"));
        assertEquals(0, clientes.liberarRejeicao("mesma-chave"));
        assertEquals("cus_confirmado", clientes.findById("mesma-chave").orElseThrow().getCustomerId());
    }
}
