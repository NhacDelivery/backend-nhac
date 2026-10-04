package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaBloqueadoException;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaInvalidoException;
import br.com.nhac.backend_nhac.exceptions.CodigoEntregaObrigatorioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class CodigoEntregaService {

    private final PedidoRepository pedidoRepository;

    @Value("${nhac.entrega.codigo.max-tentativas:5}")
    private int maxTentativas;

    @Value("${nhac.entrega.codigo.bloqueio-minutos:10}")
    private int bloqueioMinutos;

    public CodigoEntregaService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(noRollbackFor = {CodigoEntregaInvalidoException.class, CodigoEntregaBloqueadoException.class})
    public void validarCodigo(Pedido pedido, String codigoInformado) {
        if (codigoInformado == null || codigoInformado.isBlank()) {
            throw new CodigoEntregaObrigatorioException();
        }

        if (pedido.getCodigoEntregaBloqueadoAte() != null) {
            if (Instant.now().isBefore(pedido.getCodigoEntregaBloqueadoAte())) {
                throw new CodigoEntregaBloqueadoException(pedido.getCodigoEntregaBloqueadoAte());
            }
        }

        byte[] expected = pedido.getCodigoEntrega().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] actual = codigoInformado.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean correto = MessageDigest.isEqual(expected, actual);

        if (!correto) {
            registrarTentativaFalha(pedido);
            int tentativasAtuais = pedido.getCodigoEntregaTentativas() + 1;
            int restantes = Math.max(0, maxTentativas - tentativasAtuais);
            if (restantes <= 0) {
                Instant bloqueadoAte = Instant.now().plus(bloqueioMinutos, ChronoUnit.MINUTES);
                throw new CodigoEntregaBloqueadoException(bloqueadoAte);
            }
            throw new CodigoEntregaInvalidoException(restantes);
        }

        resetarTentativas(pedido.getId());
        // O UPDATE JPQL não atualiza a entidade gerenciada. Mantê-la coerente
        // evita que o flush de ENTREGUE regrave os contadores anteriores.
        pedido.setCodigoEntregaTentativas(0);
        pedido.setCodigoEntregaBloqueadoAte(null);
    }

    private void registrarTentativaFalha(Pedido pedido) {
        int tentativasAtuais = pedido.getCodigoEntregaTentativas() + 1;
        Instant bloqueadoAte = null;
        if (tentativasAtuais >= maxTentativas) {
            bloqueadoAte = Instant.now().plus(bloqueioMinutos, ChronoUnit.MINUTES);
        }
        pedidoRepository.incrementarTentativasCodigoEntrega(pedido.getId(), bloqueadoAte);
    }

    private void resetarTentativas(String pedidoId) {
        pedidoRepository.resetarTentativasCodigoEntrega(pedidoId);
    }
}
