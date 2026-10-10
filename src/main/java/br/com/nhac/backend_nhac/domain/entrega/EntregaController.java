package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.domain.entrega.dto.EntregaAtivaResponseDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.OfertaEntregaDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.RotaEntregaResponseDTO;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entregas")
@Tag(name = "Entregas e Despacho", description = "Endpoints de despacho, ofertas para motoboys, ciclo de vida da corrida e rotas no mapa")
public class EntregaController {

    private final CoordenadasEntregaService coordenadasService;
    private final DespachoService despachoService;
    private final RotaService rotaService;
    private final PedidoRepository pedidoRepository;
    private final LojaAccessService lojaAccessService;

    public EntregaController(
            DespachoService despachoService,
            RotaService rotaService,
            PedidoRepository pedidoRepository,
            LojaAccessService lojaAccessService,
            CoordenadasEntregaService coordenadasService
    ) {
        this.coordenadasService = coordenadasService;
        this.despachoService = despachoService;
        this.rotaService = rotaService;
        this.pedidoRepository = pedidoRepository;
        this.lojaAccessService = lojaAccessService;
    }

    @PostMapping("/despachar/{pedidoId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LOJISTA', 'FUNCIONARIO')")
    @Operation(summary = "Aciona o despacho manual do pedido para entregadores próximos",
            description = "O despacho também é disparado automaticamente quando a loja move o pedido para PREPARANDO. Esta rota existe para reenviar as ofertas quando ninguém aceitou na primeira rodada.")
    public ResponseEntity<List<OfertaEntregaDTO>> despacharPedido(
            @PathVariable String pedidoId,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        // Sem esta checagem, qualquer LOJISTA autenticado podia despachar o
        // pedido de outra loja: o hasAnyRole do SecurityConfig só valida papel,
        // não dono.
        Pedido pedido = buscarPedido(pedidoId);
        if (usuarioLogado.getPapel() != Papel.ADMIN
                && !lojaAccessService.temAcessoALoja(usuarioLogado, pedido.getLoja().getId())) {
            throw new AcessoNegadoException("Este pedido não pertence à sua loja.");
        }

        return ResponseEntity.ok(despachoService.despacharPedido(pedidoId));
    }

    @GetMapping("/ofertas/pendentes")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    @Operation(summary = "Lista ofertas de entrega pendentes para o entregador logado")
    public ResponseEntity<List<OfertaEntregaDTO>> listarOfertasPendentes(
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(despachoService.listarOfertasPendentes(usuarioLogado));
    }

    @PostMapping("/ofertas/{id}/aceitar")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    @Operation(summary = "Aceita uma oferta de entrega pendente",
            description = "Atribui a corrida ao entregador. O pedido continua PREPARANDO até ele confirmar a retirada em POST /{pedidoId}/coletar.")
    public ResponseEntity<EntregaAtivaResponseDTO> aceitarOferta(
            @PathVariable String id,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(despachoService.aceitarOferta(id, usuarioLogado));
    }

    @PostMapping("/ofertas/{id}/recusar")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    @Operation(summary = "Recusa uma oferta de entrega")
    public ResponseEntity<Void> recusarOferta(
            @PathVariable String id,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        despachoService.recusarOferta(id, usuarioLogado);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ativa")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    @Operation(summary = "Retorna a entrega em andamento associada ao entregador logado")
    public ResponseEntity<EntregaAtivaResponseDTO> obterEntregaAtiva(
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(despachoService.obterEntregaAtiva(usuarioLogado));
    }

    @PostMapping("/{pedidoId}/coletar")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    @Operation(summary = "Confirma a retirada do pedido na loja",
            description = "Move o pedido de PREPARANDO para SAIU_ENTREGA e grava coletado_em. Idempotente: reenviar depois de já ter coletado devolve 200 com a entrega ativa.")
    public ResponseEntity<EntregaAtivaResponseDTO> coletarPedido(
            @PathVariable String pedidoId,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(despachoService.coletarPedido(pedidoId, usuarioLogado));
    }

    @PostMapping("/{pedidoId}/concluir")
    @PreAuthorize("hasAnyRole('ENTREGADOR', 'ADMIN')")
    @Operation(summary = "Dá baixa na entrega",
            description = "Move o pedido de SAIU_ENTREGA para ENTREGUE, grava entregue_em e devolve o entregador para ONLINE (liberando-o para novas ofertas). Idempotente.")
    public ResponseEntity<Void> concluirEntrega(
            @PathVariable String pedidoId,
            @AuthenticationPrincipal Usuario usuarioLogado,
            @RequestBody(required = false) br.com.nhac.backend_nhac.domain.entrega.dto.ConcluirEntregaDTO dto
    ) {
        String codigo = dto != null ? dto.codigo() : null;
        despachoService.concluirEntrega(pedidoId, usuarioLogado, codigo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{pedidoId}/rota")
    @Operation(summary = "Calcula e retorna a rota com Polyline no mapa entre a loja e o endereço de entrega")
    public ResponseEntity<RotaEntregaResponseDTO> obterRota(
            @PathVariable String pedidoId,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        Pedido pedido = buscarPedido(pedidoId);
        validarAcessoARota(pedido, usuarioLogado);
        return ResponseEntity.ok(rotaService.calcularRota(pedido, pedido.getEntregador() != null && usuarioLogado.getId().equals(pedido.getEntregador().getUsuario().getId())));
    }

    public record CoordenadasDestinoDTO(
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") Double latitude,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") Double longitude) {}
    @PutMapping("/{pedidoId}/coordenadas-destino")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> corrigirDestino(@PathVariable String pedidoId, @AuthenticationPrincipal Usuario usuario,
            @RequestBody @jakarta.validation.Valid CoordenadasDestinoDTO dto) {
        coordenadasService.corrigir(pedidoId, usuario, dto.latitude(), dto.longitude());
        return ResponseEntity.noContent().build();
    }

    public record LocalizacaoEntregadorDTO(Double latitude, Double longitude,
            java.time.Instant atualizadaEm) {}

    @GetMapping("/{pedidoId}/localizacao-entregador")
    @Operation(summary = "Posição recente do entregador desta corrida")
    public ResponseEntity<LocalizacaoEntregadorDTO> obterLocalizacaoEntregador(
            @PathVariable String pedidoId,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        Pedido pedido = buscarPedido(pedidoId);
        validarAcessoARota(pedido, usuarioLogado);
        if (pedido.getStatus() != br.com.nhac.backend_nhac.domain.pedido.StatusPedido.PREPARANDO &&
                pedido.getStatus() != br.com.nhac.backend_nhac.domain.pedido.StatusPedido.SAIU_ENTREGA) {
            return ResponseEntity.noContent().build();
        }
        var entregador = pedido.getEntregador();
        if (entregador == null || entregador.getLatitudeAtual() == null ||
                entregador.getLongitudeAtual() == null || entregador.getUltimaAtualizacaoLocalizacao() == null ||
                entregador.getUltimaAtualizacaoLocalizacao().isBefore(java.time.Instant.now().minusSeconds(120))) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new LocalizacaoEntregadorDTO(
                entregador.getLatitudeAtual(), entregador.getLongitudeAtual(),
                entregador.getUltimaAtualizacaoLocalizacao()));
    }

    private Pedido buscarPedido(String pedidoId) {
        return pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new IdNaoEncontradoException("Pedido " + pedidoId + " não encontrado."));
    }

    /**
     * Antes desta checagem, /entregas/{pedidoId}/rota estava só sob
     * .authenticated(): qualquer conta logada conseguia descobrir o endereço de
     * entrega de qualquer pedido do sistema, bastando o id.
     */
    private void validarAcessoARota(Pedido pedido, Usuario usuario) {
        if (usuario.getPapel() == Papel.ADMIN) return;

        boolean ehCliente = usuario.getId().equals(pedido.getUsuarioId());
        boolean ehEntregadorDaCorrida = pedido.getEntregador() != null
                && pedido.getEntregador().getUsuario() != null
                && usuario.getId().equals(pedido.getEntregador().getUsuario().getId());
        boolean ehLoja = lojaAccessService.temAcessoALoja(usuario, pedido.getLoja().getId());

        if (!ehCliente && !ehEntregadorDaCorrida && !ehLoja) {
            throw new AcessoNegadoException("Você não tem acesso à rota deste pedido.");
        }
    }
}
