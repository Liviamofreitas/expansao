# ADR-005 — `pasta_origem` e `documento.caminho` são relativos à base WebDAV

**Status:** aceita
**Data:** 2026-09-15
**Contexto da decisão:** pergunta do responsável pelo sistema —
*"se eu apontar a varredura para uma pasta hoje, posso trocar depois? vamos
atualizar a versão da cloud em algumas semanas e eu não quero perder o trabalho
feito até hoje."*

---

## Contexto

Numa OwnCloud/Nextcloud o WebDAV não vive na raiz do host. Ele vive sob

```
/remote.php/dav/files/<conta>
```

Esse trecho é **infraestrutura**, não endereço do documento: muda quando a
nuvem é atualizada, quando a conta de serviço é trocada, quando o provedor muda.
O caminho do documento — `/Departamento de Pessoal/FATURAMENTO/...` — é o que o
negócio conhece e é estável.

O código anterior misturava os dois de um jeito que só piorava com o tempo.
`ClienteWebDav.alvo()` montava a URI pelas partes:

```java
new URI(base.getScheme(), null, base.getHost(), base.getPort(), caminho, null, null)
```

O caminho da base **não entrava**. Era descartado em silêncio. A consequência
prática: para a varredura funcionar, o prefixo DAV tinha de ser embutido no
`pasta_origem` de cada contrato — e a partir daí ele viajaria junto para o
`documento.caminho` de cada documento ingerido.

Duas coisas davam errado com isso, e a segunda é grave:

1. configurar `SGDF_WEBDAV_BASE` com caminho produzia GET na URL errada, 404, e
   o erro era reportado como *falha de coleta do arquivo* — apontando para o
   documento em vez de para a configuração;
2. o dia da atualização da nuvem invalidaria o `pasta_origem` de **todo**
   contrato e o `caminho` de **todo** documento já registrado. Isso é uma
   migração de dados sobre linhas que a trilha do cap. 16 já carimbou, num
   esquema que é append-only por construção em várias das tabelas envolvidas.

O carregamento de seed já assumia a forma certa (`/BNB - 482023`), e a
`IMPLANTACAO-SAD` documentava o contorno. Ou seja: a base estava dividida entre
duas convenções, e uma delas só funcionava por acaso.

## Decisão

**`SGDF_WEBDAV_BASE` carrega esquema, host, porta e o caminho-raiz do WebDAV.
`pasta_origem` e `documento.caminho` são relativos a ela.**

- `ClienteWebDav` calcula o prefixo a partir de `base.getRawPath()`,
  canonicalizado e decodificado — **decodificado** porque é assim que o
  construtor de sete argumentos de `URI` o quer, e porque é no espaço decodificado
  que `CaminhoRemoto.canonicalizar` devolve o href, onde a comparação acontece.
  Os dois lados no mesmo espaço, ou a comparação erraria em toda base com espaço
  ou acento;
- na saída, o prefixo é prependido ao caminho pedido;
- na volta, o prefixo é removido do href, **exigindo fronteira de segmento**. Com
  prefixo `/dav/files/livia`, um `startsWith` cru aceitaria
  `/dav/files/liviaOUTRA/x.pdf` e o relativizaria para `OUTRA/x.pdf` — caminho de
  **outra conta** entrando no sistema como se fosse desta, e sem barra inicial, o
  que ainda quebraria a contenção de raiz logo adiante;
- href **fora** do prefixo volta inteiro e absoluto, de propósito: ele então não
  está dentro da raiz relativa do contrato, e a `Varredura` o registra como
  *"fora da raiz"* — o controle do cap. 14.1 que já existe e já é testado.
  Descartá-lo aqui esconderia do painel exatamente o que alguém precisa ver.

## Consequências

**A favor.** Trocar de nuvem, de conta de serviço ou de provedor passa a ser uma
mudança de `SGDF_WEBDAV_BASE`. Nada no banco se move. A resposta à pergunta que
originou esta ADR passa a ser *sim, e sem perder nada*.

**Contra.** É uma mudança **incompatível** com qualquer ambiente que já tenha
configurado a base só com o host e embutido o prefixo DAV no `pasta_origem`: os
dois têm de mudar juntos, senão a requisição sai com o prefixo duplicado e o
PROPFIND devolve 404. Hoje isso não atinge dado nenhum — **nenhum documento real
foi ingerido ainda**, e é precisamente por isso que a mudança acontece agora e
não depois. Registrado na `IMPLANTACAO-SAD` § 6 como aviso.

**O que esta ADR NÃO resolve.** Se a *pasta* mudar de lugar dentro da nuvem — e
não apenas a raiz DAV —, o `pasta_origem` do contrato continua tendo de mudar.
Não existe hoje caminho auditado para alterá-lo pela aplicação; a alteração é
por SQL. É uma lacuna conhecida e está no backlog como **recomendação
obrigatória antes de a varredura real ser ligada em produção**.

## Verificação

Contra o servidor WebDAV simulado, o que prova as duas pontas de uma vez — a
requisição sai com o prefixo (senão viria 404) e o caminho volta sem ele (senão
a contenção de raiz reprovaria o próprio arquivo). Quebras deliberadas:

| quebra | assertivas que caíram |
|---|---|
| requisição sai sem o prefixo (comportamento antigo) | 2 |
| href volta com o prefixo (não relativiza) | 2 |
| `startsWith` cru, sem fronteira de segmento | 1 |

Mais o controle explícito: **sem** o prefixo, o mesmo servidor não entrega nada.
Sem esse controle, a asserção principal passaria igual se o prefixo fosse
ignorado e o servidor respondesse de qualquer jeito.
