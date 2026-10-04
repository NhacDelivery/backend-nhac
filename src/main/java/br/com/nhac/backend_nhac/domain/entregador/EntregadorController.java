package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarLocalizacaoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarStatusDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarVeiculoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarDocumentosDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarDadosBancariosDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.CadastroEntregadorDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.EntregaHistoricoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.EntregadorResponseDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.GanhosEntregadorDTO;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.entrega.DespachoService;
import br.com.nhac.backend_nhac.domain.entrega.dto.EntregaAtivaResponseDTO;
import br.com.nhac.backend_nhac.domain.entrega.dto.OfertaEntregaDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.EstadoEntregadorDTO;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import org.springframework.http.CacheControl;
import java.util.List;
import java.time.Instant;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/entregador")
@Tag(name = "Entregador", description = "Endpoints para gerenciamento do perfil e status do motoboy/entregador")
public class EntregadorController {

    private final EntregadorService entregadorService;
    private final DespachoService despachoService;
    private final GanhosEntregadorService ganhosEntregadorService;
    private final br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregadorService avaliacaoEntregadorService;

    public EntregadorController(
            EntregadorService entregadorService,
            DespachoService despachoService,
            GanhosEntregadorService ganhosEntregadorService,
            br.com.nhac.backend_nhac.domain.avaliacao_entregador.AvaliacaoEntregadorService avaliacaoEntregadorService
    ) {
        this.entregadorService = entregadorService;
        this.despachoService = despachoService;
        this.ganhosEntregadorService = ganhosEntregadorService;
        this.avaliacaoEntregadorService = avaliacaoEntregadorService;
    }

    @PostMapping("/cadastro")
    @Operation(summary = "Cadastra o usuário logado como entregador parceiro")
    public ResponseEntity<EntregadorResponseDTO> cadastrar(
            @RequestBody @Valid CadastroEntregadorDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        EntregadorResponseDTO resposta = entregadorService.cadastrar(dto, usuarioLogado);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @GetMapping("/perfil")
    @Operation(summary = "Retorna os dados cadastrais e status atual do entregador logado")
    public ResponseEntity<EntregadorResponseDTO> obterPerfil(
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(entregadorService.obterPerfil(usuarioLogado));
    }

    @GetMapping("/estado")
    @Operation(summary = "Perfil, corrida e ofertas atuais em uma única consulta HTTP")
    public ResponseEntity<EstadoEntregadorDTO> obterEstado(@AuthenticationPrincipal Usuario usuarioLogado) {
        var perfil = entregadorService.obterPerfil(usuarioLogado);
        EntregaAtivaResponseDTO entrega = null;
        if (perfil.ativo()) {
            try {
                entrega = despachoService.obterEntregaAtiva(usuarioLogado);
            } catch (IdNaoEncontradoException semEntrega) {
                // O perfil já foi validado acima; ausência de corrida é um estado normal.
            }
        }
        var ofertas = perfil.ativo() && entrega == null && perfil.statusOperacional() == StatusOperacional.ONLINE
                ? despachoService.listarOfertasPendentes(usuarioLogado)
                : List.<OfertaEntregaDTO>of();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new EstadoEntregadorDTO(perfil, entrega, ofertas, Instant.now()));
    }

    @PatchMapping("/veiculo")
    @Operation(summary = "Atualiza o veículo do entregador logado")
    public ResponseEntity<EntregadorResponseDTO> atualizarVeiculo(
            @RequestBody @Valid AtualizarVeiculoDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(entregadorService.atualizarVeiculo(dto, usuarioLogado));
    }

    @PatchMapping("/documentos")
    @Operation(summary = "Atualiza CPF e CNH do entregador logado")
    public ResponseEntity<EntregadorResponseDTO> atualizarDocumentos(
            @RequestBody @Valid AtualizarDocumentosDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(entregadorService.atualizarDocumentos(dto, usuarioLogado));
    }

    @PatchMapping("/dados-bancarios")
    @Operation(summary = "Salva a chave PIX do entregador logado")
    public ResponseEntity<EntregadorResponseDTO> atualizarDadosBancarios(
            @RequestBody @Valid AtualizarDadosBancariosDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(entregadorService.atualizarDadosBancarios(dto, usuarioLogado));
    }

    @PatchMapping("/status")
    @Operation(summary = "Altera o status operacional do entregador (ONLINE, OFFLINE)")
    public ResponseEntity<EntregadorResponseDTO> atualizarStatus(
            @RequestBody @Valid AtualizarStatusDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(entregadorService.atualizarStatus(dto, usuarioLogado));
    }

    @PatchMapping("/localizacao")
    @Operation(summary = "Atualiza a localização GPS atual do entregador (Heartbeat)")
    public ResponseEntity<EntregadorResponseDTO> atualizarLocalizacao(
            @RequestBody @Valid AtualizarLocalizacaoDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado
    ) {
        return ResponseEntity.ok(entregadorService.atualizarLocalizacao(dto, usuarioLogado));
    }

    @GetMapping("/entregas")
    @Operation(
            summary = "Histórico de corridas do entregador logado",
            description = "Paginado, mais recentes primeiro. Filtro opcional por status (ex.: ENTREGUE para ver só as concluídas)."
    )
    public ResponseEntity<Page<EntregaHistoricoDTO>> listarHistorico(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @RequestParam(required = false) StatusPedido status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ganhosEntregadorService.listarHistorico(usuarioLogado, status, pageable));
    }

    @GetMapping("/ganhos")
    @Operation(
            summary = "Resumo de ganhos do entregador por período",
            description = "periodo: HOJE, SETE_DIAS ou TRINTA_DIAS (padrão HOJE). Soma a taxa de frete dos pedidos ENTREGUE dentro da janela e devolve a série diária completa, inclusive dias zerados."
    )
    public ResponseEntity<GanhosEntregadorDTO> obterGanhos(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @RequestParam(required = false, defaultValue = "HOJE") PeriodoGanhos periodo
    ) {
        return ResponseEntity.ok(ganhosEntregadorService.obterGanhos(usuarioLogado, periodo));
    }

    @GetMapping("/avaliacoes")
    @Operation(summary = "Resumo e listagem paginada das avaliações do entregador logado")
    public ResponseEntity<br.com.nhac.backend_nhac.domain.avaliacao_entregador.dto.AvaliacoesEntregadorPageDTO> listarAvaliacoes(
            @AuthenticationPrincipal Usuario usuarioLogado,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Entregador entregador = entregadorService.buscarPorUsuario(usuarioLogado);
        return ResponseEntity.ok(avaliacaoEntregadorService.listarAvaliacoesDoEntregador(entregador.getId(), pageable));
    }
}
