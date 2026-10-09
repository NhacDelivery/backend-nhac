package br.com.nhac.backend_nhac.domain.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnderecoUsuarioRepositoryTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private EnderecoUsuarioRepository enderecoRepository;

    @Test
    void escolheMenorIdDaContaExcluindoEnderecoRemovido() {
        Usuario usuario = salvarUsuario("consulta-cliente");
        Usuario outro = salvarUsuario("consulta-outro");
        salvarEndereco("a-excluido", usuario, true);
        salvarEndereco("z-ultimo", usuario, false);
        salvarEndereco("c-primeiro", usuario, false);
        salvarEndereco("b-outra-conta", outro, true);

        var proximo = enderecoRepository.findFirstByUsuarioIdAndIdNotOrderByIsPadraoDescIdAsc(
                usuario.getId(), "a-excluido").orElseThrow();

        assertEquals("c-primeiro", proximo.getId());
        assertFalse(proximo.isPadrao());
    }

    @Test
    void priorizaPadraoExistenteMesmoComIdMaior() {
        Usuario usuario = salvarUsuario("consulta-cliente");
        salvarEndereco("a-excluido", usuario, true);
        salvarEndereco("b-sem-padrao", usuario, false);
        salvarEndereco("z-padrao", usuario, true);

        var proximo = enderecoRepository.findFirstByUsuarioIdAndIdNotOrderByIsPadraoDescIdAsc(
                usuario.getId(), "a-excluido").orElseThrow();

        assertEquals("z-padrao", proximo.getId());
        assertTrue(proximo.isPadrao());
    }

    @Test
    void retornaVazioQuandoNaoRestaEnderecoDaConta() {
        Usuario usuario = salvarUsuario("consulta-cliente");
        salvarEndereco("a-excluido", usuario, true);
        salvarEndereco("b-outra-conta", salvarUsuario("consulta-outro"), false);

        assertTrue(enderecoRepository.findFirstByUsuarioIdAndIdNotOrderByIsPadraoDescIdAsc(
                usuario.getId(), "a-excluido").isEmpty());
    }

    private Usuario salvarUsuario(String id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Cliente teste");
        usuario.setEmail(id + "@nhac.local");
        usuario.setTelefone(String.format("+55119999%05d", Math.floorMod(id.hashCode(), 100000)));
        return usuarioRepository.saveAndFlush(usuario);
    }

    private void salvarEndereco(String id, Usuario usuario, boolean padrao) {
        enderecoRepository.saveAndFlush(new EnderecoUsuario(
                id, usuario, "Rua Teste", "10", "Centro", "São Paulo", "SP", "01001-000", null, padrao));
    }
}
