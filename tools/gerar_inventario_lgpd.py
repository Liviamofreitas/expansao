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
        "## 4. Retenção — o ponto aberto mais grave",
        "",
        "> **Decisão registrada em A08: retenção sem tempo determinado.**",
        "",
        "O sistema a implementa como `LEGAL_HOLD`: o book fica protegido",
        "indefinidamente, mas de forma **reversível** por papel autorizado. O modo",
        "`COMPLIANCE`, que é irreversível até a data — nem a conta raiz reduz —,",
        "existe mas não é o padrão.",
        "",
        "**A ressalva que o DPO precisa avaliar.** Guardar dado pessoal por prazo",
        "indeterminado é tratamento sem termo final definido, o que tensiona:",
        "",
        "- **art. 6º, III (necessidade)** — o tratamento deve limitar-se ao mínimo",
        "  necessário para a finalidade;",
        "- **art. 15, I e art. 16** — o dado deve ser eliminado quando a finalidade",
        "  se exaure, salvo hipóteses legais de guarda;",
        "- **art. 18, IV e VI** — o titular pode pedir anonimização ou eliminação, e",
        "  um book sob retenção irreversível impede o atendimento.",
        "",
        "A finalidade aqui (prova de regularidade trabalhista) **tem** termo natural:",
        "a prescrição quinquenal do art. 7º, XXIX da Constituição. É esse o prazo que",
        "a tabela de temporalidade tende a fixar.",
        "",
        "**Enquanto a temporalidade não existir, `LEGAL_HOLD` é a escolha certa** —",
        "protege a evidência sem tornar a decisão irreversível. Migrar para",
        "`COMPLIANCE` deve acontecer **uma única vez**, depois da tabela aprovada,",
        "porque o caminho não tem volta.",
        "",
        "---",
        "",
        "## 5. O que falta para fechar A12",
        "",
        "| Item | Responsável |",
        "|---|---|",
        "| Confirmar a classificação de sigilo dos 51 tipos (achado **E-05**; hoje derivada, `CONFIRMADO=NAO`) | Gestão de Contratos + DAF |",
        "| Confirmar a base legal por finalidade | Jurídico |",
        "| Avaliação de risco e medidas de mitigação (o RIPD propriamente) | DPO |",
        "| Tabela de temporalidade (**A08**) e migração para `COMPLIANCE` | Jurídico |",
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
