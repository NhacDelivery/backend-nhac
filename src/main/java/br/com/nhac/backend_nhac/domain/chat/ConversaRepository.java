package br.com.nhac.backend_nhac.domain.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversaRepository extends JpaRepository<Conversa, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversa c WHERE c.id = :id")
    Optional<Conversa> findLockedById(@Param("id") String id);

    // Leitura corrente após adquirir os locks dos usuários (inclusive em REPEATABLE READ).
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Conversa> findByClienteIdAndSegundoClienteId(String clienteId, String segundoClienteId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "loja")
    @Query("SELECT c FROM Conversa c WHERE c.participanteTipo = :tipo "
            + "AND (c.clienteId = :usuarioId OR c.segundoClienteId = :usuarioId)")
    Page<Conversa> listarDoParticipante(@Param("usuarioId") String usuarioId,
            @Param("tipo") ParticipanteTipo tipo, Pageable pageable);

    Optional<Conversa> findByLojaIdAndClienteIdAndParticipanteTipo(
            String lojaId, String clienteId, ParticipanteTipo participanteTipo);

    Page<Conversa> findByLojaIdOrderByUltimaMensagemEmDesc(String lojaId, Pageable pageable);

    Page<Conversa> findByLojaIdAndParticipanteTipoOrderByUltimaMensagemEmDesc(
            String lojaId, ParticipanteTipo participanteTipo, Pageable pageable);

    @Query("SELECT c FROM Conversa c WHERE c.id = :id AND c.loja.id = :lojaId")
    Optional<Conversa> findByIdAndLojaId(@Param("id") String id, @Param("lojaId") String lojaId);
}
