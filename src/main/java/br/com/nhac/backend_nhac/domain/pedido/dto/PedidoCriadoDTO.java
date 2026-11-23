package br.com.nhac.backend_nhac.domain.pedido.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PedidoCriadoDTO(
        @Schema(description = "ID do pedido recém-criado", example = "a1b2c3d4-e5f6-...")
        String pedidoId,

        @Schema(description = "Client secret do Stripe (necessário para confirmar pagamento no frontend)", example = "pi_123_secret_456")
        String clientSecret,

        @Schema(description = "Código Copia e Cola do PIX para pagamento (será null para pagamentos com cartão/Google Pay)", example = "00020101021126580014br.gov.bcb.pix...")
        String pixCopiaECola,

        @Schema(description = "URL da imagem ou payload do QR Code do PIX (será null para pagamentos com cartão/Google Pay)", example = "https://qr.stripe.com/test_123")
        String qrCodeUrl,
        java.math.BigDecimal valorTotal,
        java.time.Instant expiraEm,
        boolean simulacaoDisponivel
) {
    public PedidoCriadoDTO(String pedidoId, String clientSecret, String pixCopiaECola, String qrCodeUrl) {
        this(pedidoId, clientSecret, pixCopiaECola, qrCodeUrl, null, null, false);
    }
}
