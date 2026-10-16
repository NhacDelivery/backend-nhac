package br.com.nhac.backend_nhac.domain.loja;

import java.util.Set;

import org.springframework.stereotype.Service;

import br.com.nhac.backend_nhac.domain.usuario.Cargo;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException;

/** Resolve a loja acessível ao dono ou funcionário vinculado. ADMIN é tratado pelos services. */
@Service
public class LojaAccessService {

    private static final Set<Cargo> CARGOS_CATALOGO = Set.of(Cargo.GERENTE, Cargo.ADMINISTRADOR);
    private static final Set<Cargo> CARGOS_FINANCEIRO = Set.of(Cargo.ADMINISTRADOR);

    private final LojaRepository lojaRepository;

    public LojaAccessService(LojaRepository lojaRepository) {
        this.lojaRepository = lojaRepository;
    }

    /** Permissões do painel, avaliadas sobre o usuário atual carregado do banco. */
    public void exigirCatalogo(Usuario usuario) {
        exigirCargo(usuario, CARGOS_CATALOGO);
    }

    public void exigirFinanceiro(Usuario usuario) {
        exigirCargo(usuario, CARGOS_FINANCEIRO);
    }

    /** Valida apenas o papel; o acesso à loja deve ser conferido separadamente. */
    public void exigirPapelDonoOuAdmin(Usuario usuario) {
        if (usuario == null || !usuario.isAtivo()
                || (usuario.getPapel() != Papel.LOJISTA && usuario.getPapel() != Papel.ADMIN)) {
            throw new AcessoNegadoException(
                    "Apenas o dono da loja pode alterar estas configurações.");
        }
    }

    private void exigirCargo(Usuario usuario, Set<Cargo> cargos) {
        if (usuario != null && usuario.isAtivo()) {
            if (usuario.getPapel() == Papel.LOJISTA || usuario.getPapel() == Papel.ADMIN) return;
            if (usuario.getPapel() == Papel.FUNCIONARIO && usuario.getLojaVinculadaId() != null) {
                if (Cargo.fromDescricao(usuario.getCargo()).filter(cargos::contains).isPresent()) return;
            }
        }
        throw new AcessoNegadoException(
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
