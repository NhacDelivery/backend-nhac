package br.com.nhac.backend_nhac.domain.feed;

import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import br.com.nhac.backend_nhac.exceptions.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import java.util.*;
import java.time.Instant;

@Service
@Transactional
public class FeedService {
    private final FeedPostRepository posts;
    private final FeedComentarioRepository comentarios;
    private final FeedInteracaoRepository interacoes;
    private final UsuarioRepository usuarios;
    private final LojaRepository lojas;
    private final LojaAccessService lojaAccess;

    public FeedService(FeedPostRepository posts, FeedComentarioRepository comentarios,
            FeedInteracaoRepository interacoes, UsuarioRepository usuarios,
            LojaRepository lojas, LojaAccessService lojaAccess) {
        this.posts = posts; this.comentarios = comentarios; this.interacoes = interacoes;
        this.usuarios = usuarios; this.lojas = lojas; this.lojaAccess = lojaAccess;
    }

    @Transactional(readOnly = true)
    public Page<FeedPostResponseDTO> listar(Usuario usuario, String categoria, int page, int size) {
        boolean promocoes = false;
        Sort sort = Sort.by(Sort.Direction.DESC, "criadoEm", "id");
        switch (categoria) {
            case "Em Alta" -> sort = Sort.by(Sort.Direction.DESC, "curtidas", "comentarios", "criadoEm", "id");
            case "Destaques", "Novidades" -> { }
            case "Promoções" -> promocoes = true;
            default -> throw new RegraDeNegocioException("Categoria de feed inválida.");
        }
        return respostas(posts.listar(promocoes, pagina(page, size, sort)), usuario);
    }

    @Transactional(readOnly = true)
    public Page<FeedPostResponseDTO> listarSalvos(Usuario usuario, int page, int size) {
        return respostas(posts.listarSalvos(usuario.getId(), FeedInteracao.Tipo.SALVO,
                pagina(page, size, Sort.by(Sort.Direction.DESC, "criadoEm", "id"))), usuario);
    }

    @Transactional(readOnly = true)
    public FeedPostResponseDTO buscar(String id, Usuario usuario) {
        FeedPost post = buscarPost(id, false);
        return respostas(new PageImpl<>(List.of(post)), usuario).getContent().getFirst();
    }

    public FeedPostResponseDTO criar(Usuario usuario, FeedPostCreateDTO dto) {
        FeedPost post = new FeedPost();
        post.setId(UUID.randomUUID().toString());
        post.setUsuario(usuarios.findById(usuario.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Usuário não encontrado.")));
        aplicar(post, usuario, dto);
        posts.save(post);
        return resposta(post, Set.of());
    }

    public FeedPostResponseDTO atualizar(String id, Usuario usuario, FeedPostCreateDTO dto) {
        FeedPost post = buscarPost(id, true);
        verificarAutor(post.getUsuario().getId(), usuario);
        aplicar(post, usuario, dto);
        return respostas(new PageImpl<>(List.of(post)), usuario).getContent().getFirst();
    }

    public void remover(String id, Usuario usuario) {
        FeedPost post = buscarPost(id, true);
        verificarAutor(post.getUsuario().getId(), usuario);
        comentarios.deleteByPostId(id);
        interacoes.deleteByPostId(id);
        posts.delete(post);
    }

    public FeedPostResponseDTO interagir(String id, Usuario usuario, FeedInteracao.Tipo tipo, boolean ativo) {
        // Serializa a checagem e a escrita no mesmo post. A constraint única protege
        // também o banco; PUT/DELETE repetidos não alteram a contagem novamente.
        FeedPost post = buscarPost(id, true);
        Optional<FeedInteracao> existente = interacoes.findByPostIdAndUsuarioIdAndTipo(id, usuario.getId(), tipo);
        int delta = 0;
        if (ativo && existente.isEmpty()) {
            FeedInteracao interacao = new FeedInteracao();
            interacao.setId(UUID.randomUUID().toString());
            interacao.setPost(post);
            interacao.setUsuario(usuarios.getReferenceById(usuario.getId()));
            interacao.setTipo(tipo);
            interacoes.save(interacao);
            delta = 1;
        } else if (!ativo && existente.isPresent()) {
            interacoes.delete(existente.get());
            delta = -1;
        }
        if (tipo == FeedInteracao.Tipo.CURTIDA) post.setCurtidas(Math.max(0, post.getCurtidas() + delta));
        else post.setSalvos(Math.max(0, post.getSalvos() + delta));
        return respostas(new PageImpl<>(List.of(post)), usuario).getContent().getFirst();
    }

    @Transactional(readOnly = true)
    public Page<FeedComentarioResponseDTO> listarComentarios(String id, int page, int size) {
        FeedPost post = buscarPost(id, false);
        return comentarios.findByPostIdOrderByCriadoEmAscIdAsc(id, pagina(page, size, Sort.unsorted()))
                .map(c -> respostaComentario(c, post));
    }

    public FeedComentarioResponseDTO comentar(String id, Usuario usuario, FeedComentarioCreateDTO dto) {
        FeedPost post = buscarPost(id, true);
        FeedComentario comentario = new FeedComentario();
        comentario.setId(UUID.randomUUID().toString());
        comentario.setPost(post);
        comentario.setUsuario(usuarios.getReferenceById(usuario.getId()));
        comentario.setConteudo(dto.conteudo().trim());
        comentarios.save(comentario);
        post.setComentarios(post.getComentarios() + 1);
        return respostaComentario(comentario, post);
    }

    public void removerComentario(String postId, String comentarioId, Usuario usuario) {
        FeedPost post = buscarPost(postId, true);
        FeedComentario comentario = comentarios.findByIdAndPostId(comentarioId, postId)
                .orElseThrow(() -> new IdNaoEncontradoException("Comentário não encontrado."));
        if (!post.getUsuario().getId().equals(usuario.getId()))
            verificarAutor(comentario.getUsuario().getId(), usuario);
        comentarios.delete(comentario);
        post.setComentarios(Math.max(0, post.getComentarios() - 1));
    }

    private void aplicar(FeedPost post, Usuario usuario, FeedPostCreateDTO dto) {
        Loja loja = dto.lojaId() == null ? null : lojas.findById(dto.lojaId())
                .orElseThrow(() -> new IdNaoEncontradoException("Loja não encontrada."));
        if (dto.isPatrocinado()) {
            boolean admin = usuario.getPapel() == Papel.ADMIN;
            boolean gestor = usuario.getPapel() == Papel.LOJISTA || usuario.getPapel() == Papel.FUNCIONARIO;
            if (!admin && !(gestor && loja != null && lojaAccess.temAcessoALoja(usuario, loja.getId())))
                throw new AcessoNegadoException("Apenas a equipe da loja ou administradores podem publicar promoções.");
            if (loja == null) throw new RegraDeNegocioException("Informe a loja da promoção.");
        } else if (dto.sponsorLabel() != null && !dto.sponsorLabel().isBlank()) {
            throw new RegraDeNegocioException("O rótulo de promoção exige um post patrocinado.");
        }
        post.setConteudo(dto.conteudo().trim());
        post.getImagens().clear();
        if (dto.imagens() != null) post.getImagens().addAll(dto.imagens());
        post.getHashTags().clear();
        if (dto.hashTags() != null) post.getHashTags().addAll(new LinkedHashSet<>(dto.hashTags()));
        post.setLoja(loja);
        post.setPatrocinado(dto.isPatrocinado());
        post.setSponsorLabel(dto.isPatrocinado() ? dto.sponsorLabel() : null);
        post.setAtualizadoEm(Instant.now());
    }

    private FeedPost buscarPost(String id, boolean bloquear) {
        FeedPost post = (bloquear ? posts.buscarComBloqueio(id) : posts.buscarComRelacionamentos(id))
                .orElseThrow(() -> new IdNaoEncontradoException("Post não encontrado."));
        if (!post.getUsuario().isAtivo()) throw new IdNaoEncontradoException("Post não encontrado.");
        return post;
    }

    private void verificarAutor(String autorId, Usuario usuario) {
        if (!autorId.equals(usuario.getId()) && usuario.getPapel() != Papel.ADMIN)
            throw new AcessoNegadoException("Você não pode alterar este conteúdo.");
    }

    private Pageable pagina(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 50)
            throw new RegraDeNegocioException("Use page >= 0 e size entre 1 e 50.");
        return PageRequest.of(page, size, sort);
    }

    private Page<FeedPostResponseDTO> respostas(Page<FeedPost> pagina, Usuario usuario) {
        List<String> ids = pagina.getContent().stream().map(FeedPost::getId).toList();
        Map<String, Set<FeedInteracao.Tipo>> estados = new HashMap<>();
        if (!ids.isEmpty()) {
            for (FeedInteracao i : interacoes.findByUsuarioIdAndPostIdIn(usuario.getId(), ids))
                estados.computeIfAbsent(i.getPost().getId(), k -> EnumSet.noneOf(FeedInteracao.Tipo.class)).add(i.getTipo());
        }
        return pagina.map(p -> resposta(p, estados.getOrDefault(p.getId(), Set.of())));
    }

    private FeedPostResponseDTO resposta(FeedPost p, Set<FeedInteracao.Tipo> estados) {
        var loja = p.getLoja();
        var dados = loja == null ? null : loja.getDadosOperacionais();
        var mentioned = loja == null ? null : new FeedPostResponseDTO.MentionedStoreDTO(loja.getId(), loja.getNome(),
                loja.getImagemUrl(), dados == null ? 0 : dados.getAvaliacaoMedia(),
                (dados == null ? 0 : dados.getTotalAvaliacoes()) + " avaliações");
        return new FeedPostResponseDTO(p.getId(), p.getUsuario().getId(), p.getUsuario().getNome(),
                p.getUsuario().getImagemUrl(), p.getConteudo(), List.copyOf(p.getImagens()), List.copyOf(p.getHashTags()),
                p.getCurtidas(), p.getComentarios(), p.getSalvos(), estados.contains(FeedInteracao.Tipo.CURTIDA),
                estados.contains(FeedInteracao.Tipo.SALVO), p.isPatrocinado(), p.getSponsorLabel(), mentioned,
                p.getCriadoEm(), p.getAtualizadoEm());
    }

    private FeedComentarioResponseDTO respostaComentario(FeedComentario c, FeedPost p) {
        return new FeedComentarioResponseDTO(c.getId(), c.getUsuario().getId(), c.getUsuario().getNome(),
                c.getUsuario().getImagemUrl(), c.getConteudo(), c.getUsuario().getId().equals(p.getUsuario().getId()), c.getCriadoEm());
    }
}
