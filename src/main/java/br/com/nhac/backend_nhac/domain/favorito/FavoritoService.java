package br.com.nhac.backend_nhac.domain.favorito;

import br.com.nhac.backend_nhac.domain.favorito.dto.FavoritoResponseDTO;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.domain.favorito.FavoritoRepository;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;

@Service
public class FavoritoService {

    private final FavoritoRepository favoritoRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaRepository lojaRepository;

    public FavoritoService(FavoritoRepository favoritoRepository, UsuarioRepository usuarioRepository, LojaRepository lojaRepository) {
        this.favoritoRepository = favoritoRepository;
        this.usuarioRepository = usuarioRepository;
        this.lojaRepository = lojaRepository;
    }

    public boolean usuarioSegueLoja(String usuarioId, String lojaId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new IdNaoEncontradoException("UsuÃƒÂ¡rio nÃƒÂ£o encontrado.");
        }
        if (!lojaRepository.existsById(lojaId)) {
            throw new IdNaoEncontradoException("Loja nÃƒÂ£o encontrada.");
        }

        return favoritoRepository.existsByUsuarioIdAndLojaId(usuarioId, lojaId);
    }
    public br.com.nhac.backend_nhac.domain.favorito.dto.FavoritoResponseDTO favoritar(String usuarioId, br.com.nhac.backend_nhac.domain.favorito.dto.FavoritoCreateDTO dto) {
        br.com.nhac.backend_nhac.domain.usuario.Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IdNaoEncontradoException("UsuÃ¡rio nÃ£o encontrado."));
        br.com.nhac.backend_nhac.domain.loja.Loja loja = lojaRepository.findById(dto.lojaId())
                .orElseThrow(() -> new IdNaoEncontradoException("Loja nÃ£o encontrada."));

        if (favoritoRepository.existsByUsuarioIdAndLojaId(usuarioId, dto.lojaId())) {
            throw new br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException("UsuÃ¡rio jÃ¡ favoritou esta loja.");
        }

        br.com.nhac.backend_nhac.domain.favorito.Favorito favorito = new br.com.nhac.backend_nhac.domain.favorito.Favorito();
        favorito.setId(java.util.UUID.randomUUID().toString());
        favorito.setUsuario(usuario);
        favorito.setLoja(loja);
        favorito.setCriadoEm(java.time.Instant.now());

        favorito = favoritoRepository.save(favorito);

        return FavoritoResponseDTO.de(favorito.getId(), loja, favorito.getCriadoEm());
    }

    public void removerFavorito(String usuarioId, String lojaId) {
        br.com.nhac.backend_nhac.domain.favorito.Favorito favorito = favoritoRepository.findByUsuarioIdAndLojaId(usuarioId, lojaId)
                .orElseThrow(() -> new IdNaoEncontradoException("Favorito nÃ£o encontrado."));
        
        favoritoRepository.delete(favorito);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<br.com.nhac.backend_nhac.domain.favorito.dto.FavoritoResponseDTO> listarFavoritos(String usuarioId, org.springframework.data.domain.Pageable pageable) {
        return favoritoRepository.findByUsuarioIdAndLojaIsAbertoTrue(usuarioId, pageable)
                .map(f -> FavoritoResponseDTO.de(f.getId(), f.getLoja(), f.getCriadoEm()));
    }

    public long contarSeguidores(String lojaId) {
        return favoritoRepository.countByLojaId(lojaId);
    }
}
