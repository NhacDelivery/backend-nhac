package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.domain.auth.VerificacaoTelefoneService;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelefoneContaService {
    private final UsuarioRepository repository;
    private final VerificacaoTelefoneService verificacao;
    public TelefoneContaService(UsuarioRepository repository, VerificacaoTelefoneService verificacao) {
        this.repository = repository;
        this.verificacao = verificacao;
    }
    @Transactional(noRollbackFor = RegraDeNegocioException.class)
    public void atualizar(String id, ValidarCodigoSmsDTO dto) {
        Usuario usuario = repository.findLockedById(id).orElseThrow();
        String telefone = dto.telefone().trim();
        repository.findByTelefone(telefone).ifPresent(dono -> {
            if (!dono.getId().equals(id)) throw new RegraDeNegocioException("Este telefone já pertence a outra conta.");
        });
        verificacao.validarCodigo(dto);
        usuario.setTelefone(telefone);
        usuario.setTelefoneVerificado(true);
        repository.saveAndFlush(usuario);
    }
}
