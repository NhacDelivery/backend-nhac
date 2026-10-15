package br.com.nhac.backend_nhac.domain.pedido;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PagamentoMetricas {
    private final MeterRegistry registry;
    public PagamentoMetricas(MeterRegistry registry) { this.registry = registry; }
    public void registrar(String etapa, String resultado) {
        registry.counter("nhac.pagamentos.operacoes", "etapa", etapa, "resultado", resultado).increment();
    }
    public record Leitura(String etapa, String resultado, double total) {}
    public List<Leitura> leituras() {
        return registry.find("nhac.pagamentos.operacoes").counters().stream()
                .map(c -> new Leitura(c.getId().getTag("etapa"), c.getId().getTag("resultado"), c.count())).toList();
    }
}
