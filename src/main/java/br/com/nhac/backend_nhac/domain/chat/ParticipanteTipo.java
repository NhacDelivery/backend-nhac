package br.com.nhac.backend_nhac.domain.chat;

/**
 * De que lado do par (loja, participante) uma Conversa é. Introduzido na V039
 * para permitir chat loja↔entregador sem duplicar toda a estrutura de
 * conversas/mensagens: o campo cliente_id (mantido por compatibilidade de
 * nome de coluna) passa a guardar o id do CLIENTE ou do ENTREGADOR, a
 * depender deste campo.
 */
public enum ParticipanteTipo {
    CLIENTE,
    ENTREGADOR,
    ENTREGA
}
