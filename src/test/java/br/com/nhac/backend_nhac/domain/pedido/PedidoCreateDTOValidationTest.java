package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PedidoCreateDTOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Deve falhar se lojaId for nulo")
    void deveFalharSeLojaIdNulo() {
        PedidoCreateDTO.EnderecoEntregaDTO endereco = new PedidoCreateDTO.EnderecoEntregaDTO("R", "1", "B", "C", "SP", "00000000", null, -23.5, -46.7);
        PedidoCreateDTO.ItemPedidoDTO item = new PedidoCreateDTO.ItemPedidoDTO("1", "nome", "img", 1);
        
        PedidoCreateDTO dto = new PedidoCreateDTO(null, "DINHEIRO", null, null, null, endereco, null, List.of(item));
        Set<ConstraintViolation<PedidoCreateDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Deve falhar se lista de itens for vazia")
    void deveFalharSeItensVazios() {
        PedidoCreateDTO.EnderecoEntregaDTO endereco = new PedidoCreateDTO.EnderecoEntregaDTO("R", "1", "B", "C", "SP", "00000000", null, -23.5, -46.7);
        
        PedidoCreateDTO dto = new PedidoCreateDTO("123", "DINHEIRO", null, null, null, endereco, null, List.of());
        Set<ConstraintViolation<PedidoCreateDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    @DisplayName("Deve falhar se quantidade do item for menor ou igual a zero")
    void deveFalharSeQuantidadeMenorIgualZero() {
        PedidoCreateDTO.ItemPedidoDTO item = new PedidoCreateDTO.ItemPedidoDTO("1", "nome", "img", 0);
        Set<ConstraintViolation<PedidoCreateDTO.ItemPedidoDTO>> violations = validator.validate(item);
        assertFalse(violations.isEmpty());
    }

    @Test
    void coordenadasDaEntregaDevemVirEmParEEntrarNoPedido() {
        for (Double[] ponto : new Double[][]{{null, null}, {0.0, 0.0}, {Double.NaN, -46.7}, {-23.5, Double.POSITIVE_INFINITY}, {91.0, -46.7}}) {
            var invalido = new PedidoCreateDTO.EnderecoEntregaDTO(
                    "Rua", "12", "Centro", "Osasco", "SP", "06000-000", null, ponto[0], ponto[1]);
            assertFalse(validator.validate(invalido).isEmpty());
        }
        var incompleto = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua", "12", "Centro", "Osasco", "SP", "06000-000", null, -23.5, null);
        assertFalse(validator.validate(incompleto).isEmpty());

        var completo = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua", "12", "Centro", "Osasco", "SP", "06000-000", null, -23.5, -46.7);
        assertTrue(validator.validate(completo).isEmpty());
        var dto = new PedidoCreateDTO("loja", "PIX", null, null, null,
                completo, null, List.of(new PedidoCreateDTO.ItemPedidoDTO("produto", "Nome", null, 1)));
        var pedido = dto.toEntity(null);
        assertTrue(pedido.getEntregaLatitude().equals(-23.5));
        assertTrue(pedido.getEntregaLongitude().equals(-46.7));
    }
}
