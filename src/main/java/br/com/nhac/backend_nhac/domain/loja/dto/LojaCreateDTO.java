package br.com.nhac.backend_nhac.domain.loja.dto;

import br.com.nhac.backend_nhac.domain.loja.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Objeto de transferência para criação ou atualização de uma loja")
public record LojaCreateDTO(

        @NotBlank(message = "O nome da loja é obrigatório.")
        @Size(min = 3, max = 100, message = "O nome da loja deve ter entre 3 e 100 caracteres.")
        @Schema(description = "Nome fantasia da loja", example = "Mercado Central")
        String nome,

        @Size(max = 2000, message = "A descrição não pode passar de 2000 caracteres.")
        @Schema(description = "Descrição detalhada da loja", example = "Filial focada em produtos orgânicos.")
        String descricao,

        @Size(max = 50)
        @Schema(description = "Categoria que a loja se encaixa", example = "Restaurantes")
        String categoria,

        @NotBlank(message = "A url do banner da loja é obrigatória.")
        @Size(max = 500, message = "A url do banner da loja não pode passar de 500 caracteres.")
        @Schema(description = "URL do banner da loja", example = "https://amazonaws.com/photo-1579202673506-ca3ce28943ef.jpg")
        String imagemUrl,

        @NotNull(message = "O status de abertura da loja deve ser informado.")
        @Schema(description = "Indica se a loja está aberta ou fechada no momento", example = "true")
        Boolean isAberto,

        @NotNull(message = "Os dados operacionais são obrigatórios.")
        @Valid
        DadosOperacionaisDTO dadosOperacionais,

        @NotNull(message = "O endereço é obrigatório.")
        @Valid
        EnderecoDTO endereco,

        @NotNull(message = "Os horários de funcionamento são obrigatórios.")
        @Valid
        HorariosDTO horarios,

        @Schema(description = "Formas de pagamento aceitas pela loja (se nulo, usa defaults: todas exceto VA/VR)")
        FormasPagamentoDTO formasPagamento,

        @org.hibernate.validator.constraints.br.CPF(message = "CPF inválido.")
        @jakarta.validation.constraints.Pattern(regexp = "\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}", message = "CPF inválido.")
        @Schema(description = "CPF do responsável, salvo no usuário autenticado apenas na criação. Opcional para clientes antigos.")
        String cpfResponsavel
) {
    public LojaCreateDTO(String nome, String descricao, String categoria, String imagemUrl,
            Boolean isAberto, DadosOperacionaisDTO dadosOperacionais, EnderecoDTO endereco,
            HorariosDTO horarios, FormasPagamentoDTO formasPagamento) {
        this(nome, descricao, categoria, imagemUrl, isAberto, dadosOperacionais, endereco,
                horarios, formasPagamento, null);
    }

    @Schema(description = "Dados de operação logística da loja")
    public record DadosOperacionaisDTO(
            @NotNull(message = "A taxa de entrega base é obrigatória.")
            @Schema(description = "Valor base cobrado pela entrega", example = "5.99")
            BigDecimal taxaEntregaBase,

            @Schema(description = "Tempo mínimo estimado para entrega em minutos", example = "30")
            int tempoEntregaMin,

            @Schema(description = "Tempo máximo estimado para entrega em minutos", example = "45")
            int tempoEntregaMax,

            @Schema(description = "Indica se a loja realiza entrega própria", example = "true")
            Boolean entregaPropria,

            @Schema(description = "Indica se a loja permite retirada no local", example = "false")
            Boolean retiradaNoLocal,

            @Schema(description = "Raio de entrega em quilômetros (null = ilimitado)", example = "10.5")
            BigDecimal raioEntregaKm
    ) {
    }

    @Schema(description = "Endereço físico da loja")
    public record EnderecoDTO(
            @NotBlank(message = "A rua é obrigatória.")
            @Schema(description = "Nome da rua ou avenida", example = "Avenida Paulista")
            String rua,

            @NotBlank(message = "O número é obrigatório.")
            @Schema(description = "Número do estabelecimento", example = "1578")
            String numero,

            @NotBlank(message = "A cidade é obrigatória.")
            @Schema(description = "Cidade onde a loja está localizada", example = "São Paulo")
            String cidade,

            @NotBlank(message = "O estado é obrigatório.")
            @Schema(description = "Sigla do estado", example = "SP")
            String estado,

            @NotBlank(message = "O CEP é obrigatório.")
            @Schema(description = "Código postal no formato XXXXX-XXX", example = "01310-200")
            String cep,

            @NotBlank(message = "O bairro é obrigatório.")
            @Schema(description = "Bairro onde a loja está localizada", example = "Bela Vista")
            String bairro,

            @Schema(description = "Complemento do endereço (opcional)", example = "Sala 42")
            String complemento
    ) {
    }

    @Schema(description = "Horários de funcionamento diários. Usar 'Fechado' para dias sem expediente.")
    public record HorariosDTO(
            @Schema(description = "Horário de Domingo", example = "18:00 - 23:00") String domingo,
            @Schema(description = "Horário de Segunda-feira", example = "Fechado") String segunda,
            @Schema(description = "Horário de Terça-feira", example = "11:00 - 15:00, 18:00 - 23:00") String terca,
            @Schema(description = "Horário de Quarta-feira", example = "11:00 - 15:00, 18:00 - 23:00") String quarta,
            @Schema(description = "Horário de Quinta-feira", example = "11:00 - 15:00, 18:00 - 23:00") String quinta,
            @Schema(description = "Horário de Sexta-feira", example = "11:00 - 23:59") String sexta,
            @Schema(description = "Horário de Sábado", example = "11:00 - 23:59") String sabado
    ) {
    }

    @Schema(description = "Formas de pagamento aceitas pela loja")
    public record FormasPagamentoDTO(
            @Schema(description = "Aceita dinheiro", example = "true") Boolean aceitaDinheiro,
            @Schema(description = "Aceita cartão de crédito", example = "true") Boolean aceitaCredito,
            @Schema(description = "Aceita cartão de débito", example = "true") Boolean aceitaDebito,
            @Schema(description = "Aceita PIX", example = "true") Boolean aceitaPix,
            @Schema(description = "Aceita vale-refeição", example = "false") Boolean aceitaValeRefeicao,
            @Schema(description = "Aceita vale-alimentação", example = "false") Boolean aceitaValeAlimentacao
    ) {
    }


    public Loja toEntity() {
        Loja loja = new Loja();
        loja.setNome(this.nome());
        loja.setDescricao(this.descricao());
        loja.setCategoria(this.categoria());
        loja.setImagemUrl(this.imagemUrl());
        loja.setAberto(this.isAberto());

        DadosOperacionais dados = new DadosOperacionais();
        dados.setTaxaEntregaBase(this.dadosOperacionais().taxaEntregaBase());
        dados.setTempoEntregaMin(this.dadosOperacionais().tempoEntregaMin());
        dados.setTempoEntregaMax(this.dadosOperacionais().tempoEntregaMax());
        dados.setAvaliacaoMedia(0.0f);
        dados.setTotalAvaliacoes(0);
        // B3 - Defaults para retrocompatibilidade
        dados.setEntregaPropria(this.dadosOperacionais().entregaPropria() != null ? this.dadosOperacionais().entregaPropria() : true);
        dados.setRetiradaNoLocal(this.dadosOperacionais().retiradaNoLocal() != null ? this.dadosOperacionais().retiradaNoLocal() : false);
        dados.setRaioEntregaKm(this.dadosOperacionais().raioEntregaKm());
        loja.setDadosOperacionais(dados);

        EnderecoLoja end = new EnderecoLoja();
        end.setRua(this.endereco().rua());
        end.setNumero(this.endereco().numero());
        end.setCidade(this.endereco().cidade());
        end.setEstado(this.endereco().estado());
        end.setCep(this.endereco().cep());
        // B2 - Bairro e complemento
        end.setBairro(this.endereco().bairro());
        end.setComplemento(this.endereco().complemento());
        loja.setEndereco(end);

        HorariosFuncionamento h = new HorariosFuncionamento();
        h.setDomingo(this.horarios().domingo());
        h.setSegunda(this.horarios().segunda());
        h.setTerca(this.horarios().terca());
        h.setQuarta(this.horarios().quarta());
        h.setQuinta(this.horarios().quinta());
        h.setSexta(this.horarios().sexta());
        h.setSabado(this.horarios().sabado());
        loja.setHorariosFuncionamento(h);

        GeoLocalizacao geo = new GeoLocalizacao();
        geo.setGeoLat(0.0);
        geo.setGeoLng(0.0);
        loja.setGeoLocalizacao(geo);

        // B4 - Formas de pagamento (defaults se nulo)
        if (this.formasPagamento() != null) {
            FormasPagamento fp = new FormasPagamento();
            fp.setAceitaDinheiro(this.formasPagamento().aceitaDinheiro() != null ? this.formasPagamento().aceitaDinheiro() : true);
            fp.setAceitaCredito(this.formasPagamento().aceitaCredito() != null ? this.formasPagamento().aceitaCredito() : true);
            fp.setAceitaDebito(this.formasPagamento().aceitaDebito() != null ? this.formasPagamento().aceitaDebito() : true);
            fp.setAceitaPix(this.formasPagamento().aceitaPix() != null ? this.formasPagamento().aceitaPix() : true);
            fp.setAceitaValeRefeicao(this.formasPagamento().aceitaValeRefeicao() != null ? this.formasPagamento().aceitaValeRefeicao() : false);
            fp.setAceitaValeAlimentacao(this.formasPagamento().aceitaValeAlimentacao() != null ? this.formasPagamento().aceitaValeAlimentacao() : false);
            loja.setFormasPagamento(fp);
        }

        return loja;
    }
}
