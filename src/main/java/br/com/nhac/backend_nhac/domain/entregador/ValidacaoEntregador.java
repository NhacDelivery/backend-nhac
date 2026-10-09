package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import java.util.Locale;

public final class ValidacaoEntregador {
    private ValidacaoEntregador() {}
    public static String cpf(String valor) {
        String cpf = valor == null ? "" : valor.replaceAll("[.\\-\\s]", "");
        if (!cpf.matches("\\d{11}") || cpf.matches("(\\d)\\1{10}")) erro("CPF inválido.");
        for (int tamanho = 9; tamanho <= 10; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
            int digito = soma % 11 < 2 ? 0 : 11 - soma % 11;
            if (cpf.charAt(tamanho) - '0' != digito) erro("CPF inválido.");
        }
        return cpf;
    }
    public static String cnh(String valor, TipoVeiculo tipo) {
        if (tipo == TipoVeiculo.BICICLETA) return "";
        String cnh = valor == null ? "" : valor.trim();
        if (!cnh.matches("\\d{11}") || cnh.matches("(\\d)\\1{10}")) erro("A CNH deve conter 11 dígitos válidos.");
        return cnh;
    }
    public static String placa(String valor, TipoVeiculo tipo) {
        if (tipo == TipoVeiculo.BICICLETA) return "";
        String placa = valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT).replace("-", "");
        if (!placa.matches("[A-Z]{3}(?:[0-9]{4}|[0-9][A-Z][0-9]{2})")) erro("Placa inválida.");
        return placa;
    }
    public static String pix(String tipo, String valor) {
        String chave = valor == null ? "" : valor.trim();
        return switch (tipo) {
            case "CPF" -> cpf(chave);
            case "CELULAR" -> {
                String telefone = chave.replaceAll("[()\\-\\s]", "");
                if (telefone.matches("[1-9][0-9]9[0-9]{8}")) telefone = "+55" + telefone;
                if (!telefone.matches("\\+55[1-9][0-9]9[0-9]{8}")) erro("Chave Pix celular inválida. Use +55, DDD e número.");
                yield telefone;
            }
            case "EMAIL" -> {
                if (!chave.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) erro("Chave Pix e-mail inválida.");
                yield chave;
            }
            case "ALEATORIA" -> {
                if (!chave.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) erro("Chave Pix aleatória inválida.");
                yield chave.toLowerCase(Locale.ROOT);
            }
            default -> throw new RegraDeNegocioException("Tipo de chave Pix inválido.");
        };
    }
    private static void erro(String mensagem) { throw new RegraDeNegocioException(mensagem); }
}
