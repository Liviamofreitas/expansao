# SGDF — Sistema de Gestão Documental do Faturamento

**Documentação de Desenvolvimento — pacote consolidado**

> Conversão fiel para Markdown de `SGDF_Documentacao_Desenvolvimento_V1.docx`,
> para leitura no navegador, busca e diff entre versões.
> **Em caso de divergência, o `.docx` prevalece.**
> Regenerar após qualquer alteração do original.
> Inconsistências verificadas contra o Anexo 1: ver [`ERRATA-V1.md`](ERRATA-V1.md).

---

| Campo | Conteúdo |
|---|---|
| Versão | V 1.0 — base para início do desenvolvimento (fases 0 e 1a liberadas; 1b condicionada à pendência A05) |
| Área demandante | Diretoria Administrativa e Financeira — Engesoftware Tecnologia S.A. |
| Classificação | Interno — Restrito |
| Documentos anexos | Anexo 1: Diagnostico_Checklist_Faturamento_Engesoftware.xlsx (catálogo canônico, matriz normalizada, divergências) Anexo 2: SGDF_Layout_Prototipo.html (protótipo navegável e tokens de design) |
| Base de análise | 176 exigências de 8 checklists; 2 políticas de faturamento; estrutura real do OwnCloud (15 pastas de contrato); identidade visual oficial |
| Público | Equipe de desenvolvimento, arquitetura e segurança da informação |


# 1. Visão geral
O SGDF automatiza a conferência documental do faturamento por medição: a cada competência, para cada contrato, o sistema materializa a lista de documentos exigidos, varre os repositórios existentes, identifica cada arquivo pelo conteúdo, valida, concilia documentos com seus comprovantes de pagamento, publica o conjunto aprovado como book de faturamento em repositório imutável e comunica as áreas — cobrando o que falta ou confirmando o fechamento.
Princípios de projeto, em ordem de precedência:
1. O sistema nunca trava o faturamento por falta de configuração própria: tipo sem regra de reconhecimento cai em conferência manual; regra sem tolerância opera em modo alerta.
2. Todo comportamento variável é metadado editável (módulo Cadastro), nunca código.
3. Toda decisão automática é determinística, auditável e explicável — sem classificadores estatísticos na decisão de conformidade.
4. A identificação usa o conteúdo do documento; nome de arquivo e pasta são apenas reforço de confiança.
5. A varredura é somente-leitura na origem; a escrita ocorre apenas no repositório de evidências.
6. Bloqueio sempre explicado: toda ação desabilitada exibe o motivo.
7. O sistema confere completude e coerência documental; o ateste técnico da medição permanece humano.

## 1.1 Fora do escopo
Emissão de NF e escrituração fiscal; apuração de folha e encargos; julgamento da medição, SLA e penalidades; envio a portais externos de clientes (fase futura).

# 2. Glossário
| Termo | Definição |
|---|---|
| Competência | Mês de referência da prestação do serviço faturado (formato AAAA-MM). |
| Ciclo | Instância do processo de conferência: um contrato-serviço × uma competência. |
| Contrato-serviço | Unidade de faturamento: número do contrato + serviço/modalidade. Ex.: CAIXA-09705.2025; BNB-482023-OUT e BNB-482023-SUS são contratos-serviço distintos. |
| Exigência | Obrigação documental materializada num ciclo: tipo documental × evento × (profissional, quando individual). |
| Tipo documental | Entrada do catálogo canônico (51 tipos na carga inicial), com código no formato FAM.NOME. |
| Alias | Nomenclatura alternativa reconhecida para um tipo (69 na carga inicial, herdadas do legado). |
| Defasagem | Relação entre a competência do documento e a do ciclo: M, M-1, vigência na data da NF, ou data do evento. |
| Book | Conjunto versionado, numerado e com hash das evidências publicadas de um ciclo — o artefato entregue ao cliente. |
| Bloco corporativo | Documentos por CNPJ (certidões, guias e comprovantes de encargos), coletados uma vez por competência e replicados em todos os books. |
| Triagem | Fila de decisão humana para documentos identificados com confiança abaixo do limiar. |
| Modo sombra | Operação completa sem envio de notificações e sem efeito sobre o processo real, para calibragem. |
| AP | Administração de Pessoas (também referida como DP nos checklists legados). |


# 3. Decisões, premissas e restrições
| ID | Decisão / premissa | Efeito no desenvolvimento |
|---|---|---|
| D-01 | Documentação de profissionais só em contratos outsourcing | Exigibilidade herdada por modalidade (bloco base + bloco outsourcing + específico) |
| D-02 | Chave do sistema é o contrato-serviço, não o cliente | CAIXA desdobra em 3; BNB e TJCE em 2 cada |
| D-03 | Certidões e encargos são por CNPJ | Coleta única (escopo corporativo) com replicação nos books |
| D-04 | 13º pode ser antecipado por profissional em qualquer mês | Exigências de evento derivadas da folha, nunca de calendário |
| D-05 | PDF+XLSX ambos exigidos quando definidos | Exigência tem lista de formatos; satisfação exige todos |
| D-06 | Operação exclusivamente on-premises, sem internet | Sem dependência de serviço em nuvem pública; atualizações offline |
| D-07 | Arquivos reais não seguem a nomenclatura do checklist | Identificação por conteúdo; nome/pasta como reforço |
| D-08 | Hierarquia contrato/ano/mês é confiável | Delimitador de varredura |
| D-09 | FGTS, DCTFWeb, DARF, INSS e IRRF referem-se a M-1 | Defasagem fixa no tipo, imutável por contrato |
| D-10 | Checklists em planilha por pasta serão descontinuados | Matriz central é a única fonte; carga inicial a partir do Anexo 1 |
| R-01 | Certidão positiva com efeito de negativa = regular | Campo natureza extraído e aceito |
| R-02 | Conta de leitura ampla ≠ conta de publicação | Duas identidades de serviço com escopos disjuntos |
| R-03 | Sem recolhimento por filial (a confirmar — A07) | Escopo corporativo com 1 CNPJ; modelo já suporta N |
| R-04 | Stack alinhada ao padrão da casa | Arquitetura de referência no cap. 4 é agnóstica de linguagem; decidir na sprint 0 |


# 4. Arquitetura de referência

## 4.1 Componentes
| Componente | Responsabilidade | Tecnologia de referência (ajustar ao padrão da casa) |
|---|---|---|
| API de aplicação | REST para UI e cadastro; autorização; orquestração de comandos | Serviço web stateless (.NET 8 / Java Spring / Node — decidir sprint 0) |
| Worker de processamento | Varredura, extração, reconhecimento, validação, conciliação, publicação — assíncrono, idempotente | Mesmo runtime da API, processos separados |
| Fila de mensagens | Desacoplar varredura/extração da UI; reprocessamento; retries com dead-letter | RabbitMQ |
| Banco relacional | Metadados, matriz, ciclos, exigências, trilha | PostgreSQL 16 |
| Armazenamento de objetos | Repositório de evidências e books, com trava de imutabilidade (object lock/WORM) e versionamento | MinIO on-prem, bucket com retenção em modo compliance |
| Extração de texto | PDF nativo: texto + posições; planilhas: leitura direta | Poppler/pdfminer ou PDFBox, conforme stack |
| OCR (contingência) | Somente documentos digitalizados; resultado sempre vai a triagem | Tesseract 5 (por-BR) |
| Identidade | Autenticação corporativa OIDC + MFA; grupos → papéis | AD/Entra ID via ADFS ou Keycloak como broker |
| Cofre de segredos | Credenciais das contas de serviço, chaves, conexões | HashiCorp Vault ou equivalente corporativo |
| E-mail | Envio via relay interno, com templates versionados | SMTP corporativo |
| Observabilidade | Logs estruturados, métricas de fila e de processamento, alertas | Prometheus + Grafana + logs centralizados |
| Agendador | Abertura de ciclos, varreduras periódicas, réguas de notificação | Scheduler do próprio worker (cron) com registro de execução |


## 4.2 Topologia
```
┌─ VLAN de aplicação ─────────────────────────────────────────────────┐
│  [UI web] ⇄ [API] ⇄ [PostgreSQL]         [Vault]                    │
│               │  ⇅ RabbitMQ                                         │
│           [Workers: varredura | extração | conciliação | publicação]│
│               │ leitura WebDAV/API        │ escrita S3 (object lock)│
└───────────────┼───────────────────────────┼─────────────────────────┘
[OwnCloud] [Jira] [Folha]      [MinIO: evidências + books]
(origem, somente leitura)      (VLAN de dados, sem acesso
direto de usuário)
```
UI e API na VLAN de aplicação; MinIO e PostgreSQL em VLAN de dados sem rota para estações.
Nenhum componente exposto à internet; DNS interno; TLS 1.2+ interno com certificados corporativos.
Duas identidades de serviço: svc-sgdf-leitura (WebDAV/Jira/folha, somente leitura) e svc-sgdf-publica (somente bucket de evidências). Segredos no cofre, rotação a cada 90 dias, uso logado.
Idempotência: todo processamento é chaveado por (origem, caminho, hash); reprocessar não duplica.

# 5. Modelo de dados
Convenções: chaves primárias UUID; timestamps UTC com timezone; soft delete apenas por campo ativo=false (nenhuma exclusão física fora do expurgo de retenção); toda tabela de negócio tem criado_em, criado_por, atualizado_em, atualizado_por.

## 5.1 Cadastro e matriz
| Tabela | Campos principais | Regras |
|---|---|---|
| cliente | id, nome, cnpj, esfera(PUBLICA|PRIVADA), ativo | CNPJ único |
| contrato_servico | id, cliente_id, numero, servico, modalidade_id, vigencia_ini, vigencia_fim, pasta_origem, data_contratual_faturamento(json prazo), calendario_id, ativo | único (cliente, numero, servico); pasta_origem é o caminho raiz no OwnCloud |
| modalidade | id, codigo(OUTSOURCING|SUSTENTACAO|ENTREGA|MISTO), blocos(json) | blocos referencia blocos de exigência herdados |
| tipo_documental | id, codigo, nome, familia, escopo(CORPORATIVO|CONTRATO|PROFISSIONAL), evento(MENSAL|13O|FERIAS|RESCISAO|ADMISSAO|EVENTUAL), defasagem(M|M_MENOS_1|VIGENCIA_NF|EVENTO), formatos(json), criticidade(BLOQUEANTE|NAO_BLOQUEANTE), sigilo(PUBLICO_CLIENTE|INTERNO|PESSOAL|PESSOAL_SENSIVEL), condicional_grupo, ativo | codigo único no formato FAM.NOME; alteração de criticidade exige papel APROVADOR_DAF |
| tipo_alias | id, tipo_id, texto_normalizado, origem(LEGADO|TRIAGEM) | normalização: sem acento, minúsculas, separadores colapsados |
| regra_exigibilidade | id, tipo_id, alvo(MODALIDADE|CONTRATO), alvo_id, obrigatoriedade(OBRIGATORIO|CONDICIONAL|DISPENSADO), prazo(json), responsavel_titular_id, responsavel_substituto_id, fundamento, vigencia_ini, vigencia_fim, versao_matriz | resolução: contrato sobrepõe modalidade; a mais específica vence |
| versao_matriz | id, numero, publicada_em, publicada_por, motivo | append-only; ciclo referencia a versão usada |
| regra_reconhecimento | id, tipo_id, ancoras(json), campos(json), limiar_auto, limiar_triagem, versao | ausência de regra ⇒ conferência manual (nunca bloqueia) |
| regra_conciliacao | id, codigo(R01..R12), tipos_envolvidos(json), logica, tolerancia(json), excecoes(json), modo(BLOQUEIO|ALERTA), fase | sem tolerância cadastrada ⇒ modo ALERTA forçado |
| calendario_feriados | id, uf, data, descricao | para cômputo de dia útil |
| profissional | id, matricula, nome, cpf_cifrado, contrato_servico_id, admissao, desligamento | cpf cifrado em nível de aplicação (AES-GCM, chave no cofre); exibição sempre mascarada |
| alocacao | id, profissional_id, contrato_servico_id, inicio, fim | fonte: relação de alocados + folha |


## 5.2 Execução
| Tabela | Campos principais | Regras |
|---|---|---|
| ciclo | id, contrato_servico_id, competencia, status, versao_matriz_id, aberto_em, prazo_interno, ateste_em, ateste_forma, ateste_evidencia_doc_id, nf_emitida_em, fechado_em | único (contrato, competência); ateste_em é o marco do indicador D+3 |
| exigencia | id, ciclo_id, tipo_id, evento, profissional_id?, status, formatos_pendentes(json), prazo_calculado, responsavel_id, origem(MATRIZ|DERIVADA), regra_id | DERIVADA = criada pela leitura da folha (cap. 7.2) |
| documento | id, origem(OWNCLOUD|JIRA|UPLOAD), caminho, nome_arquivo, hash_sha256, tamanho, mime_real, tipo_id?, confianca, competencia_extraida, validade_extraida, cnpj_extraido, natureza, formato, status_triagem, versao_origem(etag) | único (origem, caminho, hash); mime_real por assinatura binária |
| campo_extraido | id, documento_id, campo, valor, confianca, posicao(json) | posição permite auditar de onde o valor foi lido |
| vinculo_exigencia_documento | exigencia_id, documento_id, formato, decidido_por(SISTEMA|USUARIO), decidido_em | muitos-para-muitos; triagem gera decidido_por=USUARIO |
| conciliacao | id, regra_id, ciclo_id, itens(json), resultado(CONFORME|DIVERGENTE), delta, detalhe | itens lista documentos/valores comparados |
| excecao | id, exigencia_id, motivo, solicitante_id, aprovador_id, aprovado_em, evidencia | aprovador com papel APROVADOR_DAF; nunca o solicitante (SoD) |
| book | id, ciclo_id, versao, publicado_em, hash_conjunto, manifesto(json), caminho_bucket, pecas | append-only; nova publicação ⇒ versao+1 |
| notificacao | id, ciclo_id, tipo(PREVENTIVA|COBRANCA|ESCALONAMENTO|FECHAMENTO|RESOLVIDA), destinatario_id, conteudo_hash, enviada_em, template_versao | nunca contém anexo; só links autenticados |
| pendencia | id, exigencia_id, aberta_em, prazo, escalonada_em?, resolvida_em?, resolucao(ENTREGA|EXCECAO|CANCELAMENTO) | fecha automaticamente quando a exigência é satisfeita |
| log_auditoria | id, ator, papel, acao, objeto_tipo, objeto_id, ip, ocorrido_em, resultado, detalhe(json) | tabela append-only + espelho em log externo; sem UPDATE/DELETE (revogado no banco) |


# 6. Máquinas de estado

## 6.1 Exigência
| De | Para | Gatilho | Ator |
|---|---|---|---|
| — | PENDENTE | Abertura do ciclo (matriz) ou derivação pela folha | Sistema |
| PENDENTE | RECEBIDO | Documento vinculado com confiança ≥ limiar_auto | Sistema |
| PENDENTE | EM_TRIAGEM | Documento candidato com limiar_triagem ≤ confiança < limiar_auto | Sistema |
| EM_TRIAGEM | RECEBIDO | Confirmação humana (gera alias) | Triagem |
| EM_TRIAGEM | PENDENTE | Rejeição do candidato | Triagem |
| RECEBIDO | VALIDADO | Validações unitárias aprovadas (cap. 8.4) | Sistema |
| RECEBIDO | REJEITADO | Falha unitária (competência, vigência, ilegível, CNPJ) | Sistema |
| REJEITADO | PENDENTE | Automático: exige nova entrega; pendência permanece aberta | Sistema |
| VALIDADO | CONCILIADO | Regras de conciliação aplicáveis todas conformes | Sistema |
| VALIDADO | DIVERGENTE | Alguma regra em modo BLOQUEIO divergente | Sistema |
| DIVERGENTE | CONCILIADO | Nova entrega/correção reprocessada conforme | Sistema |
| PENDENTE|DIVERGENTE | DISPENSADO | Exceção aprovada | Aprovador DAF |
| CONCILIADO | PUBLICADO | Inclusão em book publicado | Sistema |


## 6.2 Ciclo
ABERTO → EM_COLETA → PRONTO (toda exigência bloqueante em PUBLICADO/DISPENSADO — pré-condição para envio da medição ao cliente) → ATESTADO (registro do ateste, dispara o relógio D+3) → FATURADO (NF emitida) → FECHADO. Estados de exceção: BLOQUEADO (divergência bloqueante) e REABERTO (republicação, incrementa versão do book). Transições registradas em log_auditoria com ator e motivo.

# 7. Regras de negócio

## 7.1 Resolução da exigibilidade na abertura do ciclo
1. Carregar regras vigentes na competência (vigencia_ini ≤ competência ≤ vigencia_fim) da versão corrente da matriz.
2. Unir blocos da modalidade do contrato-serviço + regras específicas do contrato; em conflito, a regra de contrato sobrepõe a de modalidade.
3. Materializar exigências: escopo CORPORATIVO gera 1 exigência por CNPJ por competência (compartilhada entre ciclos, satisfeita uma única vez); CONTRATO gera 1 por ciclo; PROFISSIONAL gera 1 por profissional alocado ativo na competência.
4. Gravar versao_matriz_id no ciclo: reprocessamentos usam a versão da época, nunca a atual.
5. Calcular prazo de cada exigência (7.3).

## 7.2 Derivação de eventos a partir da folha
Executada quando a folha estruturada da competência chega (integração 14.3). Nunca por calendário.
| Evento | Condição na folha/movimentação | Exigências instanciadas (por profissional) |
|---|---|---|
| 13º antecipado | Rubrica de adiantamento de 13º presente na competência | DEC.FOPAG_13, DEC.COMPROVANTE_PG_13 e encargos correspondentes |
| Férias | Rubrica de férias ou afastamento programado | FER.AVISO, FER.RECIBO, FER.COMPROVANTE_PG |
| Rescisão | Data de desligamento na movimentação | RES.AVISO_PREVIO, RES.TRCT, RES.COMPROVANTE_PG, FGT.GUIA_RESCISORIA, FGT.COMPROVANTE_PG_GRRF, RES.ASO_DEMISSIONAL |
| Admissão | Data de admissão na competência | ADM.DOCUMENTACAO |
| Não adesão a VT | Ausência de rubrica de VT para alocado ativo | BEN.TERMO_NAO_ADESAO_VT como alternativa condicional a BEN.RELACAO_VT |

Os códigos de rubrica são cadastro (tabela de-para por sistema de folha), não código-fonte.

## 7.3 Prazo estruturado
Prazo é um objeto, nunca texto livre: { ancora, tipo_dia, offset, calendario }.
| Campo | Valores | Semântica |
|---|---|---|
| ancora | INICIO_COMPETENCIA | ATESTE | SOLICITACAO_FATURAMENTO | EVENTO | Data-base do cômputo |
| tipo_dia | UTIL | CORRIDO | Dias úteis usam calendario_feriados da UF do contrato |
| offset | inteiro ≥ 0 | Ex.: {INICIO_COMPETENCIA, CORRIDO, 21} = "até o dia 21"; {ATESTE, CORRIDO, 3} = D+3 da política |
| comportamento | — | Documentos "sob faturamento" usam âncora SOLICITACAO_FATURAMENTO e só entram na régua de cobrança após o evento |


## 7.4 Defasagem de competência
Atributo do tipo documental, imutável por contrato: M (folha, benefícios, medição, NF); M-1 (FGTS, DCTFWeb, DARF, INSS, IRRF); VIGENCIA_NF (certidões — válidas na data prevista de emissão da NF, recalculada se a emissão atrasar); EVENTO (férias, rescisão, admissão). A validação compara a competência extraída do documento com a exigida após aplicar a defasagem.

## 7.5 Condicionais e formatos
Condicional "A ou B": exigências com o mesmo condicional_grupo no ciclo são satisfeitas quando qualquer uma do grupo é satisfeita (ex.: BEN.RELACAO_VT ou BEN.TERMO_NAO_ADESAO_VT, por profissional).
Formatos: a exigência lista formatos obrigatórios (ex.: [pdf, xlsx]); satisfação exige todos; validação cruzada de coerência de competência entre formatos do mesmo tipo.

# 8. Pipeline de ingestão e reconhecimento

## 8.1 Varredura
Agenda: varredura completa diária (madrugada) + incremental a cada 30 min durante o horário comercial nos ciclos abertos.
Delta por ETag/Last-Modified do WebDAV; arquivo novo ou alterado entra na fila de extração; hash decide se é conteúdo novo.
Recursiva a partir de pasta_origem/AAAA/MM, incluindo subpastas e a raiz do mês (documentos soltos são comuns na base real).
Ignorados com registro: arquivos temporários (~$, .tmp), checklists em planilha, cópias de conflito de sincronização (padrão de nome + hash duplicado) — estas sinalizadas como alerta de organização, nunca publicadas.
Limites: tamanho máximo por arquivo (parâmetro, padrão 50 MB); tipos aceitos pdf, xlsx, xls, csv; verificação antivírus (ICAP/clamd) antes da extração.

## 8.2 Extração
PDF nativo: texto com posições (camada de layout preservada para auditoria do campo extraído).
PDF sem camada de texto: OCR Tesseract por-BR; resultado marcado ocr=true e destino obrigatório = triagem.
Planilhas: leitura direta de células; identificação por cabeçalhos.
mime_real por assinatura binária; divergência extensão × conteúdo gera rejeição com motivo.

## 8.3 Classificação determinística
Cada tipo documental tem uma regra_reconhecimento com âncoras (expressões e padrões que devem ocorrer no texto) e campos (padrões de extração com validação de formato). A pontuação é uma soma ponderada declarada na própria regra — auditável e reproduzível:
score = Σ peso(âncora presente) + Σ peso(campo extraído e válido) + bônus(pasta compatível) + bônus(alias no nome do arquivo)
score ≥ limiar_auto (padrão 0,95)  → vínculo automático
limiar_triagem (0,70) ≤ score < limiar_auto → fila de triagem
score < limiar_triagem → arquivo desconhecido (não conta como entrega; aparece no painel de organização)
A confirmação em triagem grava um tipo_alias novo (origem=TRIAGEM) com o padrão do nome do arquivo — o mesmo padrão não volta à fila. Regras e pesos são versionados; a decisão registra a versão usada.

## 8.4 Validações unitárias (pós-identificação)
| # | Validação | Falha gera |
|---|---|---|
| V1 | Antivírus limpo, mime_real coerente, tamanho dentro do limite | REJEITADO (segurança) |
| V2 | Texto legível (mín. de caracteres/página) ou OCR confirmado em triagem | REJEITADO (ilegível) |
| V3 | CNPJ extraído = CNPJ da empresa (bloco corporativo) ou do cliente quando aplicável | REJEITADO (titularidade) |
| V4 | Competência extraída = competência exigida após defasagem (7.4) | REJEITADO (competência) |
| V5 | Certidões: validade ≥ data prevista da NF; natureza ∈ {negativa, positiva c/ efeito de negativa} | REJEITADO (vigência) |
| V6 | Todos os formatos exigidos presentes e coerentes entre si | exigência permanece parcial (formatos_pendentes) |
| V7 | Hash inédito para a exigência (evita contar duas vezes o mesmo arquivo renomeado) | ignorado com registro |
| **V8** | **Campos essenciais presentes e bem formados** (acrescentada — ver `CORRECOES-V1.1.md` e o achado A12) | **REJEITADO (incompleto)** |


## 8.5 Regras de reconhecimento — carga inicial (bloco corporativo)
Especificação por tipo no Anexo 1 (aba CATALOGO_DOCUMENTOS) complementada abaixo com os campos de extração. Padrões ilustrativos; os definitivos são cadastro.
| Tipo | Âncoras (todas de peso alto) | Campos extraídos e formato |
|---|---|---|
| CER.CND_RFB | Título da certidão de créditos tributários federais e dívida ativa; código de controle | cnpj (##.###.###/####-##), emissao (data), validade (data), codigo_controle, natureza |
| CER.CRF_FGTS | Título "Certificado de Regularidade do FGTS"; número do certificado | inscricao, validade_ini, validade_fim, numero |
| CER.CNDT | Título da certidão de débitos trabalhistas; número/expedição | cnpj, numero, validade, natureza |
| CER.SICAF | Cabeçalho do relatório de situação do fornecedor | cnpj, data_consulta, situacao_niveis |
| CER.CND_ESTADUAL | Emissor fazendário estadual/distrital + número | cnpj, validade, natureza, uf |
| CER.CND_CIVEL_CRIMINAL / CER.CND_FALENCIA | Distribuidor judicial + tipo de distribuição | cnpj/razão, emissao, validade (ou prazo do edital) |
| INS.DCTFWEB | Recibo de transmissão + período de apuração | cnpj, competencia (MM/AAAA), numero_recibo, valor_total |
| INS.DARF | Código de receita + período de apuração + nº documento | competencia, valor, vencimento, codigo_barras/nosso_numero |
| FGT.GUIA | Identificação da guia FGTS Digital + competência | competencia, valor, identificador, vencimento |
| FGT.EXTRATO | Cabeçalho de extrato analítico por trabalhador | competencia, lista {cpf_mascarado, valor} para cobertura |
| Comprovante bancário (pareável) | Sem âncora fixa — identificado pelo pareamento: valor + data + identificador da obrigação | valor, data_pagamento, identificador, autenticacao |


# 9. Conciliação
Regra sem tolerância cadastrada opera em modo ALERTA (nunca bloqueia). Resultado sempre gravado com os valores comparados e o delta.
| ID | Fase | Lógica (pseudocódigo) | Tolerância padrão |
|---|---|---|---|
| R01 | 1a | para cada FGT.GUIA validada: existe comprovante com |valor_c − valor_g| ≤ tol e identificador igual (ou data_pgto ≤ vencimento+carencia)? conforme : divergente | ± R$ 0,01; carência 0 dias |
| R02 | 1a | DCTFWeb.valor_total = Σ DARF.valor vinculados; cada DARF tem comprovante pareado (regra R01) | ± R$ 0,01 |
| R03 | 1a | para cada CER.*: validade ≥ data_prevista_NF(ciclo) | 0 dias (parâmetro) |
| R04 | 1a | formatos_pendentes = ∅ e competência igual entre formatos do mesmo tipo | — |
| R05 | 1b | conjunto(matriculas em OPE.RELACAO_ALOCADOS) ⊆ conjunto(matriculas na folha) e cada uma tem FOL.CONTRACHEQUE vinculado | exceções: admitidos/desligados no mês (janela pró-rata) |
| R06 | 1b | todo evento derivado (7.2) tem seu conjunto documental completo até o prazo do evento | ASO demissional: +10 dias corridos (parâmetro) |
| R07 | 2 | beneficiários(BEN.RELACAO_PLANO_SAUDE) ≡ matrículas com rubrica de desconto na folha; comprovante pareado com fatura | dependentes e coparticipação: regra de rateio cadastrada |
| R08 | 2 | idem para VA/VR e VT, aceitando condicional de não adesão | pró-rata de dias úteis |
| R09 | 2 | Σ base_fgts(folha) × alíquota ≈ FGT.GUIA.valor | ± 0,5% (rescisões apartadas) |
| R10 | 2 | Σ bases(folha) ≈ DCTFWEB.valor_total por grupo de receita | ± 0,5% |
| R11 | 2 | CPFs do FGT.EXTRATO ⊇ matrículas alocadas ativas | desligados no mês M-1 |
| R12 | 2 | valor(OPE.PLANILHA_MEDICAO) ≤ saldo(OPE.EMPENHO) e = valor da NF | ± R$ 0,01 |


# 10. Publicação do book
Pré-condição: ciclo em PRONTO (toda exigência bloqueante PUBLICÁVEL: CONCILIADO ou DISPENSADO). Publicação manual pelo Curador ou automática ao atingir PRONTO (parâmetro por contrato).
Estrutura no bucket: books/{contrato}/{AAAA-MM}/v{N}/NN_TIPO_COMPETENCIA.pdf + _indice.pdf + _manifesto.json. Numeração sequencial sem lacunas, ordenada por família.
Tarjamento: tipos com sigilo PESSOAL/PESSOAL_SENSIVEL passam pelo redator (máscara de CPF e dados não exigíveis) antes da cópia para o book do cliente; o original íntegro permanece no repositório interno com acesso restrito e logado.
Imutabilidade: bucket com object lock em modo compliance pela duração da retenção; republicação cria v{N+1}; nenhuma versão é alterada ou removida fora do expurgo formal.
O índice (_indice.pdf) traz contrato, competência, versão, relação de peças com páginas e o hash do conjunto — é a folha de rosto entregue ao cliente.
Exemplo de _manifesto.json:
{
"contrato": "CAIXA-09705.2025", "competencia": "2026-04", "versao": 2,
"publicado_em": "2026-04-10T14:22:31-03:00", "publicado_por": "svc-sgdf-publica",
"ciclo_id": "…", "versao_matriz": "1.3", "hash_conjunto_sha256": "9f31c2…",
"pecas": [
{"seq":1, "tipo":"CER.CND_RFB", "arquivo":"01_CER.CND_RFB_2026-04.pdf",
"hash_sha256":"4c17…", "origem":"owncloud://CAIXA - 09705.2025/2026/04/13 - CERTIDÃO…pdf",
"validado_em":"2026-04-08T09:12:04-03:00", "tarjado": false }
]
}

# 11. Notificações

## 11.1 Régua
| Momento | Mensagem | Destinatário |
|---|---|---|
| D-2 do prazo da exigência | Preventiva consolidada (1 por área/ciclo): o que vence em 48 h | Titular |
| D+0 | Cobrança consolidada: pendências vencidas, com prazo e link | Titular + substituto |
| D+2 | Escalonamento nível 1 | Gestor da área, cópia ao titular |
| D+5 | Escalonamento nível 2 | DAF, cópia ao gestor |
| Entrega detectada | "Resolvido" — fecha a pendência com registro | Quem foi cobrado |
| Ciclo PRONTO | Fechamento com dados de desempenho (modelo aprovado em reunião de 03/09) e rodapé obrigatório de não-substituição do ateste | Áreas do ciclo, cópia gestor regional e DAF |


## 11.2 Regras fixas
Máximo 1 e-mail por área por ciclo por dia (consolidação forçada).
Nunca anexar documento; somente link autenticado com expiração e log de acesso.
Destinatário nominal resolvido por (contrato × família documental), com substituto.
Templates versionados no cadastro; o hash do conteúdo enviado fica em notificacao.
Modo sombra suprime todo envio e grava o que teria sido enviado, para calibragem.

# 12. Interface de usuário
Referência visual e de interação: Anexo 2 (SGDF_Layout_Prototipo.html), navegável, com os tokens declarados como variáveis CSS. Resumo normativo:
| Tela | Conteúdo obrigatório | Regras de interação |
|---|---|---|
| Painel da competência | Faixa de indicadores; bloco corporativo (estado dos 12 tipos por CNPJ); razão de ciclos com barra segmentada por família | Seleção de competência global; clique no ciclo abre o detalhe; legenda fixa |
| Ciclo | Exigências agrupadas por família com estado; linha expansível mostrando pareamento e motivo da decisão; trilho lateral com resumo, eventos derivados, versões do book | Publicar book desabilitado exibe o motivo por extenso; cobrança manual disponível; nada é editável aqui |
| Triagem | Fila com sugestão + confiança + motivo de estar em triagem; detalhe com campos extraídos, pré-visualização e ações | Confirmar registra alias e informa; reclassificar exige escolher tipo; ilegível devolve a PENDENTE com motivo |
| Matriz de exigibilidade | Abas por bloco; versão vigente; regras com criticidade, prazos e responsáveis; histórico de versões | Alteração cria rascunho → publicação gera nova versão; bloqueante pede aprovação DAF; aviso fixo: alterações não travam o faturamento |
| Books publicados | Versões, peças, hash, publicador | Somente leitura; download logado |


## 12.1 Tokens de design (identidade Engesoftware)
| Token | Valor | Uso |
|---|---|---|
| brand | #E87605 | Ação primária, item ativo, marcas gráficas (medido do logotipo oficial) |
| brand-ink | #9A4E00 | Texto e links laranja sobre branco (contraste AA; nunca usar #E87605 em texto corrido) |
| ink / ink-2 / ink-3 | #26211A / #645C4F / #8B8375 | Texto primário, secundário, apoio |
| paper / surface / line | #F4F3F0 / #FFFFFF / #E5E1D9 | Fundo da aplicação, superfícies, divisores |
| estados | ok #1E7F4F · triagem #2258C4 · divergente #C2362B · pendente #6B7280 · dispensa #7854C8 | Chips e barras; nunca comunicar estado apenas por cor (sempre com rótulo) |
| tipografia | IBM Plex Sans (400–700); IBM Plex Mono para códigos, competências e hashes | Fontes hospedadas localmente em produção (ambiente sem internet) |


# 13. API interna (contratos principais)
REST/JSON, versionada em /api/v1, autenticação por sessão OIDC (UI) e client-credentials (workers). Autorização por papel + escopo de contrato avaliada no servidor em todo endpoint. Paginação por cursor; erros no formato problem+json com código estável.
| Endpoint | Métodos | Função / observações |
|---|---|---|
| /contratos · /contratos/{id} | GET POST PATCH | Cadastro de contrato-serviço; PATCH nunca apaga histórico |
| /matriz/tipos · /matriz/regras · /matriz/versoes | GET POST PATCH | Rascunho → POST /matriz/versoes publica; criticidade bloqueante exige papel APROVADOR_DAF |
| /ciclos?competencia=&status= | GET | Painel; inclui agregados por família |
| /ciclos/{id} | GET | Detalhe com exigências, conciliações, books |
| /ciclos/{id}/ateste | POST | Registra ateste {data, forma, documento_id} — marco do D+3; papel Gestor do Contrato |
| /ciclos/{id}/book | POST | Publica nova versão; valida pré-condição PRONTO; 409 com motivo se bloqueado |
| /ciclos/{id}/notificar | POST | Cobrança manual consolidada; respeita a regra de 1/dia |
| /exigencias/{id}/excecao | POST | Solicita dispensa; aprovação em /excecoes/{id}/aprovar (SoD: aprovador ≠ solicitante) |
| /triagem · /triagem/{id}/confirmar|reclassificar|ilegivel | GET POST | Fila e decisões; confirmar grava alias |
| /documentos/{id} · /documentos/{id}/conteudo | GET | Metadados; conteúdo via URL assinada de curta duração (bucket), acesso logado |
| /books/{id}/indice | GET | Índice e manifesto |
| /auditoria?objeto=&ator=&periodo= | GET | Papel Auditoria; exportação CSV |
| /webhooks/varredura | POST interno | Gatilho de varredura incremental (workers) |


# 14. Integrações

## 14.1 OwnCloud (origem principal)
Protocolo WebDAV: PROPFIND para listagem com ETag; GET para download; conta svc-sgdf-leitura somente-leitura.
Escopo: raiz de faturamento declarada por contrato (pasta_origem). Fora dela, nada é lido.
Resiliência: retry exponencial; indisponibilidade gera alerta de operação, nunca perda (fila persiste).

## 14.2 Jira
REST API: busca por JQL parametrizada por contrato; anexos das issues do processo de faturamento.
Jira é fonte complementar: quando o mesmo tipo existir nas duas origens, o repositório-mestre do tipo (cadastro) decide qual é a evidência — pendência A09.

## 14.3 Folha de pagamento (habilita a fase 1b)
Contrato de dados mínimo, por competência: {matricula, cpf, contrato/centro_custo, rubrica, tipo_rubrica, valor, base_fgts, base_inss, evento_data?}.
Meio de entrega em ordem de preferência: API do sistema de folha → exportação agendada (CSV/posicional) em diretório monitorado → eventos eSocial (S-1200/S-1210/S-2299) como alternativa.
Dados de folha não são exibidos na UI além do necessário à conciliação; CPF cifrado, valores agregados quando possível.
Pendência A05: fornecedor/sistema e layout a confirmar — bloqueia o início da 1b, não da 1a.

## 14.4 E-mail e identidade
SMTP relay interno com remetente dedicado (faturamento-sgdf@); SPF/DKIM internos conforme padrão da casa.
OIDC contra o IdP corporativo; grupos do diretório mapeados a papéis; MFA exigido nos papéis com acesso a documento de escopo profissional.

# 15. Segurança e conformidade (requisitos de implementação)

## 15.1 Autorização
| Papel | Pode | Não pode |
|---|---|---|
| ADMIN_SISTEMA | Configuração técnica, regras de reconhecimento, integrações, parâmetros | Ver conteúdo de documentos de escopo profissional; aprovar exceção |
| CURADOR_MATRIZ | Cadastro de contratos/regras; publicar versão da matriz; publicar e enviar book | Aprovar exceção; alterar criticidade bloqueante sem aprovação |
| PUBLICADOR_AP | Ver e acompanhar exigências trabalhistas dos seus contratos; triagem desses tipos | Acessar contratos fora do seu escopo; documentos fiscais |
| PUBLICADOR_FIN | Idem para tipos fiscais e comprovantes | Documentos de escopo profissional fora da conciliação |
| GESTOR_CONTRATO | Registrar ateste; acompanhar seu ciclo; depositar documentos técnicos | Ver dados pessoais além do agregado |
| APROVADOR_DAF | Aprovar exceções e criticidade; visão global | Aprovar exceção que ele mesmo solicitou (SoD imposta no serviço) |
| AUDITORIA | Leitura global + trilha + exportações | Qualquer escrita |
| svc-sgdf-leitura / svc-sgdf-publica | Ler origens / escrever no bucket de evidências | Qualquer outra operação; login interativo negado |


## 15.2 Requisitos verificáveis
| Req. | Implementação exigida | Verificação |
|---|---|---|
| SEC-01 | OWASP ASVS nível 2 como baseline; Top 10 e API Top 10 cobertos em revisão de código | Checklist ASVS preenchido por release |
| SEC-02 | CPF cifrado (AES-256-GCM, chave no cofre, rotação anual); exibição sempre mascarada (***.***.***-NN) | Teste automatizado de mascaramento |
| SEC-03 | log_auditoria sem UPDATE/DELETE (permissão revogada no banco) + espelho em log externo | Tentativa de alteração falha em teste |
| SEC-04 | URLs de conteúdo assinadas, expiração ≤ 15 min, acesso registrado com ator | Log confere ator × documento |
| SEC-05 | Uploads e varredura passam por antivírus e validação de mime_real | Arquivo EICAR rejeitado |
| SEC-06 | Rate limiting e lockout progressivo na API; sessões com timeout de inatividade | Teste de força bruta |
| SEC-07 | Dependências com SCA no pipeline; imagem base fixada e escaneada; sem vulnerabilidade crítica/alta aberta em release | Gate no CI |
| SEC-08 | Backups: banco (PITR) e bucket (replicação para segundo destino + cópia imutável); teste de restauração trimestral | Relatório de teste |
| SEC-09 | RTO 8 h / RPO 4 h fora do fechamento; runbook de DR documentado | Simulação anual |
| SEC-10 | Recertificação trimestral de acessos por contrato, com relatório para a DAF | Job de relatório |
| LGPD-01 | Inventário de dados pessoais e RIPD aprovados antes da produção (pendência A12) | Documento assinado |
| LGPD-02 | Retenção por tabela de temporalidade com expurgo agendado e registrado (pendência A08) | Log de expurgo |
| LGPD-03 | Tarjamento aplicado a todo tipo PESSOAL/PESSOAL_SENSIVEL no book do cliente; revisão por amostragem mensal (n≥5) | Checklist mensal |


# 16. Requisitos não funcionais
| Categoria | Requisito |
|---|---|
| Volumetria de referência | ±15 contratos-serviço ativos; ±890 profissionais; estimativa 2.500–4.500 arquivos/mês (1–2 GB/mês); dimensionar armazenamento para 5 anos com folga de 3× |
| Desempenho | Varredura incremental de um contrato ≤ 5 min; classificação de um PDF nativo ≤ 3 s; painel da competência ≤ 2 s com 15 ciclos |
| Janela crítica | Do 1º ao 10º dia útil o sistema está em período de fechamento: congelamento de mudanças (integração com o processo de mudanças ITIL) e prioridade máxima de suporte |
| Disponibilidade | 99% em horário comercial; degradação aceitável: fila acumula e recupera sem perda |
| Compatibilidade | UI: navegadores corporativos suportados (Chrome/Edge atuais); responsiva até 1024 px; sem dependência de internet (fontes e bibliotecas locais) |
| Acessibilidade | Foco visível, navegação por teclado nas filas e tabelas, contraste AA, estado nunca comunicado apenas por cor |
| Idioma e formatos | pt-BR; datas dd/mm/aaaa na UI e ISO-8601 na API; moeda R$ com duas casas; competência AAAA-MM |
| Auditabilidade | Qualquer decisão automática reproduzível a partir de: documento (hash) + versão da regra + versão da matriz |


# 17. Ambientes e pipeline
Ambientes: DEV → HML (com cópia mascarada de dados; nunca dados pessoais reais) → PRD. Modo sombra é um estado da PRD, não um ambiente.
Pipeline por merge request: build + testes unitários (cobertura mínima 70% nos módulos de regra) + SAST + SCA + lint; DAST na HML a cada release.
Gate de release: zero vulnerabilidade crítica/alta aberta; checklist ASVS do release; aprovação de mudança fora da janela crítica.
Migrations de banco versionadas e reversíveis; seed da carga inicial (catálogo + matriz do Anexo 1) como migration de dados auditável.
Feature flags para: envio de notificações (liga/desliga por contrato), publicação automática do book, bloqueio por divergência.

# 18. Backlog de desenvolvimento
Histórias com critério de aceite objetivo. IDs estáveis para o board. Estimativas em pontos ficam com a equipe; a ordem abaixo é a ordem de dependência.

## Fase 0 — Fundação (cadastro e carga)
| ID | História | Critério de aceite |
|---|---|---|
| F0-01 | Autenticação OIDC + papéis por grupo do diretório | Login com MFA; papel errado recebe 403 em endpoint protegido |
| F0-02 | CRUD de cliente e contrato-serviço | CAIXA cadastrada como 3 contratos-serviço distintos; unicidade (cliente, número, serviço) |
| F0-03 | CRUD de tipo documental + aliases | Importação dos 51 tipos e 69 aliases do Anexo 1 por migration |
| F0-04 | Regras de exigibilidade com herança por modalidade e vigência | Ciclo simulado de contrato outsourcing gera blocos base+outsourcing; sustentação gera só base |
| F0-05 | Versionamento da matriz (rascunho → publicação) | Alterar regra não afeta ciclo aberto; histórico lista versões com autor e motivo |
| F0-06 | Prazo estruturado + calendário de feriados | "5º dia útil" de 04/2026 calculado corretamente com feriado cadastrado |
| F0-07 | Abertura automática de ciclos e materialização de exigências | No 1º dia útil, 15 ciclos abertos; exigência corporativa única compartilhada |
| F0-08 | Trilha de auditoria append-only | UPDATE em log_auditoria negado pelo banco; toda escrita de negócio gera evento |
| F0-09 | Fluxo de exceção com SoD | Solicitante não consegue aprovar a própria exceção; aprovação exige APROVADOR_DAF |


## Fase 1a — Bloco corporativo
| ID | História | Critério de aceite |
|---|---|---|
| F1-01 | Conector WebDAV com delta por ETag e antivírus | Varredura de pasta real; arquivo infectado (EICAR) rejeitado e logado |
| F1-02 | Extração de texto PDF com posições | Campos extraídos exibem a região de origem no documento |
| F1-03 | Motor de classificação por âncoras + score | As 7 certidões, DCTFWeb, DARF e guia FGTS de 3 competências fechadas classificadas com precisão ≥ 95% |
| F1-04 | Validações unitárias V1–V7 | Certidão vencida → REJEITADO com motivo; competência errada idem |
| F1-05 | Conciliação R01–R04 | Guia sem comprovante → DIVERGENTE; par correto → CONCILIADO com valores gravados |
| F1-06 | Fila de triagem com registro de alias | Confirmar tira da fila, cria alias e o mesmo padrão não retorna |
| F1-07 | Painel da competência (telas 1 e 2 do Anexo 2) | Barra segmentada reflete os estados reais; bloqueio de publicação exibe motivo |
| F1-08 | Publicação do book v1 (bucket + índice + manifesto) | Objeto imutável (delete negado); hash confere; índice legível |
| F1-09 | Módulo de notificação em modo sombra | Régua registra o que enviaria, nada é enviado |
| F1-10 | Painel de arquivos desconhecidos e conflitos de sincronização | Cópia em conflito aparece sinalizada e nunca vinculada |


## Fase 1b — Outsourcing e eventos
| ID | História | Critério de aceite |
|---|---|---|
| F2-01 | Ingestão da folha estruturada (layout 14.3) + de-para de rubricas | Folha de teste carregada; rubricas mapeadas por cadastro |
| F2-02 | Derivação de eventos (13º antecipado, férias, rescisão, admissão) | Rescisão na folha de teste instancia as 6 exigências do conjunto, só para aquela matrícula |
| F2-03 | Escopo profissional e completude por equipe | "Faltam 3 contracheques de 42" calculado e exibido |
| F2-04 | Conciliação R05–R06 com janelas pró-rata | Admitido no meio do mês não gera falso positivo |
| F2-05 | Condicionais A-ou-B por profissional | Termo de não adesão satisfaz a exigência de VT do profissional |
| F2-06 | Cifra e mascaramento de CPF ponta a ponta | CPF nunca aparece em claro em UI, log ou notificação |
| F2-07 | Tarjamento no book para tipos PESSOAL/PESSOAL_SENSIVEL | Book do cliente sai mascarado; original íntegro no repositório interno |


## Fases 2 e 3 — Sombra e produção
| ID | História | Critério de aceite |
|---|---|---|
| F3-01 | Modo sombra em 2 contratos, 2 ciclos, com relatório comparativo | Divergência ≤ 2% contra a conferência manual (metodologia no cap. 19) |
| F3-02 | Ativação de notificações com feature flag por contrato | Régua completa D-2/D+0/D+2/D+5; máx. 1 e-mail/área/dia |
| F3-03 | Registro de ateste e indicador D+3 | Painel exibe tempo ateste→NF por contrato e o % ≥ 98% da política |
| F3-04 | Bloqueio de PRONTO por exigência bloqueante + dispensa DAF | Ciclo com divergência bloqueante não publica; dispensa aprovada libera com trilha |
| F3-05 | Indicadores e exportações (cap. 21) | KPI/KRI calculados por competência, exportáveis em CSV |
| F3-06 | Recertificação trimestral de acessos | Relatório gerado e enviado à DAF |


# 19. Plano de testes e metodologia do modo sombra
Massa de teste: 3 competências fechadas reais de 2 contratos (1 outsourcing, 1 entrega), copiadas para HML com dados pessoais mascarados.
Precisão de classificação = corretos / total de arquivos com gabarito humano; medir por tipo documental; meta ≥ 95% no bloco corporativo antes da 1b.
Modo sombra: para cada ciclo, comparar (a) exigências marcadas pendentes pelo sistema × pendências reais apontadas pela conferência manual; (b) documentos conciliados × conferência manual. Divergência = (falsos positivos + falsos negativos) / total de exigências. Meta ≤ 2% por dois ciclos consecutivos.
Falso negativo (sistema diz completo, humano acha pendência) tem peso de bloqueio: qualquer ocorrência em exigência bloqueante impede a ativação, independentemente do percentual.
Testes de segurança: casos SEC-01…SEC-10 do cap. 15.2 automatizados onde possível; pentest interno antes da produção.
Testes de acessibilidade: navegação por teclado nas 5 telas; leitor de tela nos fluxos de triagem e exceção.

# 20. Implantação e operação (ITIL)
Catálogo: "SGDF — conferência documental do faturamento", criticidade alta com sazonalidade (1º–10º dia útil).
Mudanças: janela proibida no período de fechamento; mudanças na matriz não são mudanças de TI (são cadastro), mas publicam versão auditável.
Incidentes: P1 = varredura ou publicação paradas em período de fechamento (resposta 1 h); P2 = triagem indisponível (4 h); P3 = demais (1 dia útil).
Runbook mínimo: reprocessar ciclo; reprocessar documento por hash; reemitir notificação; restaurar versão de book para consulta; rotação de credenciais de serviço.
Base de conhecimento: dicionário das regras (o que cada divergência significa e quem resolve) publicado no Confluence e linkado na UI.
Sustentação: definir na pendência A10 o time responsável pós-go-live; sem dono nomeado, não ativar a fase 3.

# 21. Indicadores
| Indicador | Fórmula | Meta | Fonte |
|---|---|---|---|
| NF em D+3 (política) | ciclos com nf_emitida_em ≤ ateste_em + 3 dias / ciclos faturados | ≥ 98% | ciclo |
| Tempo ateste → NF | média(nf_emitida_em − ateste_em) | ≤ 2 dias | ciclo |
| Completude na 1ª conferência | exigências satisfeitas na 1ª varredura pós-prazo / total | tendência ↑ | exigencia |
| Pendências vencidas por área | contagem por responsável no fechamento | 0 | pendencia |
| Exceções aprovadas (KRI) | contagem por competência — alta = controle contornado | monitorar | excecao |
| Divergências de conciliação (KRI) | contagem por regra e contrato | tendência ↓ | conciliacao |
| Idade da certidão na NF (KRI) | validade_restante na emissão | > 5 dias | documento |
| Precisão de classificação | confirmações de triagem que mantêm a sugestão / total triado | ≥ 90% | triagem |
| Books republicados | versões > 1 por competência | tendência ↓ | book |


# 22. Riscos de desenvolvimento e operação
| ID | Risco | Mitigação embutida nesta especificação |
|---|---|---|
| P01 | Falso positivo em massa → abandono | Modo sombra obrigatório (F3-01); limiares por tipo; triagem que ensina aliases |
| P02 | Automação desloca a responsabilidade do ateste | Rodapé fixo nas mensagens; ateste humano como transição de estado; texto na política |
| P03 | Modalidades conferidas com a matriz errada (BNB/TJCE) | Contrato-serviço como chave (F0-02); verificação manual antes da fase 0 |
| P04 | Comprometimento das contas de serviço | Escopos disjuntos, cofre, rotação 90 dias, login interativo negado |
| P05 | Vazamento de dado pessoal no book | Sigilo por tipo + tarjamento (F2-07) + amostragem mensal LGPD-03 |
| P06 | Folha estruturada indisponível | 1a independente; 1b bloqueada até A05; contingência por eSocial |
| P07 | Regressão de regra em produção | Regras versionadas; reprocessamento usa versão da época; testes de regra com massa fixa |
| P08 | Sistema vira legado sem dono | Gate: fase 3 não ativa sem time de sustentação nomeado (A10) |


# 23. Pendências que condicionam o desenvolvimento
| ID | Pendência | Bloqueia | Responsável |
|---|---|---|---|
| A01 | Checklists de SEFAZ-CE, CGM-CE e SEBRAE GO (a incorporar ao catálogo) | Carga completa da fase 0 (não impede o início) | Gestão de Contratos |
| A02 | Natureza de APOIO A GESTAO (interna × contrato sem checklist) | Escopo da carteira | Gestão de Contratos |
| A03 | Matriz separada por modalidade em BNB e TJCE | Fase 0 — verificação prévia urgente | Gestão de Contratos |
| A04 | Exigências dos 3 contratos CAIXA (idênticas ou não) | Carga da fase 0 | Gestão de Contratos |
| A05 | Sistema de folha + layout de extração | Início da fase 1b | TI e AP |
| A06 | Fundamento contratual por exigência | Campo obrigatório na matriz (pode ser preenchido em paralelo) | Gestão de Contratos |
| A07 | Recolhimento por CNPJ único ou por filial | Modelagem do escopo corporativo (modelo já suporta N) | AP |
| A08 | Tabela de temporalidade | Configuração de retenção do bucket antes da produção | Jurídico |
| A09 | Repositório-mestre por tipo (OwnCloud × Jira) | Regra de desempate na ingestão | TI e DAF |
| A10 | Time de sustentação pós-go-live | Ativação da fase 3 | DAF e TI |
| A11 | Consolidação das duas políticas + inclusão da AP no RACI | Publicação da norma que ampara o portão documental | DAF |
| A12 | RIPD e inventário de dados pessoais | Entrada em produção | DAF e Jurídico |
| A13 | Stack de desenvolvimento (padrão da casa) e infraestrutura alocada | Sprint 0 | TI |

Regra de leitura deste documento: tudo o que está como "padrão" ou "parâmetro" é valor inicial ajustável em cadastro; tudo o que está como "exigido" ou "obrigatório" é requisito de aceitação. Dúvidas de interpretação são resolvidas com a área demandante antes de codificar — nunca por suposição silenciosa.