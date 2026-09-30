package br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto;

import org.springframework.data.domain.Page;

public record AvaliacoesEntregadorPageDTO(
        ResumoDTO resumo,
        Page<AvaliacaoEntregadorResumoDTO> avaliacoes
) {
    public record ResumoDTO(double media, long total) {}
}
