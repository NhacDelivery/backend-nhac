package br.com.nhac.backend_nhac.domain.chat;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMensagemCommitIT extends AbstractIntegrationTest {
    @Autowired private ChatService service;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private MensagemRepository mensagens;
    @Autowired private ConversaRepository conversas;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private ChatMensagemPublisher publisher;
    private Usuario a;
    private String conversaId;

    @BeforeEach
    void dados() {
        a = usuarios.saveAndFlush(Usuario.builder().id("commit-a").nome("A")
                .email("commit-a@teste.com").telefone("11900000001").papel(Papel.CLIENTE).build());
        usuarios.saveAndFlush(Usuario.builder().id("commit-b").nome("B")
                .email("commit-b@teste.com").telefone("11900000002").papel(Papel.CLIENTE).build());
        conversaId = service.obterOuCriarConversaEntreClientes("commit-b", a).getId();
        clearInvocations(publisher);
    }

    @Test
    void publicaSomenteDepoisDoCommitDaTransacaoExterna() {
        var dto = new TransactionTemplate(transactionManager).execute(status -> {
            var mensagem = service.enviarMensagem(conversaId, a, "Após commit");
            verifyNoInteractions(publisher);
            return mensagem;
        });
        assertNotNull(dto);
        verify(publisher, times(1)).publicar(new MensagemEnviadaEvent(dto));
        assertTrue(mensagens.existsById(dto.id()));
    }

    @Test
    void rollbackExternoNaoPublicaNemPersisteMensagemOuContador() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.enviarMensagem(conversaId, a, "Não publicar");
            verifyNoInteractions(publisher);
            status.setRollbackOnly();
        });
        verifyNoInteractions(publisher);
        assertEquals(0, mensagens.countByConversaId(conversaId));
        assertEquals(0, conversas.findById(conversaId).orElseThrow().getNaoLidasLoja());
    }

    @Test
    void falhaNoBancoDescartaPublicacaoAgendada() {
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.enviarMensagem(conversaId, a, "Commit que falhará");
            Conversa conversa = conversas.findById(conversaId).orElseThrow();
            // Conteúdo obrigatório ausente faz a transação inteira falhar no flush.
            mensagens.saveAndFlush(new Mensagem("msg_invalida", conversa, RemetenteTipo.CLIENTE, a.getId(), null));
        }));
        verifyNoInteractions(publisher);
        assertEquals(0, mensagens.countByConversaId(conversaId));
    }
}
