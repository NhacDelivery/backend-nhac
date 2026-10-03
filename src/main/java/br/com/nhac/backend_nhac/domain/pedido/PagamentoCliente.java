package br.com.nhac.backend_nhac.domain.pedido;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "tb_pagamento_clientes")
@Getter @Setter @NoArgsConstructor
public class PagamentoCliente {
    @Id @Column(length = 64) private String id;
    @Column(name = "customer_id", nullable = false, length = 80) private String customerId;
    public PagamentoCliente(String id, String customerId) { this.id = id; this.customerId = customerId; }
}
