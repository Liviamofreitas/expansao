# SGDF — Sistema de Gestão Documental do Faturamento

Automatiza a conferência documental do faturamento por medição na **Engesoftware Tecnologia S.A.**: a cada competência, para cada contrato-serviço, o sistema materializa a lista de documentos exigidos, varre os repositórios existentes, identifica cada arquivo **pelo conteúdo**, valida, concilia documentos com seus comprovantes, publica o conjunto aprovado como *book* de faturamento em repositório imutável e comunica as áreas — cobrando o que falta ou confirmando o fechamento.

> **Estado:** documentação V1 consolidada, base para início do desenvolvimento.
> **Fases 0 e 1a liberadas.** Fase 1b condicionada à pendência **A05** (sistema de folha e layout).
> **Classificação da informação:** Interno — Restrito.

---

## O que há neste repositório

| Caminho | Conteúdo |
|---|---|
| [`docs/SGDF_Documentacao_Desenvolvimento_V1.docx`](docs/SGDF_Documentacao_Desenvolvimento_V1.docx) | **Documento normativo.** 23 capítulos. É a fonte oficial. |
| [`docs/SGDF_Documentacao_Desenvolvimento_V1.md`](docs/SGDF_Documentacao_Desenvolvimento_V1.md) | Conversão fiel do `.docx` para Markdown — leitura no navegador, busca e **diff entre versões**. Cópia de conveniência: em caso de divergência, o `.docx` prevalece. |
| [`docs/anexos/Anexo1_Diagnostico_Checklist_Faturamento.xlsx`](docs/anexos/Anexo1_Diagnostico_Checklist_Faturamento.xlsx) | **Anexo 1** — catálogo canônico (51 tipos), matriz normalizada (176 exigências), divergências e campos a criar. Fonte da carga inicial. |
| [`docs/anexos/Anexo2_SGDF_Layout_Prototipo.html`](docs/anexos/Anexo2_SGDF_Layout_Prototipo.html) | **Anexo 2** — protótipo navegável das 5 telas e tokens de design da identidade Engesoftware. Abrir direto no navegador. |
| [`docs/ERRATA-V1.md`](docs/ERRATA-V1.md) | **Ler antes de codificar.** Quatro inconsistências verificadas entre o documento e o Anexo 1, uma delas crítica. |
| [`dados/*.csv`](dados/) | As 8 abas do Anexo 1 exportadas em CSV UTF-8 — insumo direto da *migration* de carga inicial e revisável em *code review*. Gerados a partir do `.xlsx`; **não editar à mão**. |

---

## Ordem de leitura sugerida

1. **README** (este arquivo) — mapa do pacote.
2. **`docs/ERRATA-V1.md`** — o que está inconsistente e ainda não foi decidido.
3. **Documentação, caps. 1–3** — escopo, glossário e as 14 decisões/premissas com efeito direto no código (D-01 a R-04).
4. **Cap. 4–6** — arquitetura de referência, modelo de dados e as máquinas de estado da exigência e do ciclo.
5. **Cap. 7–9** — o motor: exigibilidade, prazo estruturado, defasagem, reconhecimento determinístico, validações V1–V7 e conciliação.
6. **Cap. 18** — backlog, em ordem de dependência. É por onde a sprint 0 começa.
7. **Anexo 2** no navegador, junto com o cap. 12 — as telas são requisito normativo, não sugestão.

---

## Regra de leitura (vale para todo o pacote)

- Tudo marcado como **"padrão"** ou **"parâmetro"** é valor inicial ajustável em cadastro.
- Tudo marcado como **"exigido"** ou **"obrigatório"** é requisito de aceitação.
- Dúvida de interpretação se resolve **com a área demandante, antes de codificar** — nunca por suposição silenciosa.

---

## Princípios de projeto (cap. 1, em ordem de precedência)

1. O sistema **nunca trava o faturamento por falta de configuração própria**: tipo sem regra de reconhecimento cai em conferência manual; regra sem tolerância opera em modo alerta.
2. Todo comportamento variável é **metadado editável**, nunca código.
3. Toda decisão automática é **determinística, auditável e explicável** — sem classificadores estatísticos na decisão de conformidade.
4. A identificação usa o **conteúdo** do documento; nome de arquivo e pasta são apenas reforço de confiança.
5. A varredura é **somente-leitura na origem**; a escrita ocorre apenas no repositório de evidências.
6. **Bloqueio sempre explicado**: toda ação desabilitada exibe o motivo.
7. O sistema confere completude e coerência documental; **o ateste técnico da medição permanece humano**.

---

## Perfis de acesso (cap. 15.1)

`ADMIN_SISTEMA` · `CURADOR_MATRIZ` · `PUBLICADOR_AP` · `PUBLICADOR_FIN` · `GESTOR_CONTRATO` · `APROVADOR_DAF` · `AUDITORIA`, mais duas identidades de serviço com escopos disjuntos (`svc-sgdf-leitura`, `svc-sgdf-publica`).

Segregação de funções imposta no serviço: **quem solicita exceção não aprova a própria exceção**; `ADMIN_SISTEMA` configura mas não vê conteúdo de documento de escopo profissional.

---

## Antes do kickoff — o que precisa ir na frente

Estas pendências destravam o resto; as demais correm em paralelo ao desenvolvimento.

| ID | Pendência | Bloqueia | Responsável |
|---|---|---|---|
| **A03** | BNB e TJCE usam a mesma matriz nas duas modalidades? | Fase 0 — verificação prévia urgente (≈30 min de checagem; risco de responsabilidade subsidiária) | Gestão de Contratos |
| **A13** | Stack de desenvolvimento e infraestrutura alocada | Sprint 0 | TI |
| **A05** | Sistema de folha e layout de extração | Início da fase 1b (único bloqueio real) | TI e AP |
| **E-01** | Colisão da numeração R01–R12 entre documento e Anexo 1 | Escrita das histórias de conciliação | Área demandante + arquitetura |

A lista completa das 13 pendências está no cap. 23 da documentação; as inconsistências (E-01 a E-04) estão na errata.

---

## Regenerar os CSV a partir do Anexo 1

Os arquivos em `dados/` são derivados. Ao receber uma nova versão do `.xlsx`, substitua o anexo e rode:

```bash
pip install openpyxl
python3 tools/exportar_anexo1.py
```

O diff dos CSV mostra exatamente o que mudou no catálogo e na matriz entre as versões — que é o controle de mudança da carga inicial exigido pelo cap. 17 (*seed* como migration de dados auditável).
