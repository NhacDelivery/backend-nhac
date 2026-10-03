package br.com.nhac.backend_nhac.domain.usuario;


import br.com.nhac.backend_nhac.domain.usuario.EnderecoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnderecoUsuarioRepository extends JpaRepository<EnderecoUsuario, String> {
    List<EnderecoUsuario> findByUsuarioId(String usuarioId);

    // Preserva um padrão existente; caso contrário, escolhe o menor id da conta.
    Optional<EnderecoUsuario> findFirstByUsuarioIdAndIdNotOrderByIsPadraoDescIdAsc(
            String usuarioId, String enderecoId);
}
