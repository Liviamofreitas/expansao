# Inventário de dados pessoais — SGDF

**Pendência A12 · requisito LGPD-01 · classificação: Interno — Restrito**

> **GERADO** por `tools/gerar_inventario_lgpd.py` a partir do catálogo. Não editar à mão.
> Regenerado em 2026-09-04.

Este documento é a **parte factual** do RIPD: o que o sistema trata, de que
natureza, onde fica e por quanto tempo. O que ele **não** é: a avaliação de
risco, a base legal por finalidade e a aprovação — essas continuam sendo do
DPO e do jurídico, e são o que falta para fechar A12.

---

## 1. Resumo

| | |
|---|---|
| Tipos documentais no catálogo | 51 |
| Que tratam dado pessoal | **23** |
| Dos quais, dado **sensível** (saúde) | **4** |
| Sem dado pessoal (pessoa jurídica) | 28 |

**Titulares:** colaboradores alocados nos contratos de prestação de serviço
(±890 pessoas, conforme a volumetria do cap. 16) e seus dependentes, quando
houver plano de saúde.

**Finalidade:** comprovar, perante o contratante, o cumprimento das obrigações
trabalhistas, previdenciárias e fiscais que condicionam o faturamento.

**Base legal indicada** (a confirmar pelo jurídico): art. 7º, II (cumprimento de
obrigação legal ou regulatória) e art. 7º, V (execução de contrato), com
art. 11, II, 'a' e 'g' para os dados de saúde. **Não** se apoia em consentimento:
o tratamento é condição do contrato e do vínculo, e consentimento nessa relação
não seria livre.

---

## 2. Categorias de dado por tipo documental

### 2.1 Dado sensível — saúde

Exigem o tratamento mais restritivo: acesso apenas aos papéis com MFA
(cap. 14.4), tarjamento obrigatório antes do book do cliente (LGPD-03) e
cifragem em repouso.

| Código | Nome | Escopo | Onde fica |
|---|---|---|---|
| `ADM.DOCUMENTACAO` | Documentação admissional | Profissional | OwnCloud → repositório interno |
| `BEN.COMPROVANTE_PLANO_SAUDE` | Comprovante de pagamento do plano de saúde | Contrato | OwnCloud → repositório interno |
| `BEN.RELACAO_PLANO_SAUDE` | Relação de beneficiários do plano de saúde | Profissional | OwnCloud → repositório interno |
| `RES.ASO_DEMISSIONAL` | ASO demissional | Profissional | OwnCloud → repositório interno |

### 2.2 Dado pessoal comum

| Código | Nome | Escopo | Categorias prováveis |
|---|---|---|---|
| `BEN.RELACAO_VA_VR` | Relação de vale-alimentação/refeição | Profissional | Nome, matrícula, adesão a benefício |
| `BEN.RELACAO_VT` | Relação de vale-transporte | Profissional | Nome, matrícula, adesão a benefício |
| `BEN.TERMO_NAO_ADESAO_VT` | Termo de não adesão ao VT | Profissional | Nome, matrícula, adesão a benefício |
| `DEC.COMPROVANTE_PG_13` | Comprovante de pagamento do 13º salário | Contrato | Nome, matrícula, remuneração |
| `DEC.FOPAG_13` | Folha de pagamento do 13º salário | Contrato | Nome, matrícula, remuneração |
| `FER.AVISO` | Aviso de férias | Profissional | Nome, matrícula, período aquisitivo, remuneração |
| `FER.COMPROVANTE_PG` | Comprovante de pagamento de férias | Profissional | Nome, matrícula, período aquisitivo, remuneração |
| `FER.RECIBO` | Recibo de férias | Profissional | Nome, matrícula, período aquisitivo, remuneração |
| `FGT.COMPROVANTE_PG_GRRF` | Comprovante de pagamento da GRRF | Profissional | CPF, remuneração, recolhimento individualizado |
| `FGT.EXTRATO` | Extrato analítico do FGTS por trabalhador | Profissional | CPF, remuneração, recolhimento individualizado |
| `FGT.GUIA_RESCISORIA` | Guia FGTS rescisória (GRRF) | Profissional | CPF, remuneração, recolhimento individualizado |
| `FGT.RELATORIO_DIGITAL` | Relatórios do FGTS Digital (individualização | Profissional | CPF, remuneração, recolhimento individualizado |
| `FOL.COMPROVANTE_PG` | Comprovante de pagamento da folha | Contrato | Nome, CPF, matrícula, remuneração, descontos |
| `FOL.CONTRACHEQUE` | Contracheque / recibo de pagamento | Profissional | Nome, CPF, matrícula, remuneração, descontos |
| `FOL.FOPAG` | Folha de pagamento analítica | Contrato | Nome, CPF, matrícula, remuneração, descontos |
| `OPE.FOLHA_PONTO` | Folha de ponto / ponto eletrônico | Profissional | Nome, matrícula, jornada |
| `RES.AVISO_PREVIO` | Aviso prévio | Profissional | Nome, CPF, datas de vínculo, verbas rescisórias |
| `RES.COMPROVANTE_PG` | Comprovante de pagamento da rescisão | Profissional | Nome, CPF, datas de vínculo, verbas rescisórias |
| `RES.TRCT` | TRCT / Termo de rescisão | Profissional | Nome, CPF, datas de vínculo, verbas rescisórias |

---

## 3. Onde o dado pessoal vive no sistema

| Local | O que guarda | Proteção em vigor |
|---|---|---|
| `profissional.cpf_cifrado` | CPF | AES-256-GCM, chave no cofre (SEC-02) |
| `profissional.cpf_hash` | CPF, para busca e unicidade | HMAC-SHA256 com chave no cofre — hash simples permitiria enumerar o espaço de 10¹¹ CPFs |
| `profissional.nome` | Nome | Acesso por papel; mascarado na UI conforme o papel |
| `documento` (binário no bucket) | O documento em si | Cifragem em repouso; URL assinada ≤ 15 min; acesso registrado (SEC-04) |
| `campo_extraido.valor` | Valores extraídos do documento | Mesmo controle de acesso do documento de origem |
| `book` (bucket de evidências) | Cópia publicada | Tarjamento antes da cópia para o cliente (LGPD-03) |
| `log_auditoria` | Quem acessou o quê | Append-only; contém identificador do ator, não dado do titular |
| `notificacao` | Envio da régua | **Não** contém dado pessoal nem anexo — só link autenticado (cap. 11.2) |

---

## 4. Retenção — o ponto aberto mais grave

> **Decisão registrada em A08: retenção sem tempo determinado.**

O sistema a implementa como `LEGAL_HOLD`: o book fica protegido
indefinidamente, mas de forma **reversível** por papel autorizado. O modo
`COMPLIANCE`, que é irreversível até a data — nem a conta raiz reduz —,
existe mas não é o padrão.

**A ressalva que o DPO precisa avaliar.** Guardar dado pessoal por prazo
indeterminado é tratamento sem termo final definido, o que tensiona:

- **art. 6º, III (necessidade)** — o tratamento deve limitar-se ao mínimo
  necessário para a finalidade;
- **art. 15, I e art. 16** — o dado deve ser eliminado quando a finalidade
  se exaure, salvo hipóteses legais de guarda;
- **art. 18, IV e VI** — o titular pode pedir anonimização ou eliminação, e
  um book sob retenção irreversível impede o atendimento.

A finalidade aqui (prova de regularidade trabalhista) **tem** termo natural:
a prescrição quinquenal do art. 7º, XXIX da Constituição. É esse o prazo que
a tabela de temporalidade tende a fixar.

**Enquanto a temporalidade não existir, `LEGAL_HOLD` é a escolha certa** —
protege a evidência sem tornar a decisão irreversível. Migrar para
`COMPLIANCE` deve acontecer **uma única vez**, depois da tabela aprovada,
porque o caminho não tem volta.

---

## 5. O que falta para fechar A12

| Item | Responsável |
|---|---|
| Confirmar a classificação de sigilo dos 51 tipos (achado **E-05**; hoje derivada, `CONFIRMADO=NAO`) | Gestão de Contratos + DAF |
| Confirmar a base legal por finalidade | Jurídico |
| Avaliação de risco e medidas de mitigação (o RIPD propriamente) | DPO |
| Tabela de temporalidade (**A08**) e migração para `COMPLIANCE` | Jurídico |
| Definir o fluxo de atendimento a pedido de titular sobre documento já publicado em book | DPO + Jurídico |
| Contrato de operador com o contratante, quando aplicável | Jurídico |

O último item merece atenção: ao publicar o book para o contratante, a
Engesoftware transfere dado pessoal de seus colaboradores a um terceiro. A
relação entre controladores precisa estar formalizada, e é o tarjamento
(**F2-07**) que limita o que efetivamente sai.
