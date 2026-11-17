package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.EnviarCodigoSmsDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.ValidarCodigoSmsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

public class AuthFlowIT extends AbstractIntegrationTest {
    @Autowired private CodigoVerificacaoRepository codigosSms;

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

    @Test
    void loginSmsContaAsTentativasEPermiteEntrarComNovoCodigo() throws Exception {
        String telefone = "+5511999998888";
        String codigo = solicitarSms(telefone);
        String errado = codigo.equals("000000") ? "111111" : "000000";

        for (int tentativa = 0; tentativa < 3; tentativa++) {
            mockMvc.perform(loginSms(telefone, errado)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(loginSms(telefone, codigo)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Limite de tentativas")));

        String novoCodigo = solicitarSms(telefone);
        mockMvc.perform(loginSms(telefone, novoCodigo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.isNovoUsuario").value(true));
        mockMvc.perform(loginSms(telefone, novoCodigo)).andExpect(status().isBadRequest());

        String codigoRetorno = solicitarSms(telefone);
        mockMvc.perform(loginSms(telefone, codigoRetorno)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isNovoUsuario").value(false));
    }

    private String solicitarSms(String telefone) throws Exception {
        mockMvc.perform(post("/api/v1/verificacao-telefone/enviar-codigo")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EnviarCodigoSmsDTO(telefone))))
                .andExpect(status().isOk());
        return codigosSms.findAll().stream()
                .filter(codigo -> codigo.getTelefone().equals(telefone) && !codigo.isUtilizado())
                .findFirst().orElseThrow().getCodigo();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginSms(
            String telefone, String codigo) throws Exception {
        return post("/api/v1/auth/login-sms").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ValidarCodigoSmsDTO(telefone, codigo, null)));
    }
}
