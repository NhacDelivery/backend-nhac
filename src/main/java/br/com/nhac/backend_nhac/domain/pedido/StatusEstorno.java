package br.com.nhac.backend_nhac.domain.pedido;

/** ENVIANDO é persistido antes de qualquer POST; nunca reenviar automaticamente. */
public enum StatusEstorno { SOLICITADO, ENVIANDO, PROCESSANDO, CONCLUIDO, REVISAO }
