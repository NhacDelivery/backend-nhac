package br.com.nhac.backend_nhac.domain.chat;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;

/**
 * Valida o @Version da entidade Conversa em cenário de concorrência real.
 *
 * Sem o optimistic lock, duas threads fazendo setNaoLidasLoja(atual + 1)
 * acabam com o último escritor sobrescrevendo o primeiro — o contador
 * fica errado. Com @Version, a segunda transação recebe
 * ObjectOptimisticLockingFailureException, e aqui validamos que o
 * contador final está correto depois que ambas terminam.
 *
 * O retry é feito manualmente dentro do teste (é o que o Spring faz por
 * padrão quando a transação é do framework, mas aqui chamamos o service
 * diretamente, então replicamos o comportamento).
 */
public class ChatConcorrenciaIT extends AbstractIntegrationTest {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private LojaRepository lojaRepository;
    @Autowired private ConversaRepository conversaRepository;
    @Autowired private MensagemRepository mensagemRepository;
    @Autowired private ChatService chatService;

    private Usuario cliente;
    private Loja loja;
    private Conversa conversa;

    @BeforeEach
    void prepararDados() {
        mensagemRepository.deleteAll();
        conversaRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario dono = criarUsuario("dono.conc@teste.com", Papel.LOJISTA);
        cliente = criarUsuario("cliente.conc@teste.com", Papel.CLIENTE);
        loja = criarLoja("loja-conc", dono.getId());
        conversa = conversaRepository.saveAndFlush(
                new Conversa("conv_conc_" + UUID.randomUUID(), loja, cliente.getId(),
                        ParticipanteTipo.CLIENTE));
    }

    /**
     * 2 threads enviam ao mesmo tempo. Sem @Version, uma atualização seria
     * perdida e naoLidasLoja terminaria em 1. Com @Version + retry, termina
     * em 2.
     *
     * Repete 5x para aumentar a chance de pegar a race (o timing varia por
     * execução e por máquina).
     */
    @RepeatedTest(5)
    @DisplayName("@Version previne lost update: 2 mensagens concorrentes resultam em naoLidasLoja = 2")
    void duasMensagensConcorrentesNaoPerdemIncremento() throws Exception {
        int concorrentes = 2;
        ExecutorService pool = Executors.newFixedThreadPool(concorrentes);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch terminou = new CountDownLatch(concorrentes);
        AtomicInteger sucessos = new AtomicInteger();

        for (int i = 0; i < concorrentes; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    largada.await();
                    // Simula o "retry" que o Spring faria numa transação de framework:
                    // se o save colidir com versão, tenta de novo até dar certo.
                    int tentativas = 0;
                    while (tentativas < 5) {
                        try {
                            chatService.enviarMensagem(conversa.getId(), cliente, "msg " + idx);
                            sucessos.incrementAndGet();
                            return;
                        } catch (ObjectOptimisticLockingFailureException e) {
                            tentativas++;
                        }
                    }
                    fail("Não conseguiu enviar após 5 tentativas — lock muito agressivo?");
                } catch (Exception e) {
                    fail("Erro inesperado: " + e.getMessage());
                } finally {
                    terminou.countDown();
                }
            });
        }

        largada.countDown();                          // dispara as 2 threads juntas
        assertTrue(terminou.await(15, TimeUnit.SECONDS),
                "As threads não terminaram em 15s");
        pool.shutdown();

        assertEquals(concorrentes, sucessos.get(),
                "Todas as mensagens deveriam ter sido persistidas");

        // Releitura do banco — o contador deve refletir AS DUAS mensagens
        Conversa relida = conversaRepository.findById(conversa.getId()).orElseThrow();
        assertEquals(2, relida.getNaoLidasLoja(),
                "naoLidasLoja deveria ser 2 — sem @Version daria 1");
        assertEquals(2, mensagemRepository.countByConversaId(conversa.getId()));
    }

    // ---- helpers ----

    private Usuario criarUsuario(String email, Papel papel) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID().toString());
        u.setNome("Usuario " + email);
        u.setEmail(email);
        u.setSenha("senha123");
        u.setTelefone(String.format("+55119999%05d", Math.floorMod(email.hashCode(), 100000)));
        u.setPapel(papel);
        u.setAtivo(true);
        u.setEmailVerificado(true);
        return usuarioRepository.saveAndFlush(u);
    }

    private Loja criarLoja(String id, String usuarioId) {
        Loja loja = new Loja();
        loja.setId(id);
        loja.setNome("Loja " + id);
        loja.setUsuarioId(usuarioId);
        loja.setAberto(true);
        DadosOperacionais dadosOp = new DadosOperacionais();
        dadosOp.setEntregaPropria(true);
        dadosOp.setRetiradaNoLocal(true);
        dadosOp.setTaxaEntregaBase(BigDecimal.ZERO);
        dadosOp.setTempoEntregaMin(10);
        dadosOp.setTempoEntregaMax(30);
        loja.setDadosOperacionais(dadosOp);
        loja.setEndereco(new EnderecoLoja("Rua Teste", "123", "Cidade", "SP", "00000-000", "Bairro", null));
        return lojaRepository.saveAndFlush(loja);
    }
}