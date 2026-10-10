package br.com.nhac.backend_nhac.domain.usuario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaAccessService;
import br.com.nhac.backend_nhac.domain.usuario.dto.FuncionarioCreateDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.FuncionarioResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.dto.FuncionarioUpdateDTO;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;

/**
 * CRUD de funcionários (Round 20 / item 5.2). Decisões de produto já tomadas:
 *  - funcionário loga com conta própria (é um Usuario comum, papel=FUNCIONARIO);
 *  - cargo determina acesso ao catálogo/painel/financeiro em LojaAccessService;
 *  - o LOJISTA (dono) define a senha inicial no cadastro, sem convite por e-mail.
 *
 * Gestão de funcionários (criar/editar/desativar) é restrita ao DONO da loja —
 * um funcionário não pode cadastrar ou remover outro funcionário nesta fase
 * (ver pergunta em aberto #10 da spec: se isso mudar, é só trocar a checagem
 * de exigirDono() por lojaAccessService.temAcessoALoja()).
 */
@Service
public class FuncionarioService {

    private final UsuarioRepository usuarioRepository;
    private final LojaAccessService lojaAccessService;
    private final PasswordEncoder passwordEncoder;

    public FuncionarioService(UsuarioRepository usuarioRepository, LojaAccessService lojaAccessService, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.lojaAccessService = lojaAccessService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<FuncionarioResponseDTO> listar(Usuario usuarioLogado, Pageable pageable) {
        Loja loja = exigirDono(usuarioLogado);
        return usuarioRepository.findByLojaVinculadaIdOrderByCriadoEmDesc(loja.getId(), pageable)
                .map(FuncionarioResponseDTO::new);
    }

    @Transactional
    public FuncionarioResponseDTO criar(FuncionarioCreateDTO dto, Usuario usuarioLogado) {
        Loja loja = exigirDono(usuarioLogado);

        if (usuarioRepository.findByEmailIgnoreCase(dto.email()).isPresent()) {
            throw new RegraDeNegocioException("Já existe uma conta cadastrada com este e-mail.");
        }

        Usuario funcionario = new Usuario();
        funcionario.setId(UUID.randomUUID().toString());
        funcionario.setNome(dto.nome());
        funcionario.setEmail(dto.email());
        funcionario.setTelefone(dto.telefone());
        funcionario.setSenha(passwordEncoder.encode(dto.senha()));
        funcionario.setEnderecos(new ArrayList<>());
        funcionario.setPapel(Papel.FUNCIONARIO);
        funcionario.setLojaVinculadaId(loja.getId());
        funcionario.setCargo(dto.cargo());
        funcionario.setAtivo(true);
        funcionario.setEmailVerificado(true); // criado pelo lojista, não passa pelo fluxo de verificação por e-mail
        funcionario.setCriadoEm(Instant.now());

        usuarioRepository.save(funcionario);
        return new FuncionarioResponseDTO(funcionario);
    }

    @Transactional
    public FuncionarioResponseDTO atualizar(String funcionarioId, FuncionarioUpdateDTO dto, Usuario usuarioLogado) {
        Loja loja = exigirDono(usuarioLogado);
        Usuario funcionario = buscarFuncionarioDaLoja(funcionarioId, loja.getId());

       funcionario.setNome(dto.nome());
funcionario.setCargo(dto.cargo());

if (dto.telefone() != null && !dto.telefone().isBlank()) {
    funcionario.setTelefone(dto.telefone());
}
if (dto.imagemUrl() != null) {
    funcionario.setImagemUrl(dto.imagemUrl());
}

usuarioRepository.save(funcionario);
        return new FuncionarioResponseDTO(funcionario);
    }

    @Transactional
    public void desativar(String funcionarioId, Usuario usuarioLogado) {
        Loja loja = exigirDono(usuarioLogado);
        Usuario funcionario = buscarFuncionarioDaLoja(funcionarioId, loja.getId());

        // Usuario.isEnabled() (Spring Security / UserDetails) já retorna this.ativo,
        // então isso bloqueia login automaticamente — não precisa de nenhuma
        // lógica extra de revogação de token (ver pergunta em aberto #12 da spec
        // sobre tokens já emitidos e ainda válidos).
        funcionario.setAtivo(false);
        usuarioRepository.save(funcionario);
    }

    @Transactional
    public FuncionarioResponseDTO reativar(String funcionarioId, Usuario usuarioLogado) {
        Loja loja = exigirDono(usuarioLogado);
        Usuario funcionario = buscarFuncionarioDaLoja(funcionarioId, loja.getId());

        funcionario.setAtivo(true);
        usuarioRepository.save(funcionario);
        return new FuncionarioResponseDTO(funcionario);
    }

    private Usuario buscarFuncionarioDaLoja(String funcionarioId, String lojaId) {
        Usuario funcionario = usuarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new IdNaoEncontradoException("Funcionário com o id: " + funcionarioId + " não foi encontrado."));

        if (funcionario.getPapel() != Papel.FUNCIONARIO || !lojaId.equals(funcionario.getLojaVinculadaId())) {
            throw new AcessoNegadoException("Acesso negado: este funcionário não pertence à sua loja.");
        }
        return funcionario;
    }

    private Loja exigirDono(Usuario usuarioLogado) {
        if (usuarioLogado.getPapel() != Papel.LOJISTA && usuarioLogado.getPapel() != Papel.ADMIN) {
            throw new AcessoNegadoException("Acesso negado: apenas o dono da loja pode gerenciar a equipe.");
        }
        return lojaAccessService.obterLojaAcessivel(usuarioLogado);
    }
}
