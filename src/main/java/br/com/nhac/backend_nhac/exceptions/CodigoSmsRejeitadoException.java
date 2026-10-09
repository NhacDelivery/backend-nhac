package br.com.nhac.backend_nhac.exceptions;

/** Falha de SMS cujas tentativas precisam ser persistidas para limitar abuso. */
public class CodigoSmsRejeitadoException extends RegraDeNegocioException {
    public CodigoSmsRejeitadoException(String mensagem) {
        super(mensagem);
    }
}
