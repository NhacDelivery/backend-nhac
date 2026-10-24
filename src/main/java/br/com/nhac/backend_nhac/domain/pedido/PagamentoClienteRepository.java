package br.com.nhac.backend_nhac.domain.pedido;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PagamentoClienteRepository extends JpaRepository<PagamentoCliente, String> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @org.springframework.data.jpa.repository.Query(value = "insert into tb_pagamento_clientes (id, customer_id) values (:id, '__CRIACAO_PENDENTE__')", nativeQuery = true)
    int reservarCriacao(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @org.springframework.data.jpa.repository.Query("update PagamentoCliente c set c.customerId = :customerId where c.id = :id and c.customerId = '__CRIACAO_PENDENTE__'")
    int concluirCriacao(@org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("customerId") String customerId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @org.springframework.data.jpa.repository.Query("delete from PagamentoCliente c where c.id = :id and c.customerId = '__CRIACAO_PENDENTE__'")
    int liberarRejeicao(@org.springframework.data.repository.query.Param("id") String id);
}
