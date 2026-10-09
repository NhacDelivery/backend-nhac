package br.com.nhac.backend_nhac.infra.security;

import br.com.nhac.backend_nhac.domain.entrega.*;
import br.com.nhac.backend_nhac.domain.entregador.*;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Page;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcControllerTest(controllers = {SuporteEntregaController.class, RepasseEntregadorController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(SuporteAdministrativoSecurityTest.MethodSecurityConfig.class)
class SuporteAdministrativoSecurityTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {}
    @Autowired MockMvc mvc;
    @MockitoBean TokenService tokenService;
    @MockitoBean UsuarioRepository usuarios;
    @MockitoBean SuporteEntregaService suporte;
    @MockitoBean SolicitacaoSuporteRepository tickets;
    @MockitoBean RepasseEntregadorService repasses;
    @MockitoBean RepasseEntregadorRepository registros;
    @MockitoBean PedidoRepository pedidos;
    @MockitoBean EntregadorService entregadores;

    private void verificar(Usuario usuario, int esperado) throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            mvc.perform(get("/api/v1/suporte/entregas")).andExpect(status().is(esperado));
            mvc.perform(put("/api/v1/suporte/entregas/ticket/resposta")
                    .contentType("application/json").content("{\"resposta\":\"Resposta do suporte\"}"))
                    .andExpect(status().is(esperado));
            mvc.perform(put("/api/v1/suporte/repasses/pedido/apuracao")
                    .contentType("application/json").content("{\"valorDevido\":10.00}"))
                    .andExpect(status().is(esperado));
            mvc.perform(put("/api/v1/suporte/repasses/pedido/pagamento")
                    .contentType("application/json").content("{\"referencia\":\"comprovante\",\"valor\":10.00,\"pagoEm\":\"2026-10-09T12:00:00Z\"}"))
                    .andExpect(status().is(esperado));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @Test void bloqueiaPapeisNaoAdministrativos() throws Exception {
        for (Papel papel : List.of(Papel.CLIENTE, Papel.LOJISTA, Papel.FUNCIONARIO, Papel.ENTREGADOR)) {
            verificar(Usuario.builder().id("usuario").papel(papel).build(), 403);
        }
        verifyNoInteractions(tickets, suporte, repasses);
    }
    @Test void bloqueiaAdministradorInativoOuVinculadoALoja() throws Exception {
        verificar(Usuario.builder().id("admin").papel(Papel.ADMIN).ativo(false).build(), 403);
        verificar(Usuario.builder().id("admin").papel(Papel.ADMIN).lojaVinculadaId("loja").build(), 403);
        verifyNoInteractions(tickets, suporte, repasses);
    }
    @Test void permiteAdministradorAtivoGlobal() throws Exception {
        when(tickets.findByStatusOrderByCriadoEmAsc(anyString(), any())).thenReturn(Page.empty());
        verificar(Usuario.builder().id("admin").papel(Papel.ADMIN).build(), 200);
        verify(suporte).responder(eq("ticket"), anyString());
        verify(repasses).apurar(eq("pedido"), any());
        verify(repasses).registrarPagamento(eq("pedido"), anyString(), any(), any());
    }
}
