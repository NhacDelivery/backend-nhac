package br.com.nhac.backend_nhac.config;

import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class E2EDataLoaderTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EnderecoUsuarioRepository enderecos = mock(EnderecoUsuarioRepository.class);
    private final LojaRepository lojas = mock(LojaRepository.class);
    private final ProdutoRepository produtos = mock(ProdutoRepository.class);
    private final PedidoRepository pedidos = mock(PedidoRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test void todasAsContasUsamASenhaTemporariaDoRunner() {
        String senha = "senha-temporaria-exclusiva-e2e";
        when(encoder.encode(senha)).thenReturn("hash-da-senha-temporaria");
        var loader = new E2EDataLoader(jdbc, usuarios, enderecos, lojas, produtos, pedidos, encoder, senha);
        loader.run();
        verify(encoder, times(3)).encode(senha);
        verify(usuarios).saveAndFlush(argThat(u -> u.getId().equals(E2EDataLoader.USER_ID)
                && "hash-da-senha-temporaria".equals(u.getSenha())));
    }

    @Test void senhaAusenteOuCurtaFalhaAntesDeAlterarBanco() {
        for (String senha : new String[] {null, "", "curta"}) {
            assertThrows(IllegalArgumentException.class, () ->
                    new E2EDataLoader(jdbc, usuarios, enderecos, lojas, produtos, pedidos, encoder, senha));
        }
        verifyNoInteractions(jdbc, usuarios, encoder);
    }
}
