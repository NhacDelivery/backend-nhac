package br.com.nhac.backend_nhac.domain.auth;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CodigoVerificacaoEmailRepository extends JpaRepository<CodigoVerificacaoEmail, Long> {

    @Modifying
    @Query("UPDATE CodigoVerificacaoEmail c SET c.utilizado = true, c.tokenCadastroHash = null WHERE c.email = :email AND c.consumidoEm IS NULL")
    void inativarCodigosAtivosPorEmail(@Param("email") String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<CodigoVerificacaoEmail> findTopByEmailAndTipoAndUtilizadoFalseAndDataExpiracaoAfterOrderByCriadoEmDesc(
        String email, CodigoVerificacaoEmail.TipoCodigo tipoCodigo, LocalDateTime dataLimite);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodigoVerificacaoEmail c where c.email = :email and c.tokenCadastroHash = :hash and c.tipo = br.com.nhac.backend_nhac.domain.auth.CodigoVerificacaoEmail.TipoCodigo.CADASTRO and c.validadoEm > :limite and c.consumidoEm is null")
    Optional<CodigoVerificacaoEmail> buscarProvaCadastro(@Param("email") String email,
            @Param("hash") String hash, @Param("limite") LocalDateTime limite);

}
