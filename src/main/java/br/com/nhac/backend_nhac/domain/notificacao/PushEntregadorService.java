package br.com.nhac.backend_nhac.domain.notificacao;

import br.com.nhac.backend_nhac.domain.entrega.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class PushEntregadorService {
  private final AvisoEntregadorRepository avisos;
  private final UsuarioRepository usuarios;
  private final OfertaEntregaRepository ofertas;
  private final String projectId;
  private final com.google.auth.oauth2.GoogleCredentials auth;
  private final RestClient client;

  public PushEntregadorService(
      AvisoEntregadorRepository avisos,
      UsuarioRepository usuarios,
      OfertaEntregaRepository ofertas,
      @Value("${nhac.push.firebase-project-id:${FIREBASE_PROJECT_ID:}}") String projectId,
      @Value("${nhac.push.credentials-base64:${FIREBASE_PUSH_CREDENTIALS_BASE64:}}")
          String credentials) {
    this.avisos = avisos;
    this.usuarios = usuarios;
    this.ofertas = ofertas;
    this.projectId = projectId;
    this.auth = carregarCredenciais(credentials);
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(4000);
    factory.setReadTimeout(4000);
    client = RestClient.builder().requestFactory(factory).build();
  }

  private static com.google.auth.oauth2.GoogleCredentials carregarCredenciais(String credentials) {
    if (credentials.isBlank()) return null;
    try {
      // Somente credencial explícita; parsing e escopo feitos uma vez, sem handshake no construtor.
      return com.google.auth.oauth2.ServiceAccountCredentials.fromStream(
              new ByteArrayInputStream(Base64.getDecoder().decode(credentials)))
          .createScoped(List.of("https://www.googleapis.com/auth/firebase.messaging"));
    } catch (java.io.IOException | IllegalArgumentException e) {
      throw new IllegalStateException("Credenciais do Firebase Push inválidas.", e);
    }
  }

  public boolean configurado() {
    return !projectId.isBlank() && auth != null;
  }

  @Transactional
  public void enviar(String id) {
    var aviso = avisos.findLockedById(id).orElse(null);
    if (aviso == null || aviso.isPushEnviado() || !configurado()) return;
    var usuario = usuarios.findById(aviso.getUsuarioId()).orElse(null);
    if (usuario == null
        || !usuario.isAtivo()
        || !AvisoEntregadorListener.permitido(usuario, aviso.getTipo())) {
      aviso.setPushEnviado(true);
      return;
    }
    if ("OFERTA".equals(aviso.getTipo())) {
      var oferta = ofertas.findById(aviso.getOfertaId()).orElse(null);
      if (oferta == null
          || oferta.isExpirada()
          || oferta.getStatus() != StatusOferta.PENDENTE
          || oferta.getEntregador().getStatusOperacional()
              != br.com.nhac.backend_nhac.domain.entregador.StatusOperacional.ONLINE) {
        aviso.setPushEnviado(true);
        return;
      }
    }
    if (usuario.getFcmToken() == null || usuario.getFcmToken().isBlank()) {
      aviso.setPushEnviado(true);
      return;
    }
    try {
      auth.refreshIfExpired();
      Map<String, String> data = new HashMap<>();
      data.put("id", id);
      data.put("usuarioId", aviso.getUsuarioId());
      data.put("tipo", aviso.getTipo());
      if (aviso.getPedidoId() != null) data.put("pedidoId", aviso.getPedidoId());
      if (aviso.getLojaId() != null) data.put("lojaId", aviso.getLojaId());
      if (aviso.getLojaNome() != null) data.put("lojaNome", aviso.getLojaNome());
      if (aviso.getOfertaId() != null) data.put("ofertaId", aviso.getOfertaId());
      client
          .post()
          .uri("https://fcm.googleapis.com/v1/projects/{project}/messages:send", projectId)
          .header("Authorization", "Bearer " + auth.getAccessToken().getTokenValue())
          .body(
              Map.of(
                  "message",
                  Map.of(
                      "token",
                      usuario.getFcmToken(),
                      "data",
                      data,
                      "notification",
                      Map.of("title", "Nhac Motoboy", "body", aviso.getTexto()),
                      "android",
                      Map.of("priority", "HIGH", "ttl", "60s", "notification", Map.of("tag", id)))))
          .retrieve()
          .toBodilessEntity();
      aviso.setPushEnviado(true);
    } catch (Exception e) {
      aviso.setTentativas(aviso.getTentativas() + 1);
      aviso.setProximaTentativa(Instant.now().plusSeconds(30L * aviso.getTentativas()));
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Push não confirmado para aviso {} (tentativa {}).", id, aviso.getTentativas());
    }
  }
}
