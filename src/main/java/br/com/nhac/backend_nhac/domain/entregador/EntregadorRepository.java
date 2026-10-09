package br.com.nhac.backend_nhac.domain.entregador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntregadorRepository extends JpaRepository<Entregador, String> {

    Optional<Entregador> findByUsuarioId(String usuarioId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Entregador e where e.usuario.id = :usuarioId")
    Optional<Entregador> findLockedByUsuarioId(@org.springframework.data.repository.query.Param("usuarioId") String usuarioId);

    List<Entregador> findByStatusOperacionalAndAtivoTrue(StatusOperacional statusOperacional);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            update Entregador e set e.statusOperacional = br.com.nhac.backend_nhac.domain.entregador.StatusOperacional.OFFLINE,
                e.version = e.version + 1
            where e.statusOperacional = br.com.nhac.backend_nhac.domain.entregador.StatusOperacional.ONLINE
              and (e.ultimaAtualizacaoLocalizacao is null or e.ultimaAtualizacaoLocalizacao < :limite)
              and not exists (select p.id from Pedido p where p.entregador.id = e.id
                and p.status in (br.com.nhac.backend_nhac.domain.pedido.StatusPedido.PREPARANDO,
                                 br.com.nhac.backend_nhac.domain.pedido.StatusPedido.SAIU_ENTREGA))
            """)
    int expirarDisponibilidade(@org.springframework.data.repository.query.Param("limite") java.time.Instant limite);

    boolean existsByUsuarioId(String usuarioId);

    /**
     * Usado na autenticação (SecurityFilter / StompAuthChannelInterceptor)
     * para somar ROLE_ENTREGADOR às authorities de quem tem cadastro ativo,
     * sem depender de Usuario.papel (que continua CLIENTE mesmo depois do
     * cadastro de entregador — ver EntregadorService.cadastrar()).
     */
    boolean existsByUsuarioIdAndAtivoTrue(String usuarioId);
}
