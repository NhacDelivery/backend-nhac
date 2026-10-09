package br.com.nhac.backend_nhac.domain.chat;

import br.com.nhac.backend_nhac.domain.chat.dto.ChatDTOs.MensagemDTO;

/** Snapshot imutável para publicar somente depois do commit, sem acessar entidades lazy. */
public record MensagemEnviadaEvent(MensagemDTO mensagem) {}
