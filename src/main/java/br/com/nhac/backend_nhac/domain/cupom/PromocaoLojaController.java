package br.com.nhac.backend_nhac.domain.cupom;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.*;

@RestController
@RequestMapping("/api/v1/lojista/cupons")
public class PromocaoLojaController {
    private final CupomRepository cupons;
    private final LojaAccessService acesso;
    public PromocaoLojaController(CupomRepository cupons, LojaAccessService acesso) {
        this.cupons = cupons;
        this.acesso = acesso;
    }
    public record Dados(@NotBlank @Pattern(regexp="[A-Z0-9_-]{3,30}") String codigo, @Size(max=255) String descricao,
            @NotNull @Pattern(regexp="FIXO|PERCENTUAL|FRETE_GRATIS") String tipo,
            @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal valor,
            @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal minimo,
            @NotNull LocalDate inicio, @NotNull @FutureOrPresent LocalDate fim,
            @Min(1) int limite, boolean ativo) {}
    public record Resumo(String id, String codigo, String tipo, BigDecimal valor, BigDecimal minimo,
            LocalDate inicio, LocalDate fim, int limite, boolean ativo) {
        static Resumo de(Cupom c) {
            return new Resumo(c.getId(),c.getCodigo(),c.getTipoDesconto(),c.getValorDesconto(),
                    c.getValorMinimoPedido(),c.getInicio().toLocalDate(),c.getDataValidade().toLocalDate().minusDays(1),
                    c.getLimiteUsos(),c.isAtivo());
        }
    }
    private Loja loja(Usuario usuario) {
        acesso.exigirCatalogo(usuario);
        return acesso.obterLojaAcessivel(usuario);
    }
    @GetMapping @Transactional(readOnly=true)
    public List<Resumo> listar(@AuthenticationPrincipal Usuario usuario) {
        return cupons.findByLojaIdOrderByCriadoEmDesc(loja(usuario).getId()).stream().map(Resumo::de).toList();
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Resumo criar(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody Dados dto) {
        var loja = loja(usuario);
        if (dto.fim().isBefore(dto.inicio())) throw new RegraDeNegocioException("Confira o período do cupom.");
        if (!dto.tipo().equals("FRETE_GRATIS") && dto.valor().signum() <= 0 ||
                dto.tipo().equals("PERCENTUAL") && dto.valor().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new RegraDeNegocioException("Valor de desconto inválido.");
        if (cupons.existsByCodigo(dto.codigo())) throw new RegraDeNegocioException("Este código já está em uso.");
        var c = new Cupom();
        c.setId(UUID.randomUUID().toString()); c.setLoja(loja); c.setCodigo(dto.codigo());
        c.setTipoDesconto(dto.tipo()); c.setValorDesconto(dto.tipo().equals("FRETE_GRATIS") ? BigDecimal.ZERO : dto.valor());
        c.setDescricao(dto.descricao() != null && !dto.descricao().isBlank() ? dto.descricao().trim() : loja.getNome());
        c.setValorMinimoPedido(dto.minimo()); c.setLimiteUsos(dto.limite()); c.setAtivo(dto.ativo());
        c.setInicio(dto.inicio().atStartOfDay()); c.setDataValidade(dto.fim().plusDays(1).atStartOfDay());
        c.setCriadoEm(LocalDateTime.now());
        return Resumo.de(cupons.save(c));
    }
    public record Estado(@NotNull Boolean ativo) {}
    @PatchMapping("/{id}") @Transactional
    public Resumo alterar(@AuthenticationPrincipal Usuario usuario, @PathVariable String id, @Valid @RequestBody Estado estado) {
        var loja = loja(usuario);
        var c = cupons.findLockedById(id).filter(x -> x.getLoja()!=null && x.getLoja().getId().equals(loja.getId()))
                .orElseThrow(() -> new IdNaoEncontradoException("Cupom não encontrado."));
        c.setAtivo(estado.ativo());
        return Resumo.de(c);
    }
}
