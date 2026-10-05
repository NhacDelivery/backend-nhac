package br.com.nhac.backend_nhac.domain.notificacao;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
@Service
public class PushEntregadorScheduler {
    private final AvisoEntregadorRepository avisos; private final PushEntregadorService push;
    public PushEntregadorScheduler(AvisoEntregadorRepository avisos,PushEntregadorService push) { this.avisos=avisos; this.push=push; }
    @Scheduled(fixedDelay=10000,initialDelay=10000)
    public void processar() {
        if(!push.configurado()) return;
        for(var a:avisos.findTop20ByPushEnviadoFalseAndTentativasLessThanAndProximaTentativaBeforeOrderByCriadoEmAsc(3,Instant.now())) push.enviar(a.getId());
    }
}
