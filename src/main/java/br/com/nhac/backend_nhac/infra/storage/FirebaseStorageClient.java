package br.com.nhac.backend_nhac.infra.storage;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente para upload de arquivos no Firebase Storage (que é, por baixo dos panos, um bucket do
 * Google Cloud Storage). Segue o mesmo padrão de mock-mode já usado em EmailService/TwilioSmsService:
 * O modo mock é explícito para testes; sem credenciais reais, o modo normal falha ao iniciar.
 *
 * IMPORTANTE: esta integração ainda não foi testada contra um bucket real (sem credenciais disponíveis
 * no ambiente em que foi escrita). Antes de usar em produção, configure as credenciais reais, rode
 * localmente com nhac.storage.mock-mode=false e valide um upload de ponta a ponta.
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
public class FirebaseStorageClient {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseStorageClient.class);

    private final StorageProperties properties;

    @Value("${nhac.storage.mock-mode:true}")
    private boolean mockMode;

    private Storage storage;

    public FirebaseStorageClient(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
public void init() {
    if (mockMode) {
        logger.info("Firebase Storage operando em modo MOCK (upload não sobe nada de verdade).");
        return;
    }

    try {
        GoogleCredentials credentials;

        if (properties.getCredentialsBase64() != null && !properties.getCredentialsBase64().isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(properties.getCredentialsBase64());
            credentials = GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
        } else if (properties.getCredentialsPath() != null) {
            credentials = GoogleCredentials.fromStream(properties.getCredentialsPath().getInputStream());
        } else {
            throw new IllegalStateException(
                "Nenhuma credencial do Firebase configurada. Defina firebase.storage.credentials-path (dev) " +
                "ou firebase.storage.credentials-base64 (prod).");
        }

        this.storage = StorageOptions.newBuilder()
                .setCredentials(credentials)
                .build()
                .getService();

        logger.info("Firebase Storage Client inicializado com sucesso para o bucket '{}'.", properties.getBucketName());
    } catch (Exception e) {
        throw new IllegalStateException("Falha ao inicializar o Firebase Storage Client", e);
    }
}

    /**
     * Faz upload dos bytes de um arquivo e devolve uma URL de download no formato do Firebase Storage
     * (https://firebasestorage.googleapis.com/v0/b/{bucket}/o/{caminho}?alt=media&amp;token=...),
     * o mesmo formato que já é salvo em imagemUrl hoje (gerado pelo SDK cliente do Flutter).
     *
     * @param bytes       conteúdo do arquivo
     * @param pasta       pasta dentro do bucket, ex: "lojas" ou "produtos"
     * @param extensao    extensão do arquivo sem o ponto, ex: "jpg", "png", "webp"
     * @param contentType content-type do arquivo, ex: "image/jpeg"
     * @return URL pública de download do arquivo
     */
    public String upload(byte[] bytes, String pasta, String extensao, String contentType) {
        String nomeArquivo = UUID.randomUUID() + "." + extensao;
        String caminhoNoBucket = pasta + "/" + nomeArquivo;
        String downloadToken = UUID.randomUUID().toString();

        if (mockMode) {
            String urlMock = "https://firebasestorage.googleapis.com/v0/b/MOCK-BUCKET/o/"
                    + urlEncode(caminhoNoBucket) + "?alt=media&token=" + downloadToken;
            logger.info("[UPLOAD MOCK] Arquivo '{}' ({} bytes, {}) NÃO foi enviado de verdade. URL de placeholder: {}",
                    caminhoNoBucket, bytes.length, contentType, urlMock);
            return urlMock;
        }

        BlobId blobId = BlobId.of(properties.getBucketName(), caminhoNoBucket);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                // Esse metadado é o que faz o Firebase Storage aceitar a URL de download pública
                // por token, sem precisar tornar o bucket inteiro público.
                .setMetadata(Map.of("firebaseStorageDownloadTokens", downloadToken))
                .build();

        storage.create(blobInfo, bytes);

        return "https://firebasestorage.googleapis.com/v0/b/" + properties.getBucketName() + "/o/"
                + urlEncode(caminhoNoBucket) + "?alt=media&token=" + downloadToken;
    }

    private String urlEncode(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
