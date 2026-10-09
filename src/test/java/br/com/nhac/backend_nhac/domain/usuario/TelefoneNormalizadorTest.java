package br.com.nhac.backend_nhac.domain.usuario;
import org.junit.jupiter.api.Test;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import static org.junit.jupiter.api.Assertions.*;
class TelefoneNormalizadorTest {
    @Test void converteFormatosBrasileirosParaMesmoIdentificador() {
        for (String value : new String[]{"(11) 99999-1234", "11999991234", "5511999991234", "+5511999991234"})
            assertEquals("+5511999991234", TelefoneNormalizador.normalizar(value));
    }
    @Test void preservaInternacionalEContaSemTelefone() {
        assertEquals("+14155552671", TelefoneNormalizador.normalizar("+1 (415) 555-2671"));
        assertNull(TelefoneNormalizador.normalizar(null));
        assertThrows(RegraDeNegocioException.class, () -> TelefoneNormalizador.normalizar("00000000000"));
    }
}
