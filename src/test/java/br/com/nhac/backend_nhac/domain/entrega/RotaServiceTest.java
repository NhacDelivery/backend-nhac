package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entrega.dto.PontoCoordenadaDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.RotaEntregaResponseDTO;
import br.com.nhac.backend_nhac.domain.loja.GeoLocalizacao;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RotaServiceTest {

    private final org.springframework.web.client.RestClient.Builder builder = org.springframework.web.client.RestClient.builder();
    private final org.springframework.test.web.client.MockRestServiceServer server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
    private final RotaService rotaService = new RotaService(builder.build());

    @Test
    @DisplayName("Deve codificar e decodificar Polyline corretamente")
    void deveCodificarEDecodificarPolyline() {
        List<PontoCoordenadaDTO> pontosOriginais = List.of(
                new PontoCoordenadaDTO(-23.55052, -46.63330),
                new PontoCoordenadaDTO(-23.56000, -46.64000)
        );

        String polyline = RotaService.codificarPolyline(pontosOriginais);
        assertNotNull(polyline);
        assertFalse(polyline.isBlank());

        List<PontoCoordenadaDTO> pontosDecodificados = RotaService.decodificarPolyline(polyline);
        assertEquals(2, pontosDecodificados.size());
        assertEquals(-23.55052, pontosDecodificados.get(0).latitude(), 0.0001);
        assertEquals(-46.63330, pontosDecodificados.get(0).longitude(), 0.0001);
        assertEquals(-23.56000, pontosDecodificados.get(1).latitude(), 0.0001);
        assertEquals(-46.64000, pontosDecodificados.get(1).longitude(), 0.0001);
    }

    @Test
    @DisplayName("Deve devolver a distância e geometria recebidas do serviço de rotas")
    void deveCalcularRotaComFallback() {
        Loja loja = new Loja();
        loja.setNome("Hamburgueria Nhac");
        loja.setGeoLocalizacao(new GeoLocalizacao(-23.55052, -46.63330, "6fz2h3k"));

        Pedido pedido = new Pedido();
        pedido.setId("ped_rota_1");
        pedido.setLoja(loja);
        pedido.setEntregaLatitude(-23.56000);
        pedido.setEntregaLongitude(-46.64000);

        String geometry = RotaService.codificarPolyline(List.of(
                new PontoCoordenadaDTO(-23.55052, -46.63330), new PontoCoordenadaDTO(-23.56000, -46.64000)));
        String response = new com.google.gson.Gson().toJson(java.util.Map.of("code", "Ok", "routes", List.of(
                java.util.Map.of("distance", 1234, "duration", 420, "geometry", geometry))));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                "https://router.project-osrm.org/route/v1/driving/-46.633300,-23.550520;-46.640000,-23.560000?overview=full&geometries=polyline"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(response, org.springframework.http.MediaType.APPLICATION_JSON));
        RotaEntregaResponseDTO rota = rotaService.calcularRota(pedido);
        server.verify();
        assertEquals(1234, rota.distanciaMetros());

        assertNotNull(rota);
        assertEquals("ped_rota_1", rota.pedidoId());
        assertEquals("Hamburgueria Nhac", rota.lojaNome());
        assertEquals(-23.55052, rota.origem().latitude());
        assertEquals(-46.63330, rota.origem().longitude());
        assertEquals(-23.56000, rota.destino().latitude());
        assertEquals(-46.64000, rota.destino().longitude());
        assertTrue(rota.distanciaKm() > 0);
        assertTrue(rota.duracaoEstimadaMinutos() >= 1);
        assertNotNull(rota.polyline());
        assertFalse(rota.polyline().isBlank());
    }

    @Test
    void semCoordenadasNaoConsultaServicoNemInventaDestino() {
        Pedido pedido = pedidoValido();
        pedido.setEntregaLatitude(null);
        var erro = assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,
                () -> rotaService.calcularRota(pedido));
        assertTrue(erro.getMessage().contains("não possui coordenadas de entrega"));
        server.verify();
    }

    @Test
    void lojaComZeroZeroRecebeCausaEspecifica() {
        Pedido pedido = pedidoValido();
        pedido.getLoja().setGeoLocalizacao(new GeoLocalizacao(0, 0, null));
        var erro = assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,
                () -> rotaService.calcularRota(pedido));
        assertTrue(erro.getMessage().contains("coordenadas da loja inválidas"));
        server.verify();
    }

    @Test
    void falhaExternaNaoViraLinhaRetaNemDistanciaFicticia() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());
        assertThrows(br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException.class,
                () -> rotaService.calcularRota(pedidoValido()));
        server.verify();
    }

    private Pedido pedidoValido() {
        var loja = new Loja();
        loja.setGeoLocalizacao(new GeoLocalizacao(-23.5, -46.6, null));
        var pedido = new Pedido();
        pedido.setLoja(loja);
        pedido.setEntregaLatitude(-23.6);
        pedido.setEntregaLongitude(-46.7);
        return pedido;
    }
}
