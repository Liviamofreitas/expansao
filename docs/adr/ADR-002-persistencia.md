# ADR-002 · JDBC direto, sem ORM

**Status:** aceito
**Contexto:** história de persistência da fase 1a; ADR-001 já fixou Java 21.

## Decisão

A camada de persistência usa **JDBC direto** com SQL escrito à mão, sem ORM
(JPA/Hibernate) e sem geração de esquema a partir de código.

## Por quê

**O esquema impõe regras de negócio, e não só forma.** As migrações V001–V008
carregam:

- `RULE ... DO INSTEAD NOTHING` que torna `log_auditoria` append-only;
- `CHECK` que impede reprovação sem motivo (`validacao_reprovada_tem_motivo`);
- `CHECK` que força modo ALERTA quando não há tolerância cadastrada
  (`regra_conc_sem_tolerancia_nao_bloqueia`);
- `CHECK` que impede aprovador igual a solicitante (`excecao_sod`);
- função `IMMUTABLE` usada em `CHECK` para validar campos essenciais;
- índices parciais e `UNIQUE NULLS NOT DISTINCT`.

Nada disso é expressável em anotações de mapeamento. Um ORM com `ddl-auto` — em
qualquer modo que não seja `none` — desfaz parte disso na primeira execução, em
silêncio. E mesmo com `ddl-auto: none`, a tentação de mover a regra para o código
"porque o ORM não a enxerga" é o caminho por onde a garantia se perde.

**O banco é a fonte da verdade estrutural; o código a consome.** É a mesma razão
pela qual as restrições foram escritas no banco em vez de só no serviço: elas são
a rede de segurança para o caso de um caminho de código esquecer a checagem
(comentário da própria `excecao_sod`).

**O domínio já é independente de infraestrutura.** As classes de `extracao`,
`documento`, `validacao`, `classificacao` e `conciliacao` não conhecem banco:
recebem documentos e devolvem vereditos. Um ORM as obrigaria a virar entidades
com identidade gerenciada, anotações e construtor sem argumentos — perdendo os
`record` imutáveis e as validações de construtor que hoje impedem estado
inválido de existir.

## Consequências

**Aceitas.** SQL à mão é mais verboso e não tem cache de primeiro nível. Cada
repositório precisa mapear `ResultSet` para `record` explicitamente.

**Mitigação.** Os repositórios são finos e testados contra o PostgreSQL real —
não contra um banco em memória, que não teria as restrições que importam.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| JPA/Hibernate | Desfaz garantias do esquema; obriga o domínio a virar entidade |
| jOOQ | Boa aderência ao SQL, mas exige geração de código a partir do banco no build — dependência de ferramenta que o ambiente-alvo (D-06, mirror restrito) pode não suportar |
| Spring Data JDBC | Menos invasivo que JPA, mas ainda impõe convenções de agregado que não correspondem ao modelo |
