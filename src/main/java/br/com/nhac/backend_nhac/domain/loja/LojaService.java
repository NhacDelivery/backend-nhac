package br.com.nhac.backend_nhac.domain.loja;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import static br.com.nhac.backend_nhac.config.cache.CacheNames.*;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import br.com.nhac.backend_nhac.domain.loja.dto.LojaCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.AtualizarLocalizacaoLojaDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaDetalhesDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.AcessoNegadoException;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import jakarta.transaction.Transactional;


@Service
public class LojaService {

    private final LojaRepository lojaRepository;
    private final UsuarioRepository usuarioRepository;
    private final LojaAccessService lojaAccessService;
    private final FreteService freteService;


    public LojaService(LojaRepository lojaRepository, UsuarioRepository usuarioRepository,
                       LojaAccessService lojaAccessService, FreteService freteService) {
        this.lojaRepository = lojaRepository;
        this.usuarioRepository = usuarioRepository;
        this.lojaAccessService = lojaAccessService;
        this.freteService = freteService;
    }


    @Cacheable(cacheNames = LOJAS, condition = "#p4 >= 0 && #p4 < 20 && #p5 > 0 && #p5 <= 100 && #p1 == null && #p2 == null")
    public Page<LojaResumoDTO> obterLojasPaginadas(String nome, Double lat, Double lng, Double raio, int page, int size) {
        Pageable paginacao = PageRequest.of(page, size);

        Page<Loja> lojas;
        if (nome == null && lat == null && lng == null) {
            lojas = lojaRepository.findByIsAbertoTrue(paginacao);
        } else {
            lojas = lojaRepository.buscarLojasComFiltros(nome, lat, lng, raio, paginacao);
        }

        return lojas.map(LojaResumoDTO::new);
    }

    @Cacheable(cacheNames = LOJA)
    public LojaDetalhesDTO obterLojaId(String id) {

        Loja loja = lojaRepository.findByIdAndIsAbertoTrue(id)
                .orElseThrow(() -> new IdNaoEncontradoException("A loja com o id: " + id + " não foi encontrada."));

        return new LojaDetalhesDTO(loja);


    }

    @Transactional
    @CacheEvict(cacheNames = {LOJAS, LOJA}, allEntries = true)
    public LojaResumoDTO criarLoja(LojaCreateDTO dto, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado para cadastrar uma loja.");
        }

        if (lojaRepository.findByUsuarioId(usuarioLogado.getId()).isPresent()) {
            throw new RegraDeNegocioException("Você já possui uma loja cadastrada.");
        }

        String novoId = "loja_" + UUID.randomUUID();

        Loja novaLoja = dto.toEntity();
        novaLoja.setId(novoId);
        novaLoja.setUsuarioId(usuarioLogado.getId());

        if (usuarioLogado.getPapel() == Papel.CLIENTE) {
            usuarioLogado.setPapel(Papel.LOJISTA);
            usuarioRepository.save(usuarioLogado);
        }

        Loja lojaSalva = lojaRepository.save(novaLoja);
        return new LojaResumoDTO(lojaSalva);
    }

    public br.com.nhac.backend_nhac.domain.loja.dto.CalcularFreteResponseDTO calcularFrete(String lojaId, br.com.nhac.backend_nhac.domain.loja.dto.CalcularFreteRequestDTO dto) {
        Loja loja = lojaRepository.findByIdAndIsAbertoTrue(lojaId)
                .orElseThrow(() -> new IdNaoEncontradoException("A loja com o id: " + lojaId + " não foi encontrada ou está fechada."));

        return freteService.calcular(loja);
    }

    public LojaDetalhesDTO obterMinhaLoja(Usuario usuarioLogado) {
    if (usuarioLogado == null) {
        throw new AcessoNegadoException("É necessário estar autenticado para consultar a loja.");
    }
    // Antes só olhava loja.usuarioId (o dono) — um funcionário logado
    // (papel=FUNCIONARIO) nunca é o dono, então caía sempre em
    // LOJA_NAO_ENCONTRADA e o frontend mandava ele pro onboarding de
    // "criar loja nova". LojaAccessService resolve dono OU funcionário.
    Loja loja = lojaAccessService.obterLojaAcessivel(usuarioLogado);
    return new LojaDetalhesDTO(loja);
}

    @Transactional
    @CacheEvict(cacheNames = {LOJAS, LOJA, PRODUTOS, PRODUTO}, allEntries = true)
    public LojaDetalhesDTO atualizarLoja(String id, LojaCreateDTO dto, Usuario usuarioLogado) {
    if (usuarioLogado == null) {
        throw new AcessoNegadoException("É necessário estar autenticado para atualizar a loja.");
    }

    Loja loja = lojaRepository.findById(id)
            .orElseThrow(() -> new br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException(id));

    boolean isAdmin = usuarioLogado.getPapel() == Papel.ADMIN;
    if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, loja.getId())) {
        throw new AcessoNegadoException("Acesso negado: você não tem permissão para atualizar esta loja.");
    }

    String donoOriginal = loja.getUsuarioId();
    GeoLocalizacao geoOriginal = loja.getGeoLocalizacao();

    Loja dadosAtualizados = dto.toEntity();
        loja.setNome(dadosAtualizados.getNome());
        loja.setDescricao(dadosAtualizados.getDescricao());
        loja.setCategoria(dadosAtualizados.getCategoria());
        loja.setImagemUrl(dadosAtualizados.getImagemUrl());
        loja.setAberto(dadosAtualizados.isAberto());
        loja.setDadosOperacionais(dadosAtualizados.getDadosOperacionais());
        loja.setEndereco(dadosAtualizados.getEndereco());
        loja.setHorariosFuncionamento(dadosAtualizados.getHorariosFuncionamento());
        loja.setFormasPagamento(dadosAtualizados.getFormasPagamento());
        loja.setUsuarioId(donoOriginal);
        loja.setGeoLocalizacao(geoOriginal);

        return new LojaDetalhesDTO(lojaRepository.save(loja));
    }

    @Transactional
    @CacheEvict(cacheNames = {LOJAS, LOJA}, allEntries = true)
    public LojaDetalhesDTO atualizarLocalizacao(String id, AtualizarLocalizacaoLojaDTO dto, Usuario usuarioLogado) {
        if (usuarioLogado == null) {
            throw new AcessoNegadoException("É necessário estar autenticado para atualizar a loja.");
        }
        Loja loja = lojaRepository.findById(id)
                .orElseThrow(() -> new br.com.nhac.backend_nhac.exceptions.LojaNaoEncontradaException(id));
        if (usuarioLogado.getPapel() != Papel.ADMIN
                && !lojaAccessService.temAcessoALoja(usuarioLogado, id)) {
            throw new AcessoNegadoException("Acesso negado: você não tem permissão para atualizar esta loja.");
        }
        if (!Double.isFinite(dto.latitude()) || !Double.isFinite(dto.longitude())
                || Math.abs(dto.latitude()) > 90 || Math.abs(dto.longitude()) > 180
                || (dto.latitude() == 0 && dto.longitude() == 0)) {
            throw new RegraDeNegocioException("Informe coordenadas válidas do endereço da loja.");
        }
        GeoLocalizacao geo = loja.getGeoLocalizacao();
        if (geo == null) geo = new GeoLocalizacao();
        geo.setGeoLat(dto.latitude());
        geo.setGeoLng(dto.longitude());
        geo.setGeoHash(null);
        loja.setGeoLocalizacao(geo);
        // O endereço textual e o ponto de retirada precisam mudar na mesma
        // transação. Um PUT separado deixava pedidos serem despachados para
        // as coordenadas antigas depois de uma alteração de endereço.
        if (dto.endereco() != null) {
            var endereco = dto.endereco();
            loja.setEndereco(new EnderecoLoja(endereco.rua(), endereco.numero(),
                    endereco.cidade(), endereco.estado(), endereco.cep(),
                    endereco.bairro(), endereco.complemento()));
        }
        return new LojaDetalhesDTO(lojaRepository.save(loja));
    }


    @Transactional
    @CacheEvict(cacheNames = {LOJAS, LOJA, PRODUTOS, PRODUTO}, allEntries = true)
    public LojaDetalhesDTO atualizarAbertura(String id, Boolean isAberto, Usuario usuarioLogado) {
    if (usuarioLogado == null) {
        throw new AcessoNegadoException("É necessário estar autenticado para atualizar a loja.");
    }

    if (isAberto == null) {
        throw new RegraDeNegocioException("O status de abertura (isAberto) deve ser informado.");
    }

    Loja loja = lojaRepository.findById(id)
            .orElseThrow(() -> new LojaNaoEncontradaException(id));

    boolean isAdmin = usuarioLogado.getPapel() == Papel.ADMIN;
    if (!isAdmin && !lojaAccessService.temAcessoALoja(usuarioLogado, loja.getId())) {
        throw new AcessoNegadoException("Acesso negado: você não tem permissão para abrir/fechar esta loja.");
    }

    loja.setAberto(isAberto);
    return new LojaDetalhesDTO(lojaRepository.save(loja));
}}
