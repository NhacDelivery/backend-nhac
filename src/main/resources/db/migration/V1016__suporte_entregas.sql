CREATE TABLE tb_suporte_entrega (
 id VARCHAR(50) PRIMARY KEY,
 pedido_id VARCHAR(50) NOT NULL,
 usuario_id VARCHAR(50) NOT NULL,
 motivo VARCHAR(30) NOT NULL,
 descricao VARCHAR(2000) NOT NULL,
 etapa VARCHAR(30) NOT NULL,
 status VARCHAR(30) NOT NULL,
 resposta VARCHAR(2000),
 criado_em TIMESTAMP(6) NOT NULL,
 respondido_em TIMESTAMP(6),
 CONSTRAINT fk_suporte_pedido FOREIGN KEY(pedido_id) REFERENCES tb_pedidos(id),
 CONSTRAINT fk_suporte_usuario FOREIGN KEY(usuario_id) REFERENCES tb_usuarios(id),
 INDEX idx_suporte_usuario_pedido(usuario_id,pedido_id),
 INDEX idx_suporte_status(status,criado_em)
);
