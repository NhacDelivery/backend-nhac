package br.com.nhac.backend_nhac.domain.notificacao;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.Instant;
@Service
public class AvisoEntregadorListener {
    private final UsuarioRepository usuarios;
    private final AvisoEntregadorRepository avisos;
    public AvisoEntregadorListener(UsuarioRepository usuarios,AvisoEntregadorRepository avisos) { this.usuarios=usuarios; this.avisos=avisos; }
    public static boolean permitido(Usuario u,String tipo) {
        return switch(tipo) { case "OFERTA" -> u.isNotificarNovoPedido(); case "MENSAGEM" -> u.isNotificarMensagens();
            case "AVALIACAO" -> u.isNotificarAvaliacoes(); case "NOVIDADE" -> u.isNotificarNovidades(); default -> true; };
    }
    @TransactionalEventListener
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void registrar(AvisoEntregadorEvent event) {
        Usuario u=usuarios.findById(event.usuarioId()).orElse(null);
        if (u==null || !u.isAtivo() || !permitido(u,event.tipo()) || avisos.existsById(event.id())) return;
        var aviso=new AvisoEntregador(); aviso.setId(event.id()); aviso.setUsuarioId(u.getId());
        aviso.setTipo(event.tipo()); aviso.setTexto(event.texto()); aviso.setPedidoId(event.pedidoId());
        aviso.setLojaId(event.lojaId()); aviso.setLojaNome(event.lojaNome()); aviso.setOfertaId(event.ofertaId());
        aviso.setCriadoEm(Instant.now()); aviso.setProximaTentativa(Instant.now()); avisos.save(aviso);
    }
}
