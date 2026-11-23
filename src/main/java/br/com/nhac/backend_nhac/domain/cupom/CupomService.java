package br.com.nhac.backend_nhac.domain.cupom;

import br.com.nhac.backend_nhac.domain.loja.FreteService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;

@Service
public class CupomService {
    private final CupomRepository repository;
    private final UsuarioRepository usuarios;
    private final BigDecimal valor;
    private final BigDecimal minimo;
    private final int dias;
    private final FreteService frete;

    public CupomService(CupomRepository repository, UsuarioRepository usuarios, FreteService frete,
            @Value("${nhac.cupom.boas-vindas.valor:5.00}") BigDecimal valor,
            @Value("${nhac.cupom.boas-vindas.minimo:25.00}") BigDecimal minimo,
            @Value("${nhac.cupom.boas-vindas.validade-dias:30}") int dias) {
        if (valor.signum() <= 0 || minimo.compareTo(valor) <= 0 || dias <= 0) {
            throw new IllegalArgumentException("Cupom de boas-vindas exige valor positivo, mínimo maior que o desconto e validade positiva.");
        }
        this.repository = repository;
        this.usuarios = usuarios;
        this.frete = frete;
        this.valor = valor;
        this.minimo = minimo;
        this.dias = dias;
    }

    @Transactional(readOnly = true)
    public List<CupomResponse> listar(String usuarioId) {
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .map(c -> CupomResponse.from(c, BigDecimal.ZERO)).toList();
    }

    @Transactional(readOnly = true)
    public List<CupomResponse> listar(String usuarioId, String lojaId) {
        var resultado = new java.util.ArrayList<>(listar(usuarioId));
        if (lojaId != null) {
            for (Cupom c : repository.findByLojaIdOrderByCriadoEmDesc(lojaId)) {
                var dto = CupomResponse.from(c, BigDecimal.ZERO, repository.usosDoCliente(usuarioId, c.getId()));
                if ("DISPONIVEL".equals(dto.status())) resultado.add(dto);
            }
        }
        return resultado;
    }

    @Transactional
    public CupomResponse ganharBoasVindas(String usuarioId) {
        // O mesmo lock utilizado no checkout serializa resgates da mesma conta.
        var usuario = usuarios.findLockedById(usuarioId)
                .orElseThrow(() -> new RegraDeNegocioException("Usuário não encontrado."));
        if (!usuario.isAtivo()) throw new RegraDeNegocioException("Usuário inativo.");
        Cupom cupom = repository.findByUsuarioIdAndOrigem(usuarioId, "BOAS_VINDAS").orElseGet(() -> {
            Cupom novo = new Cupom();
            novo.setId(UUID.randomUUID().toString());
            novo.setCodigo("BEMVINDO-" + novo.getId());
            novo.setDescricao("Desconto nos produtos. Um cupom de boas-vindas por conta.");
            novo.setTipoDesconto("FIXO");
            novo.setValorDesconto(valor);
            novo.setValorMinimoPedido(minimo);
            novo.setCriadoEm(LocalDateTime.now());
            novo.setDataValidade(LocalDateTime.now().plusDays(dias));
            novo.setUsuarioId(usuarioId);
            novo.setOrigem("BOAS_VINDAS");
            return repository.save(novo);
        });
        // Repetir o resgate não renova a validade e não gera outro cupom.
        return CupomResponse.from(cupom, BigDecimal.ZERO);
    }

    @Transactional
    public CupomResponse validar(String usuarioId, String cupomId, BigDecimal subtotal) {
        return validar(usuarioId, cupomId, subtotal, null);
    }

    @Transactional
    public CupomResponse validar(String usuarioId, String cupomId, BigDecimal subtotal, String lojaId) {
        Cupom cupom = obterValido(usuarioId, cupomId, subtotal, lojaId);
        return CupomResponse.from(cupom, desconto(cupom, subtotal), usos(usuarioId, cupom));
    }

    @Transactional
    public BigDecimal consumir(String usuarioId, String cupomId, BigDecimal subtotal) {
        return consumir(usuarioId, cupomId, subtotal, null);
    }

    @Transactional
    public BigDecimal consumir(String usuarioId, String cupomId, BigDecimal subtotal, String lojaId) {
        Cupom cupom = obterValido(usuarioId, cupomId, subtotal, lojaId);
        BigDecimal desconto = desconto(cupom, subtotal);
        cupom.setUsosAtuais(cupom.getUsosAtuais() + 1);
        repository.save(cupom);
        return desconto;
    }

    @Transactional
    public void devolver(String usuarioId, String cupomId) {
        Cupom cupom = repository.findLockedById(cupomId)
                .orElseThrow(() -> new RegraDeNegocioException("Cupom não encontrado."));
        if (cupom.getLoja() == null && !usuarioId.equals(cupom.getUsuarioId())) throw new RegraDeNegocioException("Cupom não pertence à conta.");
        cupom.setUsosAtuais(Math.max(0, cupom.getUsosAtuais() - 1));
        repository.save(cupom);
    }

    private long usos(String usuarioId, Cupom cupom) {
        return cupom.getLoja() == null ? cupom.getUsosAtuais() : repository.usosDoCliente(usuarioId, cupom.getId());
    }

    private Cupom obterValido(String usuarioId, String cupomId, BigDecimal subtotal, String lojaId) {
        Cupom cupom = repository.findLockedById(cupomId)
                .orElseThrow(() -> new RegraDeNegocioException("Cupom indisponível."));
        if (cupom.getLoja() != null) {
            if (!cupom.getLoja().getId().equals(lojaId)) throw new RegraDeNegocioException("Este cupom pertence a outra loja.");
        } else if (!usuarioId.equals(cupom.getUsuarioId())) throw new RegraDeNegocioException("Cupom indisponível para esta conta.");
        if (!"DISPONIVEL".equals(CupomResponse.from(cupom, BigDecimal.ZERO, usos(usuarioId, cupom)).status())) {
            throw new RegraDeNegocioException("Cupom usado ou expirado.");
        }
        if (subtotal == null || subtotal.signum() <= 0 || subtotal.compareTo(cupom.getValorMinimoPedido()) < 0) {
            throw new RegraDeNegocioException("Pedido mínimo para este cupom: R$ " + cupom.getValorMinimoPedido().toPlainString());
        }
        return cupom;
    }

    private BigDecimal desconto(Cupom cupom, BigDecimal subtotal) {
        if ("FRETE_GRATIS".equals(cupom.getTipoDesconto())) {
            return frete.calcularTaxa(cupom.getLoja());
        }
        BigDecimal desconto = "PERCENTUAL".equals(cupom.getTipoDesconto())
                ? subtotal.multiply(cupom.getValorDesconto()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : cupom.getValorDesconto();
        return desconto.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }
}
