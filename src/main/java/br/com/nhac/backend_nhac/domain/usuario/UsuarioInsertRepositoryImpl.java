package br.com.nhac.backend_nhac.domain.usuario;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro é INSERT. Nunca transforma colisão de identidade em UPDATE. */
public class UsuarioInsertRepositoryImpl implements UsuarioInsertRepository {
    @PersistenceContext private EntityManager entityManager;

    @Override
    @Transactional
    public Usuario inserir(Usuario usuario) {
        entityManager.persist(usuario);
        entityManager.flush();
        return usuario;
    }
}
