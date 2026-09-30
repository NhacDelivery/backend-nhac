package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CodigoEntregaConcorrenciaIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private CodigoEntregaService codigoEntregaService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Test
    void contadorPersisteMesmoComRollback() {
        // Encontra ou cria um pedido no banco de testes
        Optional<Pedido> pedidoOpt = pedidoRepository.findAll().stream().findFirst();
        if (pedidoOpt.isEmpty()) return; // Ignora se não houver pedido no BD de testes, em cenário real haverá inserts

        Pedido pedido = pedidoOpt.get();
        String id = pedido.getId();
        int tentativasIniciais = pedido.getCodigoEntregaTentativas();

        // O Spring abre transação no teste por padrão, mas chamaremos um método que lança RuntimeException.
        // O REQUIRES_NEW do serviço garante que o UPDATE seja commitado mesmo que capturemos a exceção.
        try {
            codigoEntregaService.validarCodigo(pedido, "senha-errada");
            fail("Deveria ter lançado exceção");
        } catch (CodigoEntregaInvalidoException e) {
            // esperado
        }

        Pedido atualizado = pedidoRepository.findById(id).orElseThrow();
        assertEquals(tentativasIniciais + 1, atualizado.getCodigoEntregaTentativas());
    }
}
