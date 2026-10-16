package br.com.nhac.backend_nhac.domain.upload;

import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.infra.storage.FirebaseStorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private FirebaseStorageClient storageClient;

    @InjectMocks
    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(uploadService, "tamanhoMaximoEmBytes", 5L * 1024 * 1024);
    }

    @Test
    @DisplayName("Deve lançar erro quando o arquivo estiver vazio")
    void deveLancarErroQuandoArquivoVazio() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "vazio.jpg", "image/jpeg", new byte[0]);

        assertThrows(RegraDeNegocioException.class, () -> uploadService.enviarImagem(arquivo, "produtos"));
    }

    @Test
    @DisplayName("Deve lançar erro quando o content-type não for suportado")
    void deveLancarErroQuandoTipoNaoSuportado() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "documento.pdf", "application/pdf", "conteudo".getBytes());

        RegraDeNegocioException excecao = assertThrows(RegraDeNegocioException.class,
                () -> uploadService.enviarImagem(arquivo, "produtos"));
        assertTrue(excecao.getMessage().contains("JPEG, PNG ou WEBP"));
    }

    @Test
    @DisplayName("Deve lançar erro quando a pasta de destino for inválida")
    void deveLancarErroQuandoPastaInvalida() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo".getBytes());

        assertThrows(RegraDeNegocioException.class, () -> uploadService.enviarImagem(arquivo, "pasta_qualquer"));
    }

    @Test
    @DisplayName("Deve lançar erro quando o arquivo exceder o tamanho máximo")
    void deveLancarErroQuandoArquivoMuitoGrande() {
        ReflectionTestUtils.setField(uploadService, "tamanhoMaximoEmBytes", 10L);
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo-maior-que-10-bytes".getBytes());

        assertThrows(RegraDeNegocioException.class, () -> uploadService.enviarImagem(arquivo, "produtos"));
    }

    @Test
    @DisplayName("Deve delegar para o FirebaseStorageClient quando o arquivo for válido")
    void deveEnviarComSucessoQuandoArquivoValido() throws Exception {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo-da-imagem".getBytes());
        when(storageClient.upload(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn("https://firebasestorage.googleapis.com/v0/b/bucket/o/produtos%2Fabc.jpg?alt=media&token=xyz");

        String url = uploadService.enviarImagem(arquivo, "produtos");

        assertNotNull(url);
        assertTrue(url.startsWith("https://firebasestorage.googleapis.com"));
    }


    @Test
    @DisplayName("Deve aceitar a pasta 'usuarios' e delegar ao storage com essa pasta")
    void deveAceitarPastaUsuarios() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo".getBytes());
        when(storageClient.upload(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn("https://firebasestorage.googleapis.com/v0/b/bucket/o/usuarios%2Fabc.jpg");

        String url = uploadService.enviarImagem(arquivo, "usuarios");

        assertNotNull(url);
        verify(storageClient).upload(any(byte[].class), eq("usuarios"), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve aceitar a pasta 'funcionarios' e delegar ao storage com essa pasta")
    void deveAceitarPastaFuncionarios() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo".getBytes());
        when(storageClient.upload(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn("https://firebasestorage.googleapis.com/v0/b/bucket/o/funcionarios%2Fabc.jpg");

        String url = uploadService.enviarImagem(arquivo, "funcionarios");

        assertNotNull(url);
        verify(storageClient).upload(any(byte[].class), eq("funcionarios"), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve aceitar a pasta 'lojas' e delegar ao storage com essa pasta")
    void deveAceitarPastaLojas() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo".getBytes());
        when(storageClient.upload(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn("https://firebasestorage.googleapis.com/v0/b/bucket/o/lojas%2Fabc.jpg");

        String url = uploadService.enviarImagem(arquivo, "lojas");

        assertNotNull(url);
        verify(storageClient).upload(any(byte[].class), eq("lojas"), anyString(), anyString());
    }

    @Test
    @DisplayName("Deve lançar erro quando a pasta for nula")
    void deveLancarErroQuandoPastaNula() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo".getBytes());

        RegraDeNegocioException excecao = assertThrows(RegraDeNegocioException.class,
                () -> uploadService.enviarImagem(arquivo, null));
        assertTrue(excecao.getMessage().contains("usuarios"));
    }

    @Test
    @DisplayName("Deve lançar erro quando o arquivo for nulo")
    void deveLancarErroQuandoArquivoNulo() {
        assertThrows(RegraDeNegocioException.class, () -> uploadService.enviarImagem(null, "produtos"));
    }

    @Test
    @DisplayName("A mensagem de pasta inválida deve listar todas as pastas válidas")
    void mensagemDeErroDeveListarTodasAsPastas() {
        MockMultipartFile arquivo = new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", "conteudo".getBytes());

        RegraDeNegocioException excecao = assertThrows(RegraDeNegocioException.class,
                () -> uploadService.enviarImagem(arquivo, "invalida"));
        assertTrue(excecao.getMessage().contains("lojas"));
        assertTrue(excecao.getMessage().contains("produtos"));
        assertTrue(excecao.getMessage().contains("usuarios"));
        assertTrue(excecao.getMessage().contains("funcionarios"));
    }
}
