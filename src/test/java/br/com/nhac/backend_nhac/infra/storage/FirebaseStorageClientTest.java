package br.com.nhac.backend_nhac.infra.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseStorageClientTest {

    private FirebaseStorageClient client;

    @BeforeEach
    void setUp() {
        client = new FirebaseStorageClient(new StorageProperties());
        ReflectionTestUtils.setField(client, "mockMode", true);
        client.init();
    }

    @Test
    @DisplayName("Em modo mock, deve devolver uma URL no formato do Firebase Storage sem subir nada de verdade")
    void deveDevolverUrlMockSemSubirArquivo() {
        String url = client.upload("conteudo-fake".getBytes(), "produtos", "jpg", "image/jpeg");

        assertNotNull(url);
        assertTrue(url.startsWith("https://firebasestorage.googleapis.com/v0/b/"));
        assertTrue(url.contains("alt=media"));
        assertTrue(url.contains("token="));
    }

    @Test
    void naoDeveFingirUploadQuandoCredenciaisSaoInvalidas() {
        StorageProperties propriedades = new StorageProperties();
        propriedades.setCredentialsBase64(Base64.getEncoder().encodeToString("invalido".getBytes()));
        FirebaseStorageClient real = new FirebaseStorageClient(propriedades);
        ReflectionTestUtils.setField(real, "mockMode", false);
        assertThrows(IllegalStateException.class, real::init);
    }
}
