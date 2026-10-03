#!/usr/bin/env python3
"""Add synthetic catalogue only to the two explicitly isolated perf databases."""
import subprocess
for database in ('nhac_perf','nhac_perf_baseline'):
    statements=['USE '+database+';']
    for i in range(300):
        pid=f'perf-product-{i:03}'
        statements.append(f"""INSERT IGNORE INTO tb_produtos
        (id,loja_id,nome,preco,categoria_menu,is_ativo,estoque,percentual_desconto,peso,descricao,imagem_url,criado_em)
        SELECT '{pid}',loja_id,'Performance product {i:03}',preco,categoria_menu,is_ativo,1000,10,peso,descricao,imagem_url,criado_em
        FROM tb_produtos WHERE id='e2e-produto-001';""")
        for g in range(2):
            gid=f'perf-group-{i:03}-{g}'
            statements.append(f"INSERT IGNORE INTO tb_grupo_adicional (id,produto_id,nome,obrigatorio,minimo,maximo) VALUES ('{gid}','{pid}','Complementos {g}',false,0,3);")
            for a in range(3):
                statements.append(f"INSERT IGNORE INTO tb_item_adicional (id,grupo_adicional_id,nome,preco) VALUES ('perf-extra-{i:03}-{g}-{a}','{gid}','Adicional {a}',2.5);")
    subprocess.run(['docker','exec','-i','nhac-perf-isolado','mariadb','-uroot','-pnhac_perf_root_local'],
                   input='\n'.join(statements), text=True,check=True)
print('Synthetic dataset ready in both isolated databases.')
