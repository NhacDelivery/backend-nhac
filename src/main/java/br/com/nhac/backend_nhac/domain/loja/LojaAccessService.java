package br.com.nhac.backend_nhac.domain.loja;

import org.springframework.stereotype.Service;

import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException;

/**
 * Único ponto de resolução de "qual loja este usuário logado pode gerenciar".
 * Antes desta classe, cada service comparava produto.getLoja().getUsuarioId()
 * diretamente com o id do usuário logado — o que só funciona para o DONO da
 * loja. Com a introdução de funcionários (Papel.FUNCIONARIO), passamos a ter
 * dois jeitos de estar autorizado a mexer numa loja:
 *   - ser o dono (tb_lojas.usuario_id == usuario.id)
 *   - ser funcionário vinculado a ela (usuario.lojaVinculadaId == loja.id)
 *
 * ADMIN não passa por aqui: cada service continua tratando o bypass de ADMIN
 * separadamente, como já fazia antes desta mudança.
 */
@Service
public class LojaAccessService {

    private final LojaRepository lojaRepository;

    public LojaAccessService(LojaRepository lojaRepository) {
        this.lojaRepository = lojaRepository;
    }

    /** Permissões do painel, avaliadas sobre o usuário atual carregado do banco. */
    public void exigirCatalogo(Usuario usuario) {
        exigirCargo(usuario, "gerente", "administrador");
    }

    public void exigirFinanceiro(Usuario usuario) {
        exigirCargo(usuario, "administrador");
    }

    public void exigirDono(Usuario usuario) {
        if (usuario == null || !usuario.isAtivo()
                || (usuario.getPapel() != Papel.LOJISTA && usuario.getPapel() != Papel.ADMIN)) {
            throw new br.com.nhac.backend_nhac.exceptions.AcessoNegadoException(
                    "Apenas o dono da loja pode alterar estas configurações.");
        }
    }

    private void exigirCargo(Usuario usuario, String... cargos) {
        if (usuario != null && usuario.isAtivo()) {
            if (usuario.getPapel() == Papel.LOJISTA || usuario.getPapel() == Papel.ADMIN) return;
            if (usuario.getPapel() == Papel.FUNCIONARIO && usuario.getLojaVinculadaId() != null) {
                String cargo = usuario.getCargo() == null ? "" : usuario.getCargo().trim().toLowerCase(java.util.Locale.ROOT);
                if (java.util.Arrays.asList(cargos).contains(cargo)) return;
            }
        }
        throw new br.com.nhac.backend_nhac.exceptions.AcessoNegadoException(
                "Seu cargo não permite acessar esta área da loja.");
    }

    /**
     * Retorna a loja que o usuário logado (dono ou funcionário) pode gerenciar.
     * Lança LojaNaoEncontradaException se ele não tiver loja associada de
     * nenhuma das duas formas (ex.: CLIENTE, ou FUNCIONARIO órfão).
     */
    public Loja obterLojaAcessivel(Usuario usuarioLogado) {
        if (usuarioLogado.getPapel() == Papel.FUNCIONARIO) {
            if (usuarioLogado.getLojaVinculadaId() == null) {
                throw new LojaNaoEncontradaException();
            }
            return lojaRepository.findById(usuarioLogado.getLojaVinculadaId())
                    .orElseThrow(LojaNaoEncontradaException::new);
        }

        return lojaRepository.findByUsuarioId(usuarioLogado.getId())
                .orElseThrow(LojaNaoEncontradaException::new);
    }

    /**
     * Confere se o usuário logado (dono, funcionário, ou ADMIN) tem acesso à
     * loja informada. Uso típico: checagem de ownership em atualizarProduto,
     * desativarProduto, atualizarStatus de pedido, etc.
     */
   public boolean temAcessoALoja(Usuario usuarioLogado, String lojaId) {


    if (usuarioLogado.getPapel() == Papel.ADMIN) return true;
    try {
        Loja lojaAcessivel = obterLojaAcessivel(usuarioLogado);
        return lojaAcessivel.getId().equals(lojaId);
    } catch (LojaNaoEncontradaException e) {
        return false;
    }
}
}
