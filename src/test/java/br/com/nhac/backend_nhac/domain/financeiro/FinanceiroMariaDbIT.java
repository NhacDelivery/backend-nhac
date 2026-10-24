package br.com.nhac.backend_nhac.domain.financeiro;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Executa os mesmos contratos e limites de data no dialeto usado em produção. */
class FinanceiroMariaDbIT extends FinanceiroFlowIT {
    @DynamicPropertySource
    static void mariaDb(DynamicPropertyRegistry registry) {
        AbstractMariaDbIntegrationTest.mariaDbProperties(registry);
    }
}
