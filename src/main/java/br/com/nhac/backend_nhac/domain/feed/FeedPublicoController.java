package br.com.nhac.backend_nhac.domain.feed;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
@RestController
public class FeedPublicoController {
    private final FeedPostRepository posts;
    public FeedPublicoController(FeedPostRepository posts) { this.posts=posts; }
    @GetMapping(value="/publicacao/{id}",produces=MediaType.TEXT_HTML_VALUE) @Transactional(readOnly=true)
    public ResponseEntity<String> pagina(@PathVariable String id) {
        var p=posts.buscarComRelacionamentos(id).filter(post -> post.getUsuario().isAtivo());
        if(p.isEmpty()) return ResponseEntity.status(404).body("<h1>Publicação indisponível</h1><p>Esta publicação foi removida ou não está disponível.</p>");
        var post=p.get();
        String autor=HtmlUtils.htmlEscape(post.getUsuario().getNome());
        String texto=HtmlUtils.htmlEscape(post.getConteudo());
        String caminho=org.springframework.web.util.UriUtils.encodePathSegment(id,java.nio.charset.StandardCharsets.UTF_8);
        String foto=post.getImagens().isEmpty() ? "" : "<img alt='Foto da publicação' src='"+HtmlUtils.htmlEscape(post.getImagens().getFirst())+"'>";
        return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; img-src https:; base-uri 'none'; frame-ancestors 'none'")
            .body("<!doctype html><html lang='pt-BR'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Publicação no Nhac</title><style>body{font:16px system-ui;background:#fff0ee;color:#5d201c;margin:0;padding:24px}main{max-width:640px;margin:auto;background:white;padding:24px;border-radius:24px}img{max-width:100%;border-radius:16px}p{white-space:pre-wrap;overflow-wrap:anywhere}a{display:inline-block;padding:14px 20px;border-radius:24px;background:#ff6961;color:white;text-decoration:none;margin:8px 0}</style><main><h1>Nhac</h1><h2>"+autor+"</h2><p>"+texto+"</p>"+foto+"<p><a href='nhac://app/publicacao/"+caminho+"'>Abrir no aplicativo</a></p><p>Você pode ler a publicação nesta página mesmo sem instalar o Nhac. Para comentar e interagir, abra o aplicativo.</p></main></html>");
    }
    @GetMapping("/.well-known/assetlinks.json")
    public java.util.List<?> associacao(@org.springframework.beans.factory.annotation.Value("${nhac.android.sha256-certificates:}") String certificados) {
        var fingerprints=java.util.Arrays.stream(certificados.split(",")).map(String::trim).filter(s -> s.matches("([0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2}")).toList();
        if(fingerprints.isEmpty()) return java.util.List.of();
        return java.util.List.of(java.util.Map.of("relation",java.util.List.of("delegate_permission/common.handle_all_urls"),"target",java.util.Map.of("namespace","android_app","package_name","com.feentzs.nhac","sha256_cert_fingerprints",fingerprints)));
    }
}
