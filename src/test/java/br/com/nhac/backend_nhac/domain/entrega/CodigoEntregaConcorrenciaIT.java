package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Executa a mesma regressão HTTP/JWT/concorrência no MariaDB real do CI. */
class CodigoEntregaConcorrenciaIT extends CodigoEntregaFlowIT {
    @DynamicPropertySource
    static void mariaDb(DynamicPropertyRegistry registry) {
        AbstractMariaDbIntegrationTest.mariaDbProperties(registry);
    }
}
