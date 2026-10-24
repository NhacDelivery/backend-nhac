package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthFlowIT extends AbstractIntegrationTest {

    @Test
    public void deveRegistrarELogarComSucesso() throws Exception {
        String email = "teste.integracao@nhac.com.br";
        String senha = "senhaForte123";

        String prova = criarCodigoVerificadoPara(email);

        RegistroRequestDTO registroReq = new RegistroRequestDTO(
                UUID.randomUUID().toString(),
                "Usuario Teste",
                email,
                "11999999999",
                senha, prova
        );

        mockMvc.perform(post("/api/v1/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.nome").value("Usuario Teste"))
                .andExpect(jsonPath("$.papel").value("CLIENTE"));

        LoginRequestDTO loginReq = new LoginRequestDTO(email, senha);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.nome").value("Usuario Teste"))
                .andExpect(jsonPath("$.papel").value("CLIENTE"));
    }
}
