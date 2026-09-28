package br.com.nhac.backend_nhac.infra.push;

import br.com.nhac.backend_nhac.infra.storage.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FcmSender {
    private final StorageProperties storageProperties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final boolean mockMode;
    private GoogleCredentials credentials;
    private String projectId;

    public FcmSender(StorageProperties storageProperties,
            @Value("${nhac.storage.mock-mode:false}") boolean mockMode) {
        this.storageProperties = storageProperties;
        this.mockMode = mockMode;
    }

    /** Retorna false quando o FCM informa que o dispositivo deixou de existir. */
    public boolean enviar(String token, String pedidoId, String status, String titulo, String corpo) throws Exception {
        if (mockMode) return true;
        inicializarSeNecessario();
        synchronized (this) { credentials.refreshIfExpired(); }
        var mensagem = Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", titulo, "body", corpo),
                "data", Map.of("pedidoId", pedidoId, "status", status),
                "android", Map.of("priority", "HIGH", "notification", Map.of(
                        "channel_id", "nhac_high_importance_channel"))));
        var request = HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"
                        + projectId + "/messages:send"))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mensagem)))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 && response.body().contains("UNREGISTERED")) return false;
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("FCM recusou envio com HTTP " + response.statusCode());
        }
        return true;
    }

    private synchronized void inicializarSeNecessario() throws Exception {
        if (credentials != null) return;
        GoogleCredentials conta;
        if (storageProperties.getCredentialsBase64() != null
                && !storageProperties.getCredentialsBase64().isBlank()) {
            conta = GoogleCredentials.fromStream(new ByteArrayInputStream(
                    Base64.getDecoder().decode(storageProperties.getCredentialsBase64())));
        } else if (storageProperties.getCredentialsPath() != null) {
            conta = GoogleCredentials.fromStream(storageProperties.getCredentialsPath().getInputStream());
        } else {
            throw new IllegalStateException("Credenciais do Firebase não configuradas para o FCM");
        }
        if (!(conta instanceof ServiceAccountCredentials serviceAccount)) {
            throw new IllegalStateException("O FCM precisa de credenciais de service account");
        }
        projectId = serviceAccount.getProjectId();
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("project_id ausente nas credenciais do Firebase");
        }
        credentials = conta.createScoped("https://www.googleapis.com/auth/firebase.messaging");
    }
}
