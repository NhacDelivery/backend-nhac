package br.com.nhac.backend_nhac.domain.avaliacao_entregador;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO;
import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorRepository;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class AvaliacaoEntregadorConcorrenciaIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private AvaliacaoEntregadorService avaliacaoService;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private EntregadorRepository entregadorRepository;

    @Autowired
    private AvaliacaoEntregadorRepository avaliacaoRepository;

    @Test
    void naoDeveCriarDuasAvaliacoesParaOMesmoPedido() throws InterruptedException {
        Optional<Pedido> pedidoOpt = pedidoRepository.findAll().stream()
            .filter(p -> p.getEntregador() != null && p.getStatus() == br.com.nhac.backend_nhac.domain.pedido.StatusPedido.ENTREGUE)
            .findFirst();

        if (pedidoOpt.isEmpty()) return;

        Pedido pedido = pedidoOpt.get();
        Usuario dono = new Usuario();
        dono.setId(pedido.getUsuarioId());

        avaliacaoRepository.deleteAll(); // clear existing for the test

        int nThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        CountDownLatch latch = new CountDownLatch(nThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < nThreads; i++) {
            executor.submit(() -> {
                try {
                    avaliacaoService.criar(pedido.getId(), new AvaliacaoEntregadorCreateDTO(5, "bom"), dono);
                    successCount.incrementAndGet();
                } catch (RegraDeNegocioException e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(1, successCount.get(), "Apenas uma avaliação deve ter sucesso");
        assertEquals(1, errorCount.get(), "A outra avaliação deve falhar");
        
        long count = avaliacaoRepository.countByEntregadorId(pedido.getEntregador().getId());
        assertEquals(1, count);
    }
}
