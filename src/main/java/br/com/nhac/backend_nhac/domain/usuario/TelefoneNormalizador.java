package br.com.nhac.backend_nhac.domain.usuario;

import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;

public final class TelefoneNormalizador {
    private TelefoneNormalizador() {}
    public static String normalizar(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.matches("0+")) throw new RegraDeNegocioException("Informe um telefone válido.");
        if (value.trim().startsWith("+") && digits.length() >= 8 && digits.length() <= 15 && digits.charAt(0) != '0') return "+" + digits;
        if (digits.length() == 10 || digits.length() == 11) return "+55" + digits;
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) return "+" + digits;
        throw new RegraDeNegocioException("Informe o telefone com DDD ou no formato internacional.");
    }
}
