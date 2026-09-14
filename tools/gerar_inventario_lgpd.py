#!/usr/bin/env python3
"""Gera o inventário de dados pessoais — pendência A12, requisito LGPD-01.

Saída: docs/INVENTARIO-DADOS-PESSOAIS.md

O inventário é a parte factual do RIPD e é derivável do catálogo: quais tipos
documentais carregam dado pessoal, de que natureza, onde ficam e por quanto
tempo. O que NÃO é derivável — base legal por finalidade, avaliação de risco,
medidas de mitigação e a aprovação — continua sendo do DPO e do jurídico.

Entregar o inventário pronto tira do caminho a maior parte do trabalho braçal
do RIPD sem fingir que o RIPD está feito.
"""
import csv
from collections import defaultdict
from datetime import date
from pathlib import Path

CATALOGO = Path("dados/catalogo_documentos.csv")
COMPLEMENTO = Path("dados/complemento_tipo_documental.csv")
SAIDA = Path("docs/INVENTARIO-DADOS-PESSOAIS.md")

NATUREZA = {
    "PESSOAL_SENSIVEL": ("Sensível", "Saúde (art. 5º, II da LGPD)"),
    "PESSOAL": ("Comum", "Identificação, vínculo e remuneração"),
    "INTERNO": ("Não pessoal", "Dado da pessoa jurídica"),
    "PUBLICO_CLIENTE": ("Não pessoal", "Dado da pessoa jurídica"),
}


def main() -> int:
    catalogo = {c["COD_CANONICO"]: c for c in csv.DictReader(CATALOGO.open(encoding="utf-8"))}
    complemento = list(csv.DictReader(COMPLEMENTO.open(encoding="utf-8")))

    por_sigilo = defaultdict(list)
    for c in complemento:
        por_sigilo[c["SIGILO"]].append(c)

    pessoais = por_sigilo["PESSOAL"] + por_sigilo["PESSOAL_SENSIVEL"]

    L = [
        "# Inventário de dados pessoais — SGDF",
        "",
        "**Pendência A12 · requisito LGPD-01 · classificação: Interno — Restrito**",
        "",
        "> **GERADO** por `tools/gerar_inventario_lgpd.py` a partir do catálogo. Não editar à mão.",
        f"> Regenerado em {date.today().isoformat()}.",
        "",
        "Este documento é a **parte factual** do RIPD: o que o sistema trata, de que",
        "natureza, onde fica e por quanto tempo. O que ele **não** é: a avaliação de",
        "risco, a base legal por finalidade e a aprovação — essas continuam sendo do",
        "DPO e do jurídico, e são o que falta para fechar A12.",
        "",
        "---",
        "",
        "## 1. Resumo",
        "",
        "| | |",
        "|---|---|",
        f"| Tipos documentais no catálogo | {len(complemento)} |",
        f"| Que tratam dado pessoal | **{len(pessoais)}** |",
        f"| Dos quais, dado **sensível** (saúde) | **{len(por_sigilo['PESSOAL_SENSIVEL'])}** |",
        f"| Sem dado pessoal (pessoa jurídica) | {len(por_sigilo['INTERNO']) + len(por_sigilo['PUBLICO_CLIENTE'])} |",
        "",
        "**Titulares:** colaboradores alocados nos contratos de prestação de serviço",
        "(±890 pessoas, conforme a volumetria do cap. 16) e seus dependentes, quando",
        "houver plano de saúde.",
        "",
        "**Finalidade:** comprovar, perante o contratante, o cumprimento das obrigações",
        "trabalhistas, previdenciárias e fiscais que condicionam o faturamento.",
        "",
        "**Base legal indicada** (a confirmar pelo jurídico): art. 7º, II (cumprimento de",
        "obrigação legal ou regulatória) e art. 7º, V (execução de contrato), com",
        "art. 11, II, 'a' e 'g' para os dados de saúde. **Não** se apoia em consentimento:",
        "o tratamento é condição do contrato e do vínculo, e consentimento nessa relação",
        "não seria livre.",
        "",
        "---",
        "",
        "## 2. Categorias de dado por tipo documental",
        "",
        "### 2.1 Dado sensível — saúde",
        "",
        "Exigem o tratamento mais restritivo: acesso apenas aos papéis com MFA",
        "(cap. 14.4), tarjamento obrigatório antes do book do cliente (LGPD-03) e",
        "cifragem em repouso.",
        "",
        "| Código | Nome | Escopo | Onde fica |",
        "|---|---|---|---|",
    ]
    for c in sorted(por_sigilo["PESSOAL_SENSIVEL"], key=lambda x: x["COD_CANONICO"]):
        cat = catalogo[c["COD_CANONICO"]]
        L.append(f"| `{c['COD_CANONICO']}` | {cat['NOME_OFICIAL'][:52]} | {c['ESCOPO'].title()} "
                 f"| OwnCloud → repositório interno |")

    L += [
        "",
        "### 2.2 Dado pessoal comum",
        "",
        "| Código | Nome | Escopo | Categorias prováveis |",
        "|---|---|---|---|",
    ]
    for c in sorted(por_sigilo["PESSOAL"], key=lambda x: x["COD_CANONICO"]):
        cat = catalogo[c["COD_CANONICO"]]
        fam = cat["FAMILIA"]
        categorias = {
            "Folha": "Nome, CPF, matrícula, remuneração, descontos",
            "Rescisão": "Nome, CPF, datas de vínculo, verbas rescisórias",
            "Férias": "Nome, matrícula, período aquisitivo, remuneração",
            "13º salário": "Nome, matrícula, remuneração",
            "Benefícios": "Nome, matrícula, adesão a benefício",
            "FGTS": "CPF, remuneração, recolhimento individualizado",
            "Admissão": "Nome, CPF, documentos de identificação",
            "Operação e medição": "Nome, matrícula, jornada",
        }.get(fam, "Nome, matrícula")
        L.append(f"| `{c['COD_CANONICO']}` | {cat['NOME_OFICIAL'][:44]} | {c['ESCOPO'].title()} | {categorias} |")

    L += [
        "",
        "---",
        "",
        "## 3. Onde o dado pessoal vive no sistema",
        "",
        "| Local | O que guarda | Proteção em vigor |",
        "|---|---|---|",
        "| `profissional.cpf_cifrado` | CPF | AES-256-GCM, chave no cofre (SEC-02) |",
        "| `profissional.cpf_hash` | CPF, para busca e unicidade | HMAC-SHA256 com chave no cofre — hash simples permitiria enumerar o espaço de 10¹¹ CPFs |",
        "| `profissional.nome` | Nome | Acesso por papel; mascarado na UI conforme o papel |",
        "| `documento` (binário no bucket) | O documento em si | Cifragem em repouso; URL assinada ≤ 15 min; acesso registrado (SEC-04) |",
        "| `campo_extraido.valor` | Valores extraídos do documento | Mesmo controle de acesso do documento de origem |",
        "| `book` (bucket de evidências) | Cópia publicada | Tarjamento antes da cópia para o cliente (LGPD-03) |",
        "| `log_auditoria` | Quem acessou o quê | Append-only; contém identificador do ator, não dado do titular |",
        "| `notificacao` | Envio da régua | **Não** contém dado pessoal nem anexo — só link autenticado (cap. 11.2) |",
        "",
        "---",
        "",
        '## 4. Retenção — o mecanismo existe; os prazos, não',
        '',
        '> **Decisão registrada em A08: retenção sem tempo determinado — e, desde a',
        '> V020, um caminho para sair dela sem trocar código.**',
        '',
        '### 4.1 O que passou a existir',
        '',
        'A tabela `temporalidade` declara, por classe de dado: de que **marco** o prazo',
        'conta, **quantos meses**, qual a **ação** ao vencer, e o **fundamento** escrito.',
        'Um job mensal (`EXPURGO_POR_TEMPORALIDADE`) a aplica e registra o que eliminou',
        'em `expurgo` / `expurgo_item`, ambas **append-only**.',
        '',
        '| Alvo | Marco proposto | Prazo | Ação | Estado |',
        '|---|---|---|---|---|',
        '| `ACESSO_OBSERVADO` | Registro | 12 meses | EXPURGAR | Proposta, **não aprovada** |',
        '| `NOTIFICACAO` | Registro | 60 meses | EXPURGAR | Proposta, **não aprovada** |',
        '| `DOCUMENTO` | Desligamento | 60 meses | **REVISAR** | Proposta, **não aprovada** |',
        '| `PROFISSIONAL` | Desligamento | 60 meses | ANONIMIZAR | Proposta, **não aprovada**, e **sem executor** |',
        '',
        '`log_auditoria` e `book` **não podem** receber temporalidade: a lista de alvos é',
        'fechada por CHECK. A trilha guarda o identificador do *ator*, não do titular',
        '(seção 3), e é append-only desde a V002; o book se retém no armazenamento',
        '(`retencao_modo`), e destruí-lo exige antes levantar o `LEGAL_HOLD`, que é ato',
        'humano de papel autorizado.',
        '',
        '### 4.2 Nada é eliminado hoje, e o sistema diz isso',
        '',
        'Nenhuma das quatro classes está aprovada. Logo, **o expurgo não apaga nada** — o',
        'que é o comportamento correto e também o modo de falha mais perigoso possível:',
        'um job que roda todo mês, elimina zero e sai verde faria esta não conformidade',
        'sobreviver anos sem sintoma.',
        '',
        'Por isso os dois desfechos são distinguíveis por construção:',
        '',
        '- *"Nada venceu"* → linha em `expurgo` com `itens = 0`.',
        '- *"Ninguém aprovou"* → **não pode** virar linha (ver 4.3), e sai como recusa',
        '  nomeada no log em WARN, no `detalhe` da execução e no campo `ressalva` de',
        '  `GET /api/retencao`.',
        '',
        '### 4.3 A autorização é uma trava do banco',
        '',
        '`expurgo.autorizado_em` é NOT NULL e é copiada de `temporalidade.aprovado_em`',
        'pelo próprio INSERT, **no mesmo comando que o DELETE**. Classe não aprovada tem',
        '`aprovado_em` nula → violação de NOT NULL → o comando aborta → o DELETE não',
        'acontece. Não há caminho que elimine sem registrar, nem registro possível sem',
        'alguém com nome ter aprovado o prazo.',
        '',
        '### 4.4 O marco é onde se erra',
        '',
        'A prescrição do art. 7º, XXIX da CF corre da **extinção do contrato de',
        'trabalho**, não da competência. Um documento de 2020-01 de alguém desligado em',
        '2029 ainda é prova em 2034; contado da competência, teria sido apagado em 2025.',
        'Cada alvo declara a única data que possui, e uma política que pede marco que o',
        'alvo não tem é **recusada** em vez de reinterpretada.',
        '',
        'Efeito colateral que o DPO precisa conhecer: **um profissional desligado cujo',
        '`desligamento` nunca foi registrado no cadastro parece estar na casa para',
        'sempre** — e o seu dado nunca vence. O erro é na direção segura (retenção a',
        'mais, nunca prova a menos), mas é retenção indevida e depende da qualidade do',
        'cadastro de RH, não do SGDF.',
        '',
        '### 4.5 O que ainda é `LEGAL_HOLD`, e por quê',
        '',
        'O book continua selado em `LEGAL_HOLD` — protegido indefinidamente e',
        '**reversível** por papel autorizado. Migrar para `COMPLIANCE` (irreversível até',
        'a data, nem a conta raiz reduz) deve acontecer **uma única vez**, depois da',
        'temporalidade aprovada, porque o caminho não tem volta.',
        '',
        '### 4.6 A ressalva que o DPO precisa avaliar',
        '',
        'Enquanto os quatro prazos não forem aprovados, o SGDF guarda dado pessoal por',
        'prazo indeterminado, o que tensiona:',
        '',
        '- **art. 6º, III (necessidade)** — o tratamento deve limitar-se ao mínimo',
        '  necessário para a finalidade;',
        '- **art. 15, I e art. 16** — o dado deve ser eliminado quando a finalidade se',
        '  exaure, salvo hipóteses legais de guarda;',
        '- **art. 18, IV e VI** — o titular pode pedir anonimização ou eliminação.',
        '',
        'A finalidade **tem** termo natural: a prescrição quinquenal do art. 7º, XXIX da',
        'Constituição. Aprovar cada classe é um `UPDATE temporalidade SET aprovado_em,',
        'aprovado_por` — **cadastro, não deploy**: o jurídico não depende de release.',
        "---",
        "",
        "## 5. O que falta para fechar A12",
        "",
        "| Item | Responsável |",
        "|---|---|",
        "| Confirmar a classificação de sigilo dos 51 tipos (achado **E-05**; hoje derivada, `CONFIRMADO=NAO`) | Gestão de Contratos + DAF |",
        "| Confirmar a base legal por finalidade | Jurídico |",
        "| Avaliação de risco e medidas de mitigação (o RIPD propriamente) | DPO |",
        "| **Aprovar as 4 classes de temporalidade** (**A08**) — o mecanismo está pronto e recusa cada uma pelo nome | Jurídico + DPO |",
        "| Migração do book de `LEGAL_HOLD` para `COMPLIANCE`, depois das aprovações | Jurídico |",
        "| Decidir se `DOCUMENTO` algum dia passa de REVISAR a EXPURGAR — hoje o sistema recusa a segunda | DPO + Jurídico |",
        "| Definir o fluxo de atendimento a pedido de titular sobre documento já publicado em book | DPO + Jurídico |",
        "| Contrato de operador com o contratante, quando aplicável | Jurídico |",
        "",
        "O último item merece atenção: ao publicar o book para o contratante, a",
        "Engesoftware transfere dado pessoal de seus colaboradores a um terceiro. A",
        "relação entre controladores precisa estar formalizada, e é o tarjamento",
        "(**F2-07**) que limita o que efetivamente sai.",
        "",
    ]

    SAIDA.write_text("\n".join(L), encoding="utf-8")
    print(f"{SAIDA}")
    print(f"  tipos com dado pessoal .. {len(pessoais)} de {len(complemento)}")
    print(f"  dos quais sensíveis ..... {len(por_sigilo['PESSOAL_SENSIVEL'])}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
