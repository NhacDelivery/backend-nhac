package br.com.nhac.backend_nhac.domain.feed;

import java.util.List;

public record FeedPerfilResponseDTO(
        String id,
        String nome,
        String avatarUrl,
        String bannerUrl,
        boolean podeEnviarMensagem,
        List<FeedPostResponseDTO> publicacoes,
        boolean temMais
) {}
