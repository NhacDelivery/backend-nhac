package br.com.nhac.backend_nhac.domain.loja;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.auth.dto.LoginRequestDTO;
import br.com.nhac.backend_nhac.domain.auth.dto.RegistroRequestDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LojaCadastroIT extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LojaRepository lojaRepository;

    @BeforeEach
    void limparDados() {
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void deveRecusarCriacaoDeLojaSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/lojas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("usuario-forjado")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 401 || status == 403);
                });
    }

    @Test
    void deveCriarLojaAutenticadoPromoverPapelEIgnorarUsuarioIdDoCorpo() throws Exception {
        String email = "lojista.fase2@nhac.com.br";
        String senha = "senhaForte123";

        String prova = criarCodigoVerificadoPara(email);

        RegistroRequestDTO registroReq = new RegistroRequestDTO(
                UUID.randomUUID().toString(),
                "Lojista Teste",
                email,
                "11988887777",
                senha, prova
        );

        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.papel").value("CLIENTE"));

        String token = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, senha))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("CLIENTE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jwt = objectMapper.readTree(token).get("token").asText();
        String usuarioId = objectMapper.readTree(token).get("usuarioId").asText();

        mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("usuario-forjado")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Mercado Central"));

        List<Loja> lojas = lojaRepository.findAll();
        assertEquals(1, lojas.size());
        assertEquals(usuarioId, lojas.get(0).getUsuarioId());

        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow();
        assertEquals(Papel.LOJISTA, usuario.getPapel());
        assertEquals(lojas.get(0).getId(), usuario.getLojaVinculadaId());
        assertEquals("ADMINISTRADOR", usuario.getCargo());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, senha))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.papel").value("LOJISTA"));
    }

    @Test
    void deveBloquearSegundaLojaDoMesmoLojista() throws Exception {
        String email = "lojista.segundaloja@nhac.com.br";
        String senha = "senhaForte123";

        String prova = criarCodigoVerificadoPara(email);

        RegistroRequestDTO registroReq = new RegistroRequestDTO(
                UUID.randomUUID().toString(),
                "Lojista Segunda Loja",
                email,
                "11988887776",
                senha, prova
        );

        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

        String token = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, senha))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jwt = objectMapper.readTree(token).get("token").asText();

        // Primeira loja - sucesso
        mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("usuario-forjado")))
                .andExpect(status().isCreated());

        assertEquals(1, lojaRepository.count());

        // Segunda loja - deve falhar com erro de negócio
        mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("usuario-forjado-2")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 422, "Status esperado era 400 ou 422, mas foi " + status);
                })
                .andExpect(jsonPath("$.message").value("Você já possui uma loja cadastrada."));

        // Confirmar que continuou existindo apenas 1 loja
        assertEquals(1, lojaRepository.count());
    }

    @Test
    void deveConsultarMinhaLojaComSucesso() throws Exception {
        String email = "lojista.minhaloja@nhac.com.br";
        String senha = "senhaForte123";

        String prova = criarCodigoVerificadoPara(email);

        RegistroRequestDTO registroReq = new RegistroRequestDTO(
                UUID.randomUUID().toString(),
                "Lojista Minha Loja",
                email,
                "11988887775",
                senha, prova
        );

        mockMvc.perform(post("/api/v1/auth/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

        String token = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, senha))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String jwt = objectMapper.readTree(token).get("token").asText();

        // Antes de criar a loja, deve dar 404
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/lojas/minha-loja")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound());

        // Criar loja
        mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("qualquer")))
                .andExpect(status().isCreated());

        // Agora GET /minha-loja deve retornar 200 com os dados
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/lojas/minha-loja")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Mercado Central"));
    }

    @Test
    void deveAtualizarLojaPeloDonoEBloquearParaOutroUsuario() throws Exception {
        // Criar usuário 1 (dono)
        String email1 = "dono.loja@nhac.com.br";
        String senha = "senhaForte123";
        String prova1 = criarCodigoVerificadoPara(email1);
        RegistroRequestDTO registro1 = new RegistroRequestDTO(
                UUID.randomUUID().toString(), "Dono Loja", email1, "11988887774", senha, prova1
        );
        mockMvc.perform(post("/api/v1/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(registro1))).andExpect(status().isCreated());
        String token1 = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new LoginRequestDTO(email1, senha)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        // Criar usuário 2 (invasor)
        String email2 = "invasor.loja@nhac.com.br";
        String prova2 = criarCodigoVerificadoPara(email2);
        RegistroRequestDTO registro2 = new RegistroRequestDTO(
                UUID.randomUUID().toString(), "Invasor Loja", email2, "11988887773", senha, prova2
        );
        mockMvc.perform(post("/api/v1/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(registro2))).andExpect(status().isCreated());
        String token2 = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new LoginRequestDTO(email2, senha)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();

        // Dono cria loja
        String responseLoja = mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("qualquer")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String lojaId = objectMapper.readTree(responseLoja).get("id").asText();

        // Invasor tenta atualizar a loja -> 403
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/lojas/" + lojaId)
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("qualquer")))
                .andExpect(status().isForbidden());

        // Dono atualiza a loja -> 200
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/lojas/" + lojaId)
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonCriacaoLoja("qualquer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lojaId));
    }

    @Test
    void devePersistirCpfNormalizadoEVinculoNoPerfilComMesmoTokenDoCadastro() throws Exception {
        String jwt = registrarConta("cpf.dono@nhac.com.br", "11977770001");
        Usuario conta = usuarioRepository.findByEmailIgnoreCase("cpf.dono@nhac.com.br").orElseThrow();
        String resposta = mockMvc.perform(post("/api/v1/lojas")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonComCpf("529.982.247-25")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lojaId = objectMapper.readTree(resposta).get("id").asText();
        Usuario salvo = usuarioRepository.findById(conta.getId()).orElseThrow();
        assertEquals("52998224725", salvo.getCpf());
        assertEquals(lojaId, salvo.getLojaVinculadaId());
        assertEquals("ADMINISTRADOR", salvo.getCargo());
        assertEquals(Papel.LOJISTA, salvo.getPapel());
        assertEquals(conta.getId(), lojaRepository.findById(lojaId).orElseThrow().getUsuarioId());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/usuarios/" + conta.getId())
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lojaVinculadaId").value(lojaId))
                .andExpect(jsonPath("$.cargo").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.cpf").doesNotExist());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/lojas/minha-loja")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(lojaId));
    }

    @Test
    void cpfInvalidoNaoCriaLojaNemPromoveConta() throws Exception {
        String jwt = registrarConta("cpf.invalido@nhac.com.br", "11977770002");
        mockMvc.perform(post("/api/v1/lojas").header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonComCpf("11111111111")))
                .andExpect(status().isBadRequest());
        assertEquals(0, lojaRepository.count());
        assertEquals(Papel.CLIENTE, usuarioRepository.findByEmailIgnoreCase("cpf.invalido@nhac.com.br").orElseThrow().getPapel());
    }

    @Test
    void cpfDuplicadoNaoCriaLojaNemAlteraDonoDoDocumento() throws Exception {
        String primeiro = registrarConta("cpf.primeiro@nhac.com.br", "11977770003");
        mockMvc.perform(post("/api/v1/lojas").header("Authorization", "Bearer " + primeiro)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonComCpf("52998224725")))
                .andExpect(status().isCreated());
        String segundo = registrarConta("cpf.segundo@nhac.com.br", "11977770004");
        mockMvc.perform(post("/api/v1/lojas").header("Authorization", "Bearer " + segundo)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonComCpf("529.982.247-25")))
                .andExpect(status().isBadRequest());
        assertEquals(1, lojaRepository.count());
        Usuario recusado = usuarioRepository.findByEmailIgnoreCase("cpf.segundo@nhac.com.br").orElseThrow();
        assertEquals(Papel.CLIENTE, recusado.getPapel());
        org.junit.jupiter.api.Assertions.assertNull(recusado.getLojaVinculadaId());
        org.junit.jupiter.api.Assertions.assertNull(recusado.getCpf());
    }

    @Test
    void funcionarioNaoPodeTrocarSeuVinculoCriandoOutraLoja() throws Exception {
        String jwt = registrarConta("cpf.funcionario@nhac.com.br", "11977770005");
        Usuario funcionario = usuarioRepository.findByEmailIgnoreCase("cpf.funcionario@nhac.com.br").orElseThrow();
        funcionario.setPapel(Papel.FUNCIONARIO);
        funcionario.setCargo("Atendente");
        funcionario.setLojaVinculadaId("loja-original");
        usuarioRepository.save(funcionario);
        mockMvc.perform(post("/api/v1/lojas").header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON).content(jsonComCpf("52998224725")))
                .andExpect(status().isForbidden());
        assertEquals(0, lojaRepository.count());
        assertEquals("loja-original", usuarioRepository.findById(funcionario.getId()).orElseThrow().getLojaVinculadaId());
    }

    private String registrarConta(String email, String telefone) throws Exception {
        String prova = criarCodigoVerificadoPara(email);
        var registro = new RegistroRequestDTO(UUID.randomUUID().toString(), "Responsável Teste", email, telefone, "Teste12345", prova);
        String resposta = mockMvc.perform(post("/api/v1/auth/registrar").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registro)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("token").asText();
    }

    private String jsonComCpf(String cpf) {
        return jsonCriacaoLoja("usuario-forjado").replace("\"nome\":", "\"cpfResponsavel\": \"" + cpf + "\", \"nome\":");
    }

    private String jsonCriacaoLoja(String usuarioIdForjado) {
        return """
                {
                  "nome": "Mercado Central",
                  "descricao": "Orgânicos",
                  "categoria": "Restaurantes",
                  "imagemUrl": "https://example.com/banner.jpg",
                  "isAberto": true,
                  "usuarioId": "%s",
                  "dadosOperacionais": {
                    "taxaEntregaBase": 5.99,
                    "tempoEntregaMin": 30,
                    "tempoEntregaMax": 45,
                    "entregaPropria": true,
                    "retiradaNoLocal": false
                  },
                  "endereco": {
                    "rua": "Avenida Paulista",
                    "numero": "1578",
                    "cidade": "São Paulo",
                    "estado": "SP",
                    "cep": "01310-200",
                    "bairro": "Bela Vista",
                    "complemento": "Sala 42"
                  },
                  "horarios": {
                    "domingo": "18:00-23:00",
                    "segunda": "Fechado",
                    "terca": "11:00-23:00",
                    "quarta": "11:00-23:00",
                    "quinta": "11:00-23:00",
                    "sexta": "11:00-23:59",
                    "sabado": "11:00-23:59"
                  }
                }
                """.formatted(usuarioIdForjado);
    }
}
