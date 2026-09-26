package br.com.nhac.backend_nhac.domain.loja.dto;

import java.math.BigDecimal;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Objeto completo com todos os dados e horários de uma loja específica")
public record LojaDetalhesDTO(

        @Schema(description = "Identificador único da loja", example = "loja_japonesa_001")
        String id,

        @Schema(description = "Nome fantasia da loja", example = "Sushi Ken")
        String nome,

        @Schema(description = "Descrição detalhada da loja", example = "O melhor sushi da região, com ingredientes frescos e selecionados.")
        String descricao,

        @Schema(description = "Categoria principal", example = "Japonesa")
        String categoria,

        @Schema(description = "URL do banner da loja no Firebase Storage", example = "https://firebasestorage.../banner.png")
        String imagemUrl,

        @Schema(description = "Indica se a loja está aberta para receber pedidos agora", example = "true")
        boolean isAberto,

        @Schema(description = "Métricas detalhadas de logística e avaliação")
        DadosOperacionaisDTO dadosOperacionais,

        @Schema(description = "Endereço físico completo da loja")
        EnderecoDTO endereco,

        @Schema(description = "Grade completa de horários de funcionamento")
        HorariosDTO horarios,

        @Schema(description = "Formas de pagamento aceitas pela loja")
        FormasPagamentoDTO formasPagamento,
        Double latitude,
        Double longitude
) {
    @Schema(description = "Dados operacionais")
    public record DadosOperacionaisDTO(
            @Schema(description = "Média de avaliações", example = "4.8") float avaliacaoMedia,
            @Schema(description = "Valor base de entrega", example = "5.99") BigDecimal taxaEntregaBase,
            @Schema(description = "Tempo mínimo (min)", example = "30") int tempoEntregaMin,
            @Schema(description = "Tempo máximo (min)", example = "45") int tempoEntregaMax,
            @Schema(description = "Total de avaliações", example = "150") int totalAvaliacoes,
            @Schema(description = "Indica se a loja realiza entrega própria", example = "true") Boolean entregaPropria,
            @Schema(description = "Indica se a loja permite retirada no local", example = "false") Boolean retiradaNoLocal,
            @Schema(description = "Raio de entrega em quilômetros (null = ilimitado)", example = "10.5") BigDecimal raioEntregaKm
    ) {}

    @Schema(description = "Endereço físico")
    public record EnderecoDTO(
            @Schema(description = "Rua", example = "Rua das Flores") String rua,
            @Schema(description = "Número", example = "123") String numero,
            @Schema(description = "Cidade", example = "São Paulo") String cidade,
            @Schema(description = "Estado", example = "SP") String estado,
            @Schema(description = "Código Postal", example = "01000-000") String cep,
            @Schema(description = "Bairro", example = "Centro") String bairro,
            @Schema(description = "Complemento (opcional)", example = "Sala 42") String complemento
    ) {}

    @Schema(description = "Horários diários (Formato recomendado: HH:MM - HH:MM ou 'Fechado')")
    public record HorariosDTO(
            @Schema(example = "18:00 - 23:00") String domingo,
            @Schema(example = "Fechado") String segunda,
            @Schema(example = "11:00 - 15:00, 18:00 - 23:00") String terca,
            @Schema(example = "11:00 - 15:00, 18:00 - 23:00") String quarta,
            @Schema(example = "11:00 - 15:00, 18:00 - 23:00") String quinta,
            @Schema(example = "11:00 - 23:59") String sexta,
            @Schema(example = "11:00 - 23:59") String sabado
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

    public LojaDetalhesDTO(Loja loja) {
        this(
                loja.getId(),
                loja.getNome(),
                loja.getDescricao(),
                loja.getCategoria(),
                loja.getImagemUrl(),
                loja.isAberto(),
                mapearDadosOperacionais(loja),
                mapearEndereco(loja),
                mapearHorarios(loja),
                mapearFormasPagamento(loja),
                loja.getGeoLocalizacao() == null ? null : loja.getGeoLocalizacao().getGeoLat(),
                loja.getGeoLocalizacao() == null ? null : loja.getGeoLocalizacao().getGeoLng()
        );
    }

    // ================================================================
    // Mapeadores null-safe. Loja pode existir com apenas parte dos
    // embedded preenchidos (ex.: loja recém-criada via service, ou
    // loja antiga que veio de migration sem os campos novos). Antes
    // disso, acessar getHorariosFuncionamento().getDomingo() dava NPE
    // quando a loja vinha sem horários.
    // ================================================================

    private static DadosOperacionaisDTO mapearDadosOperacionais(Loja loja) {
        var d = loja.getDadosOperacionais();
        if (d == null) return null;
        return new DadosOperacionaisDTO(
                d.getAvaliacaoMedia(),
                d.getTaxaEntregaBase(),
                d.getTempoEntregaMin(),
                d.getTempoEntregaMax(),
                d.getTotalAvaliacoes(),
                d.getEntregaPropria(),
                d.getRetiradaNoLocal(),
                d.getRaioEntregaKm()
        );
    }

    private static EnderecoDTO mapearEndereco(Loja loja) {
        var e = loja.getEndereco();
        if (e == null) return null;
        return new EnderecoDTO(
                e.getRua(),
                e.getNumero(),
                e.getCidade(),
                e.getEstado(),
                e.getCep(),
                e.getBairro(),
                e.getComplemento()
        );
    }

    private static HorariosDTO mapearHorarios(Loja loja) {
        var h = loja.getHorariosFuncionamento();
        if (h == null) return null;
        return new HorariosDTO(
                h.getDomingo(),
                h.getSegunda(),
                h.getTerca(),
                h.getQuarta(),
                h.getQuinta(),
                h.getSexta(),
                h.getSabado()
        );
    }

    private static FormasPagamentoDTO mapearFormasPagamento(Loja loja) {
        var f = loja.getFormasPagamento();
        if (f == null) return null;
        return new FormasPagamentoDTO(
                f.getAceitaDinheiro(),
                f.getAceitaCredito(),
                f.getAceitaDebito(),
                f.getAceitaPix(),
                f.getAceitaValeRefeicao(),
                f.getAceitaValeAlimentacao()
        );
    }
}
