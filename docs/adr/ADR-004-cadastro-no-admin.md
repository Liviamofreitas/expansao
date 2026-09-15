# ADR-004 · O cadastro concentra-se no ADMIN_SISTEMA

**Status:** aceita · **Data:** 2026-09-15
**Decisores:** área de negócio · **Consultados:** —

## Contexto

O cap. 15.1 atribui ao **CURADOR_MATRIZ** "cadastro de contratos/regras; publicar
versão da matriz; publicar e enviar book", e ao **ADMIN_SISTEMA** a configuração
do sistema. Era assim que o `Autorizador` estava: `CADASTRAR` no curador,
`CONFIGURAR_SISTEMA` no admin.

Ao especificar a funcionalidade de acrescentar e excluir documentos do checklist,
a área determinou que **só o admin** pode cadastrar documentos e clientes.

## Decisão

`Permissao.CADASTRAR` — que cobre cliente, contrato-serviço, tipo documental e,
a partir da nova funcionalidade, **regra de reconhecimento** — passa a ser
**exclusiva do ADMIN_SISTEMA**. Sai do CURADOR_MATRIZ.

`Permissao.PUBLICAR_MATRIZ` **não** se move: segue com o CURADOR_MATRIZ.

## Razões

É decisão organizacional declarada, não leitura do capítulo. A área é quem
responde pelo desenho de papéis; o capítulo é referência, e divergir dele com a
divergência registrada é legítimo — divergir em silêncio não é.

## Risco aceito

**A mesma pessoa passa a definir o que o sistema cobra e a administrar o sistema
que cobra.** O ADMIN_SISTEMA já tinha `CONFIGURAR_SISTEMA` e
`VER_CONTEUDO_DOCUMENTO`; agora cria o tipo documental e a regra que o
reconhece. Num cenário adverso, o mesmo ator poderia criar um tipo, escrever uma
regra que não reconhece nada e administrar a configuração que esconderia o
efeito.

O risco é **aceito pela área** e mitigado por três coisas que continuam de pé:

| Mitigação | Por quê |
|---|---|
| **Publicar a matriz continua com o CURADOR_MATRIZ** | Cadastrar um tipo NÃO o coloca em contrato nenhum. Para ser cobrado, alguém tem de publicar a versão da matriz. Criar e exigir seguem em mãos diferentes |
| **A trilha é append-only** | Todo cadastro entra em `log_auditoria`, que tem `REVOKE` e `RULE ... DO INSTEAD NOTHING`. O admin não consegue apagar o registro do que cadastrou |
| **A regra é verificada contra exemplos** | Uma regra que não reconhece o próprio exemplo é recusada no cadastro; uma que rouba o exemplo de outro tipo também. A verificação não depende de quem cadastrou |

## Consequências

- O CURADOR_MATRIZ perde a capacidade de cadastrar. Se alguém acumular os dois
  papéis, a segregação da linha 1 da tabela acima deixa de existir de fato — e é
  isso que a recertificação de acesso (SEC-06) precisa olhar.
- Quatro asserções em `TestesDeAutorizacao` registram a decisão de forma
  executável. Devolver `CADASTRAR` ao curador sem revisar este ADR derruba duas
  delas.
- O ADMIN_SISTEMA passa a precisar de MFA pelos mesmos critérios dos papéis que
  veem escopo profissional (item 1.4 do plano de implantação): ele já via
  conteúdo de documento, e agora escreve regra.
