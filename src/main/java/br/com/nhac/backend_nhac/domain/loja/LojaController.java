package br.com.nhac.backend_nhac.domain.loja;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.nhac.backend_nhac.domain.loja.dto.LojaCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaDetalhesDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaResumoDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.AtualizarLocalizacaoLojaDTO;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.ErroPadraoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/lojas")
@Tag(name = "Lojas", description = "Endpoints para listagem e consulta do catálogo de restaurantes") // Título no Swagger
public class LojaController {

    private final LojaService lojaService;

    public LojaController(LojaService lojaService) {
        this.lojaService = lojaService;
    }

    @Operation(summary = "Listar lojas abertas", description = "Devolve uma lista paginada de todas as lojas que estão atualmente abertas, utilizando um DTO resumido para otimização de rede.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lojas encontradas e paginadas com sucesso")
    })
    @GetMapping
    public ResponseEntity<Page<LojaResumoDTO>> listarLojas(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double raio,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(lojaService.obterLojasPaginadas(nome, lat, lng, raio, page, size));
    }

    @Operation(summary = "Detalhes de uma loja", description = "Busca todos os dados aninhados de uma loja específica através do seu ID, incluindo horários e endereço completo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loja encontrada com sucesso"),

            @ApiResponse(responseCode = "404", description = "Loja não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<LojaDetalhesDTO> obterLojaPorId(@PathVariable String id) {
        return ResponseEntity.ok(lojaService.obterLojaId(id));
    }


    @Operation(summary = "Criar nova loja", description = "Cadastra um novo restaurante vinculado ao usuário autenticado e promove o papel para LOJISTA. O dono da loja é sempre o usuário do token, nunca um campo do corpo da requisição.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Loja criada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    @PostMapping
    public ResponseEntity<LojaResumoDTO> criarLoja(
            @RequestBody @Valid LojaCreateDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        LojaResumoDTO resumoDTO = lojaService.criarLoja(dto, usuarioLogado);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumoDTO);
    }

    @Operation(summary = "Consultar a loja do usuário autenticado",
            description = "Retorna a loja vinculada ao token. Rota canônica do painel do lojista; não usa lojaId na URL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loja do usuário retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Usuário autenticado ainda não possui loja",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @GetMapping("/minha-loja")
    public ResponseEntity<LojaDetalhesDTO> obterMinhaLoja(@AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(lojaService.obterMinhaLoja(usuarioLogado));
    }

    @Operation(summary = "Atualizar loja",
            description = "Atualização completa da loja (mesmo contrato de criação). Apenas o dono ou ADMIN. O campo usuarioId do corpo, se enviado, é ignorado — o dono não pode ser alterado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Loja atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Payload inválido",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não é o dono da loja",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Loja não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PutMapping("/{id}")
    public ResponseEntity<LojaDetalhesDTO> atualizarLoja(
            @PathVariable String id,
            @RequestBody @Valid LojaCreateDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(lojaService.atualizarLoja(id, dto, usuarioLogado));
    }

    @PatchMapping("/{id}/localizacao")
    @Operation(summary = "Configurar coordenadas e, opcionalmente, o endereço da loja em uma única operação")
    public ResponseEntity<LojaDetalhesDTO> atualizarLocalizacao(
            @PathVariable String id,
            @RequestBody @Valid AtualizarLocalizacaoLojaDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {
        return ResponseEntity.ok(lojaService.atualizarLocalizacao(id, dto, usuarioLogado));
    }

    @Operation(summary = "Abrir ou fechar a loja",
            description = "Alterna rapidamente se a loja está aberta ou fechada, sem precisar reenviar o cadastro completo da loja. Dono, funcionário da loja ou ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status de abertura atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Payload inválido",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não tem acesso a esta loja",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class))),
            @ApiResponse(responseCode = "404", description = "Loja não encontrada",
                    content = @Content(schema = @Schema(implementation = ErroPadraoDTO.class)))
    })
    @PatchMapping("/{id}/abertura")
    public ResponseEntity<LojaDetalhesDTO> atualizarAbertura(
            @PathVariable String id,
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.loja.dto.AtualizarAberturaDTO dto,
            @AuthenticationPrincipal Usuario usuarioLogado) {

                if (usuarioLogado == null) {
        throw new br.com.nhac.backend_nhac.exceptions.AcessoNegadoException("Usuário não autenticado ou token inválido.");
    }
        return ResponseEntity.ok(lojaService.atualizarAbertura(id, dto.isAberto(), usuarioLogado));
    }

    @Operation(summary = "Calcular frete dinâmico", description = "Calcula o frete e o tempo de entrega com base na localização do cliente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cálculo realizado com sucesso"),
            @ApiResponse(responseCode = "404", description = "Loja não encontrada ou fechada")
    })
    @PostMapping("/{id}/calcular-frete")
    public ResponseEntity<br.com.nhac.backend_nhac.domain.loja.dto.CalcularFreteResponseDTO> calcularFrete(
            @PathVariable String id,
            @RequestBody @Valid br.com.nhac.backend_nhac.domain.loja.dto.CalcularFreteRequestDTO dto) {
        return ResponseEntity.ok(lojaService.calcularFrete(id, dto));
    }
}
