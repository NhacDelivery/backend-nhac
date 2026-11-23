package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.domain.auth.dto.LoginResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.CredenciaisInvalidasException;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(googleAuthService, "googleClientIds", "client1,client2");
    }

    @Test
    void autenticarComGoogle_QuandoUsuarioJaExiste_GeraTokenERetornaResponse() throws Exception {
        // Arrange
        String mockTokenString = "mocked-valid-token";
        
        Usuario usuarioExistente = new Usuario();
        usuarioExistente.setId("user-id-123");
        usuarioExistente.setEmail("teste@gmail.com");
        usuarioExistente.setNome("Teste da Silva");
        
        when(usuarioRepository.findByEmailIgnoreCase("teste@gmail.com")).thenReturn(Optional.of(usuarioExistente));
        when(tokenService.gerarToken(usuarioExistente)).thenReturn("jwt-super-secreto");

        // Mocking GoogleIdTokenVerifier and GoogleIdToken
        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmailVerified(true);
        mockPayload.setEmail("teste@gmail.com");
        mockPayload.set("name", "Teste da Silva");
        mockPayload.set("picture", "http://imagem.com/pic.jpg");
        when(mockIdToken.getPayload()).thenReturn(mockPayload);

        GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
        when(mockVerifier.verify(mockTokenString)).thenReturn(mockIdToken);

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    when(mock.setAudience(anyList())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                })) {
            
            // Act
            LoginResponseDTO response = googleAuthService.autenticarComGoogle(mockTokenString);

            // Assert
            assertNotNull(response);
            assertEquals("jwt-super-secreto", response.token());
            assertEquals("user-id-123", response.usuarioId());
            assertEquals("Teste da Silva", response.nome());
            assertEquals("CLIENTE", response.papel());
            
            verify(usuarioRepository, never()).save(any()); // Usuario already exists, should not save
        }
    }

    @Test
    void autenticarComGoogle_QuandoUsuarioNaoExiste_CriaUsuarioERetornaResponse() throws Exception {
        // Arrange
        String mockTokenString = "mocked-valid-token-new";
        
        when(usuarioRepository.findByEmailIgnoreCase("novo@gmail.com")).thenReturn(Optional.empty());
        
        Usuario novoUsuarioSalvo = new Usuario();
        novoUsuarioSalvo.setId("new-user-uuid");
        novoUsuarioSalvo.setEmail("novo@gmail.com");
        novoUsuarioSalvo.setNome("Novo Usuario");
        
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(novoUsuarioSalvo);
        when(tokenService.gerarToken(novoUsuarioSalvo)).thenReturn("jwt-novo-usuario");

        GoogleIdToken mockIdToken = mock(GoogleIdToken.class);
        GoogleIdToken.Payload mockPayload = new GoogleIdToken.Payload();
        mockPayload.setEmailVerified(true);
        mockPayload.setEmail("novo@gmail.com");
        mockPayload.set("name", "Novo Usuario");
        mockPayload.set("picture", "http://imagem.com/new.jpg");
        when(mockIdToken.getPayload()).thenReturn(mockPayload);

        GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
        when(mockVerifier.verify(mockTokenString)).thenReturn(mockIdToken);

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    when(mock.setAudience(anyList())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                })) {
            
            // Act
            LoginResponseDTO response = googleAuthService.autenticarComGoogle(mockTokenString);

            // Assert
            assertNotNull(response);
            assertEquals("jwt-novo-usuario", response.token());
            assertEquals("new-user-uuid", response.usuarioId());
            assertEquals("CLIENTE", response.papel());
            verify(usuarioRepository).save(any(Usuario.class)); // Verifies that save was called
        }
    }

    @Test
    void autenticarComGoogle_QuandoTokenInvalido_LancaExcecao() throws Exception {
        // Arrange
        String mockTokenString = "invalid-token";

        GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
        when(mockVerifier.verify(mockTokenString)).thenReturn(null); // Return null means invalid

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    when(mock.setAudience(anyList())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                })) {
            
            // Act & Assert
            CredenciaisInvalidasException exception = assertThrows(CredenciaisInvalidasException.class, () -> {
                googleAuthService.autenticarComGoogle(mockTokenString);
            });
            
            assertTrue(exception.getMessage().contains("Token do Google inválido ou expirado"));
            verify(usuarioRepository, never()).findByEmailIgnoreCase(anyString());
        }
    }

    @Test
    void autenticarComGoogle_QuandoOcorreExcecaoGeral_LancaExcecaoCredenciais() throws Exception {
        // Arrange
        String mockTokenString = "error-token";

        GoogleIdTokenVerifier mockVerifier = mock(GoogleIdTokenVerifier.class);
        when(mockVerifier.verify(mockTokenString)).thenThrow(new IllegalArgumentException("Network error")); 

        try (MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder = mockConstruction(GoogleIdTokenVerifier.Builder.class,
                (mock, context) -> {
                    when(mock.setAudience(anyList())).thenReturn(mock);
                    when(mock.build()).thenReturn(mockVerifier);
                })) {
            
            // Act & Assert
            CredenciaisInvalidasException exception = assertThrows(CredenciaisInvalidasException.class, () -> {
                googleAuthService.autenticarComGoogle(mockTokenString);
            });
            
            assertTrue(exception.getMessage().contains("Erro ao validar token social"));
            verify(usuarioRepository, never()).findByEmailIgnoreCase(anyString());
        }
    }
}
