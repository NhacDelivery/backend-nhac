package br.com.nhac.backend_nhac.domain.entregador;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
class ValidacaoEntregadorTest {
    @Test void bicicletaDispensaDocumentosDeVeiculo(){
        assertEquals("",ValidacaoEntregador.cnh(null,TipoVeiculo.BICICLETA));
        assertEquals("",ValidacaoEntregador.placa(null,TipoVeiculo.BICICLETA));
        assertThrows(RegraDeNegocioException.class,()->ValidacaoEntregador.cnh(null,TipoVeiculo.MOTO));
        assertThrows(RegraDeNegocioException.class,()->ValidacaoEntregador.placa("ABC1D2-",TipoVeiculo.CARRO));
    }
    @Test void cpfValidaDigitosENormaliza(){
        assertEquals("52998224725",ValidacaoEntregador.cpf("529.982.247-25"));
        assertThrows(RegraDeNegocioException.class,()->ValidacaoEntregador.cpf("52998224726"));
        assertThrows(RegraDeNegocioException.class,()->ValidacaoEntregador.cpf("11111111111"));
    }
    @Test void pixExigeUuidEstruturadoETelefoneInternacional(){
        assertThrows(RegraDeNegocioException.class,()->ValidacaoEntregador.pix("ALEATORIA","a".repeat(36)));
        assertEquals("123e4567-e89b-12d3-a456-426614174000",ValidacaoEntregador.pix("ALEATORIA","123E4567-E89B-12D3-A456-426614174000"));
        assertEquals("+5511999991234",ValidacaoEntregador.pix("CELULAR","(11) 99999-1234"));
        assertEquals("nome+teste_1@exemplo.com",ValidacaoEntregador.pix("EMAIL","nome+teste_1@exemplo.com"));
    }
}
