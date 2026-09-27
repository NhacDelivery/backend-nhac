package br.com.nhac.backend_nhac.domain.chat.dto;

import java.time.Instant;

import br.com.nhac.backend_nhac.domain.chat.Conversa;
import br.com.nhac.backend_nhac.domain.chat.Mensagem;
import br.com.nhac.backend_nhac.domain.chat.ParticipanteTipo;
import br.com.nhac.backend_nhac.domain.chat.RemetenteTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public class ChatDTOs {

    /**
     * Resumo de conversa para a listagem no painel do lojista.
     * participanteTipo (V039) deixa explícito se a linha é uma conversa com
     * cliente ou com entregador — o painel usa isso pra decidir ícone/rótulo
     * quando lista os dois canais juntos.
     */
    public record ConversaResumoDTO(
            String id,
            String clienteId,
            String clienteNome,
            ParticipanteTipo participanteTipo,
            String ultimaMensagemPreview,
            Instant ultimaMensagemEm,
            int naoLidas
    ) {
        public ConversaResumoDTO(Conversa conversa, String clienteNome) {
            this(
                    conversa.getId(),
                    conversa.getClienteId(),
                    clienteNome,
                    conversa.getParticipanteTipo(),
                    conversa.getUltimaMensagemPreview(),
                    conversa.getUltimaMensagemEm(),
                    conversa.getNaoLidasLoja()
            );
        }
    }

    /** Uma mensagem, tanto no histórico REST quanto transmitida via WebSocket. */
    public record MensagemDTO(
            String id,
            String conversaId,
            RemetenteTipo remetenteTipo,
            String remetenteUsuarioId,
            String conteudo,
            Instant enviadaEm
    ) {
        public MensagemDTO(Mensagem mensagem) {
            this(
                    mensagem.getId(),
                    mensagem.getConversa().getId(),
                    mensagem.getRemetenteTipo(),
                    mensagem.getRemetenteUsuarioId(),
                    mensagem.getConteudo(),
                    mensagem.getEnviadaEm()
            );
        }
    }

    /**
     * Payload enviado pelo cliente WebSocket (STOMP) para
     * /app/conversas/{id}/enviar.
     *
     * Limite de 4000 caracteres: previne abuso (mandar MB de texto) e casa
     * com o que a UI mostra em preview de conversa.
     */
    public record EnviarMensagemDTO(
            @NotBlank(message = "Mensagem não pode ser vazia")
            @Size(max = 4000, message = "Mensagem muito longa (máx. 4000 caracteres)")
            String conteudo,
            @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
            String clientMessageId
    ) {
        public EnviarMensagemDTO(String conteudo) { this(conteudo, null); }
    }
}
