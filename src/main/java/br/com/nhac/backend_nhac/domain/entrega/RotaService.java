package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entrega.dto.PontoCoordenadaDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.RotaEntregaResponseDTO;
import br.com.nhac.backend_nhac.domain.entregador.EntregadorService;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RotaService {

    private static final Logger log = LoggerFactory.getLogger(RotaService.class);

    private final RestClient restClient;
    private record RouteKey(String pedidoId, String nomeLoja, Object geo, Double lat, Double lng,
            String servidor, boolean mock) {}
    private final com.github.benmanes.caffeine.cache.Cache<RouteKey, RotaEntregaResponseDTO> rotas =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(256)
                    .expireAfterWrite(java.time.Duration.ofMinutes(15)).recordStats().build();

    @Value("${nhac.routing.osrm-url:https://router.project-osrm.org}")
    private String osrmBaseUrl = "https://router.project-osrm.org";

    @Value("${nhac.routing.mock-mode:false}")
    private boolean mockMode;

    public RotaService() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public RotaService(RestClient restClient) {
        this.restClient = restClient;
    }

    public RotaEntregaResponseDTO calcularRota(Pedido pedido) {
        if (pedido.getLoja() == null || pedido.getLoja().getGeoLocalizacao() == null)
            return calcularSemCache(pedido);
        var geo = pedido.getLoja().getGeoLocalizacao();
        var key = new RouteKey(pedido.getId(), pedido.getLoja().getNome(),
                java.util.List.of(geo.getGeoLat(), geo.getGeoLng()), pedido.getEntregaLatitude(),
                pedido.getEntregaLongitude(), osrmBaseUrl, mockMode);
        return rotas.get(key, ignored -> calcularSemCache(pedido));
    }

    private RotaEntregaResponseDTO calcularSemCache(Pedido pedido) {
        if (pedido.getLoja() == null || pedido.getLoja().getGeoLocalizacao() == null) {
            throw new br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException("Rota indisponível: a loja não possui coordenadas geográficas cadastradas. Entre em contato com a loja para corrigir o cadastro.");
        }

        double origemLat = pedido.getLoja().getGeoLocalizacao().getGeoLat();
        double origemLng = pedido.getLoja().getGeoLocalizacao().getGeoLng();

        if (!mockMode &&
                (pedido.getEntregaLatitude() == null || pedido.getEntregaLongitude() == null)) {
            throw new br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException(
                    "Rota indisponível: este pedido não possui coordenadas de entrega. Entre em contato com a loja para confirmar o endereço; em uma nova compra, confirme o endereço no checkout.");
        }
        double destinoLat = mockMode ? -23.551000 : pedido.getEntregaLatitude();
        double destinoLng = mockMode ? -46.634000 : pedido.getEntregaLongitude();

        if (!coordenadasValidas(origemLat, origemLng)) {
            throw new br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException(
                    "Rota indisponível: coordenadas da loja inválidas. Solicite à loja a correção do cadastro.");
        }
        if (!coordenadasValidas(destinoLat, destinoLng)) {
            throw new br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException(
                    "Rota indisponível: coordenadas de entrega inválidas. Entre em contato com a loja para confirmar o endereço.");
        }
        PontoCoordenadaDTO origem = new PontoCoordenadaDTO(origemLat, origemLng);
        PontoCoordenadaDTO destino = new PontoCoordenadaDTO(destinoLat, destinoLng);
        String lojaNome = pedido.getLoja().getNome();

        if (!mockMode) try {
            String url = String.format(Locale.US, "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline",
                    osrmBaseUrl, origemLng, origemLat, destinoLng, destinoLat);

            String jsonResponse = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);

            if (jsonResponse != null) {
                JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
                if ("Ok".equalsIgnoreCase(root.get("code").getAsString())) {
                    JsonArray routes = root.getAsJsonArray("routes");
                    if (!routes.isEmpty()) {
                        JsonObject route = routes.get(0).getAsJsonObject();
                        double distanciaMetros = route.get("distance").getAsDouble();
                        double duracaoSegundos = route.get("duration").getAsDouble();
                        String polyline = route.get("geometry").getAsString();
                        List<PontoCoordenadaDTO> waypoints = decodificarPolyline(polyline);
                        if (!Double.isFinite(distanciaMetros) || distanciaMetros < 0 ||
                                !Double.isFinite(duracaoSegundos) || duracaoSegundos < 0 || waypoints.size() < 2 ||
                                waypoints.stream().anyMatch(p -> !coordenadasValidas(p.latitude(), p.longitude()))) {
                            throw new IllegalStateException("Resposta inválida do serviço de rotas.");
                        }

                        double distanciaKm = Math.round((distanciaMetros / 1000.0) * 100.0) / 100.0;
                        int duracaoMinutos = (int) Math.ceil(duracaoSegundos / 60.0);

                        return new RotaEntregaResponseDTO(
                                pedido.getId(),
                                lojaNome,
                                origem,
                                destino,
                                distanciaMetros,
                                distanciaKm,
                                Math.max(1, duracaoMinutos),
                                polyline,
                                waypoints
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao consultar API externa de rotas (OSRM): {}", e.getMessage());
        }

        if (!mockMode) {
            throw new br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException(
                    "O serviço de rotas não retornou um trajeto válido. Tente carregar a rota novamente mais tarde.");
        }
        // Geometria sintética exclusiva dos testes E2E explicitamente configurados.
        double distanciaKm = EntregadorService.calcularDistanciaKm(origemLat, origemLng, destinoLat, destinoLng);
        double distanciaMetros = distanciaKm * 1000.0;
        int duracaoMinutos = (int) Math.ceil((distanciaKm / 30.0) * 60.0); // estimativa a 30 km/h de moto

        List<PontoCoordenadaDTO> fallbackPoints = List.of(origem, destino);
        String fallbackPolyline = codificarPolyline(fallbackPoints);

        return new RotaEntregaResponseDTO(
                pedido.getId(),
                lojaNome,
                origem,
                destino,
                distanciaMetros,
                Math.round(distanciaKm * 100.0) / 100.0,
                Math.max(1, duracaoMinutos),
                fallbackPolyline,
                fallbackPoints
        );
    }

    private static boolean coordenadasValidas(double lat, double lng) {
        return Double.isFinite(lat) && Double.isFinite(lng) && Math.abs(lat) <= 90 && Math.abs(lng) <= 180 && (lat != 0 || lng != 0);
    }

    public static List<PontoCoordenadaDTO> decodificarPolyline(String encoded) {
        List<PontoCoordenadaDTO> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new PontoCoordenadaDTO((lat / 1E5), (lng / 1E5)));
        }

        return poly;
    }

    public static String codificarPolyline(List<PontoCoordenadaDTO> points) {
        StringBuilder result = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;

        for (PontoCoordenadaDTO point : points) {
            long lat = Math.round(point.latitude() * 1e5);
            long lng = Math.round(point.longitude() * 1e5);

            encodeValue(lat - prevLat, result);
            encodeValue(lng - prevLng, result);

            prevLat = lat;
            prevLng = lng;
        }

        return result.toString();
    }

    private static void encodeValue(long value, StringBuilder result) {
        value = value < 0 ? ~(value << 1) : (value << 1);
        while (value >= 0x20) {
            result.append((char) ((0x20 | (value & 0x1f)) + 63));
            value >>= 5;
        }
        result.append((char) (value + 63));
    }
}
