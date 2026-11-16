package br.com.nhac.backend_nhac.domain.loja.dto;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Objeto simplificado devolvido na listagem de lojas para otimização de rede")
public record LojaResumoDTO(

        @Schema(description = "Identificador único da loja", example = "loja_japonesa_001")
        String id,

        @Schema(description = "Nome fantasia da loja", example = "Sushi Ken")
        String nome,

        @Schema(description = "Breve descrição ou slogan da loja", example = "O melhor sushi da região.")
        String descricao,

        @Schema(description = "Categoria principal para filtros", example = "Japonesa")
        String categoria,

        @Schema(description = "URL da imagem de perfil da loja", example = "https://example.com/logo.jpg")
        String imagemUrl,

        @Schema(description = "URL opcional da capa exibida na página pública da loja")
        String bannerUrl,

        @Schema(description = "Métricas principais da loja para exibição no card")
        DadosOperacionaisDTO dadosOperacionais,

        @Schema(description = "Formas de pagamento aceitas pela loja")
        FormasPagamentoDTO formasPagamento
) {
    @Schema(description = "Dados de logística e avaliação resumidos")
    public record DadosOperacionaisDTO(
            @Schema(description = "Média das avaliações (0 a 5)", example = "4.8")
            float avaliacaoMedia,

            @Schema(description = "Taxa de entrega base", example = "5.99")
            BigDecimal taxaEntregaBase,

            @Schema(description = "Tempo mínimo estimado em minutos", example = "30")
            int tempoEntregaMin,

            @Schema(description = "Tempo máximo estimado em minutos", example = "45")
            int tempoEntregaMax,

            @Schema(description = "Quantidade total de avaliações recebidas", example = "150")
            int totalAvaliacoes,

            @Schema(description = "Indica se a loja realiza entrega própria", example = "true")
            Boolean entregaPropria,

            @Schema(description = "Indica se a loja permite retirada no local", example = "false")
            Boolean retiradaNoLocal,

            @Schema(description = "Raio de entrega em quilômetros (null = ilimitado)", example = "10.5")
            BigDecimal raioEntregaKm
    ) {}

    @Schema(description = "Formas de pagamento aceitas pela loja")
    public record FormasPagamentoDTO(
            @Schema(description = "Aceita dinheiro", example = "true") Boolean aceitaDinheiro,
            @Schema(description = "Aceita cartão de crédito", example = "true") Boolean aceitaCredito,
            @Schema(description = "Aceita cartão de débito", example = "true") Boolean aceitaDebito,
            @Schema(description = "Aceita PIX", example = "true") Boolean aceitaPix,
            @Schema(description = "Aceita vale-refeição", example = "false") Boolean aceitaValeRefeicao,
            @Schema(description = "Aceita vale-alimentação", example = "false") Boolean aceitaValeAlimentacao
    ) {}

    public LojaResumoDTO(Loja loja){
        this( loja.getId(),
                loja.getNome(),
                loja.getDescricao(),
                loja.getCategoria(),
                loja.getImagemUrl(),
                loja.getBannerUrl(),
                new LojaResumoDTO.DadosOperacionaisDTO(
                        loja.getDadosOperacionais().getAvaliacaoMedia(),
                        loja.getDadosOperacionais().getTaxaEntregaBase(),
                        loja.getDadosOperacionais().getTempoEntregaMin(),
                        loja.getDadosOperacionais().getTempoEntregaMax(),
                        loja.getDadosOperacionais().getTotalAvaliacoes(),
                        loja.getDadosOperacionais().getEntregaPropria(),
                        loja.getDadosOperacionais().getRetiradaNoLocal(),
                        loja.getDadosOperacionais().getRaioEntregaKm()
                ),
                new LojaResumoDTO.FormasPagamentoDTO(
                        loja.getFormasPagamento().getAceitaDinheiro(),
                        loja.getFormasPagamento().getAceitaCredito(),
                        loja.getFormasPagamento().getAceitaDebito(),
                        loja.getFormasPagamento().getAceitaPix(),
                        loja.getFormasPagamento().getAceitaValeRefeicao(),
                        loja.getFormasPagamento().getAceitaValeAlimentacao()
                ));
    }
}