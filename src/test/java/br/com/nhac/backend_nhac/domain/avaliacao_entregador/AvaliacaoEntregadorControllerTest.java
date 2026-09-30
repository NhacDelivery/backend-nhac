package br.com.nhac.backend_nhac.domain.avaliacao_entregador;

import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorCreateDTO;
import br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacaoEntregadorResponseDTO;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@br.com.nhac.backend_nhac.infra.security.WebMvcControllerTest(controllers = AvaliacaoEntregadorController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AvaliacaoEntregadorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AvaliacaoEntregadorService service;

    @MockitoBean
    private TokenService tokenService; // bypass security if needed

    @MockitoBean
    private br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository usuarioRepository;

    @Test
    @WithMockUser(roles = "CLIENTE")
    void deveValidarNotaNoCreate() throws Exception {
        AvaliacaoEntregadorCreateDTO dto = new AvaliacaoEntregadorCreateDTO(6, "x"); // nota > 5

        mockMvc.perform(post("/api/v1/pedidos/ped-1/avaliacao-entregador")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }
}
