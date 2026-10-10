package br.com.nhac.backend_nhac.domain.usuario;

import java.util.Optional;

/** Cargos reconhecidos pelas permissões, sem alterar os rótulos persistidos. */
public enum Cargo {
    ATENDENTE,
    GERENTE,
    ADMINISTRADOR;

    private static final Cargo[] CARGOS = values();

    public static Optional<Cargo> fromDescricao(String descricao) {
        if (descricao == null) return Optional.empty();
        String normalizada = descricao.trim();
        for (Cargo cargo : CARGOS) {
            if (cargo.name().equalsIgnoreCase(normalizada)) return Optional.of(cargo);
        }
        return Optional.empty();
    }
}
