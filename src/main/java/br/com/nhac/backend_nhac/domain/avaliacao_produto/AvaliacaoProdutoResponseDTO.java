package br.com.nhac.backend_nhac.domain.avaliacao_produto;
import java.time.Instant;
import java.util.List;
public record AvaliacaoProdutoResponseDTO(String id, String produtoId, String nomeUsuario, Integer nota,
    String comentario, Instant dataCriacao, List<String> imagens) {
    public AvaliacaoProdutoResponseDTO(AvaliacaoProduto a) {
        this(a.getId(), a.getProduto().getId(), a.getUsuario().getNome(), a.getNota(), a.getComentario(), a.getCriadoEm(), List.copyOf(a.getImagens()));
    }
}
