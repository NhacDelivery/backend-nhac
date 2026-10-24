package br.com.nhac.backend_nhac.domain.pedido;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Delimita as fases curtas do cancelamento, separadas da chamada ao gateway. */
@Component
public class PedidoTransacao {
    private final TransactionTemplate template;
    public PedidoTransacao(PlatformTransactionManager manager) { template = new TransactionTemplate(manager); }
    public <T> T executar(Supplier<T> operacao) { return template.execute(status -> operacao.get()); }
}
