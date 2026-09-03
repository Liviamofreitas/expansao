#!/usr/bin/env python3
"""Gera a carga inicial do SGDF a partir do Anexo 1 (história F0-03).

Entradas   dados/catalogo_documentos.csv         (aba CATALOGO_DOCUMENTOS)
           dados/complemento_tipo_documental.csv (campos que o anexo não carrega)
Saída      db/seed/V100__carga_inicial.sql

O que NÃO é gerado, e por quê:

  · contrato_servico e regra_exigibilidade — a coluna CONTRATO do Anexo 1 é
    cliente, não contrato-serviço (achado E-04), e o desdobramento previsto em
    D-02 depende das pendências A03 e A04. Semear aqui obrigaria a inventar os
    15 contratos-serviço.
  · regra_conciliacao — os códigos R01..R12 têm semânticas conflitantes entre o
    cap. 9 e o Anexo 1 (achado E-01). Semear qualquer das duas seria escolher
    por suposição.

Aliases em colisão (achado E-02) são emitidos comentados, nunca resolvidos por
desempate automático: a restrição tipo_alias_normalizado_unico rejeitaria a
segunda linha, e escolher qual sobrevive é decisão da área demandante.
"""
import csv
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

CATALOGO = Path("dados/catalogo_documentos.csv")
COMPLEMENTO = Path("dados/complemento_tipo_documental.csv")
SAIDA = Path("db/seed/V100__carga_inicial.sql")
ATOR = "carga-inicial-anexo1"

EVENTO = {
    "Mensal": "MENSAL", "13º": "13O", "Férias": "FERIAS",
    "Rescisão": "RESCISAO", "Admissão": "ADMISSAO", "Eventual": "EVENTUAL",
}
DEFASAGEM = {
    "Mês da competência": "M",
    "Competência anterior (M-1)": "M_MENOS_1",
    "Válida na data do faturamento": "VIGENCIA_NF",
    "Data do evento": "EVENTO",
}
CRITICIDADE = {"Bloqueante": "BLOQUEANTE", "Não bloqueante": "NAO_BLOQUEANTE"}


def normalizar(texto: str) -> str:
    """Normalização do cap. 5.1: sem acento, minúsculas, separadores colapsados."""
    texto = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", texto.lower()).strip("_")


def sql(valor) -> str:
    if valor is None or valor == "":
        return "NULL"
    return "'" + str(valor).replace("'", "''") + "'"


def main() -> int:
    if not CATALOGO.exists() or not COMPLEMENTO.exists():
        print("execute tools/exportar_anexo1.py primeiro", file=sys.stderr)
        return 1

    catalogo = list(csv.DictReader(CATALOGO.open(encoding="utf-8")))
    complemento = {c["COD_CANONICO"]: c for c in csv.DictReader(COMPLEMENTO.open(encoding="utf-8"))}

    faltando = [c["COD_CANONICO"] for c in catalogo if c["COD_CANONICO"] not in complemento]
    if faltando:
        print(f"sem complemento: {faltando}", file=sys.stderr)
        return 1

    # ---- clientes distintos citados na matriz ------------------------------
    clientes = sorted({
        nome.strip()
        for linha in catalogo
        for nome in linha["CONTRATOS_QUE_EXIGEM"].split(",")
        if nome.strip()
    })

    # ---- aliases, com detecção de colisão (E-02) ---------------------------
    por_normalizado = defaultdict(list)
    for linha in catalogo:
        for bruto in linha["NOMENCLATURAS_ATUAIS (aliases)"].split(";"):
            bruto = bruto.strip()
            if bruto:
                por_normalizado[normalizar(bruto)].append((linha["COD_CANONICO"], bruto))

    aliases, colisoes = [], []
    for chave, ocorrencias in sorted(por_normalizado.items()):
        tipos = {t for t, _ in ocorrencias}
        if len(tipos) > 1:
            colisoes.append((chave, ocorrencias))
        else:
            aliases.append((chave, ocorrencias[0][0], ocorrencias[0][1]))

    # ---- montagem do SQL ---------------------------------------------------
    L = [
        "-- ===========================================================================",
        "-- SGDF — V100 · Carga inicial a partir do Anexo 1",
        "-- GERADO por tools/gerar_carga_inicial.py — NÃO EDITAR À MÃO.",
        "-- Regenerar:  python3 tools/gerar_carga_inicial.py",
        "--",
        "-- História F0-03. Cap. 17: seed como migration de dados auditável.",
        "-- Idempotente: ON CONFLICT DO NOTHING em toda inserção.",
        "-- ===========================================================================",
        "",
        "BEGIN;",
        "",
        "-- --- modalidades (cap. 5.1) ------------------------------------------------",
        "INSERT INTO modalidade (codigo, nome, criado_por) VALUES",
        "    ('OUTSOURCING', 'Outsourcing com alocação de profissionais', " + sql(ATOR) + "),",
        "    ('SUSTENTACAO', 'Sustentação de sistemas', " + sql(ATOR) + "),",
        "    ('ENTREGA',     'Entrega por produto ou escopo fechado', " + sql(ATOR) + "),",
        "    ('MISTO',       'Misto', " + sql(ATOR) + ")",
        "ON CONFLICT (codigo) DO NOTHING;",
        "",
        f"-- --- clientes ({len(clientes)}) --------------------------------------------",
        "-- CNPJ e esfera pendentes de cadastro: o Anexo 1 não os carrega. O CNPJ é",
        "-- obrigatório e único, então cada cliente entra com um marcador que a tela de",
        "-- cadastro (F0-02) substitui. Nenhum CNPJ é inventado.",
    ]

    for i, nome in enumerate(clientes, start=1):
        L.append(
            f"INSERT INTO cliente (nome, cnpj, esfera, ativo, criado_por) "
            f"VALUES ({sql(nome)}, '{i:014d}', 'PUBLICA', false, {sql(ATOR)}) "
            f"ON CONFLICT (cnpj) DO NOTHING;"
        )

    L += [
        "",
        "-- --- versão inicial da matriz ---------------------------------------------",
        "INSERT INTO versao_matriz (numero, publicada_por, motivo) VALUES",
        f"    ('1.0', {sql(ATOR)}, 'Carga inicial do catálogo canônico a partir do Anexo 1')",
        "ON CONFLICT (numero) DO NOTHING;",
        "",
        f"-- --- tipos documentais ({len(catalogo)}) ------------------------------------",
        "-- escopo, sigilo, formatos e condicional_grupo vêm de",
        "-- dados/complemento_tipo_documental.csv: são DERIVADOS, não confirmados",
        "-- (coluna CONFIRMADO=NAO). Ver docs/ERRATA-V1.md, achado E-05.",
        "INSERT INTO tipo_documental",
        "    (codigo, nome, familia, escopo, evento, defasagem, formatos, criticidade,",
        "     sigilo, fonte_mestre, condicional_grupo, criado_por)",
        "VALUES",
    ]

    linhas = []
    for x in catalogo:
        c = complemento[x["COD_CANONICO"]]
        formatos = "[" + ", ".join(f'"{f}"' for f in c["FORMATOS"].split("|")) + "]"
        linhas.append(
            f"    ({sql(x['COD_CANONICO'])}, {sql(x['NOME_OFICIAL'])}, {sql(x['FAMILIA'])}, "
            f"{sql(c['ESCOPO'])}, {sql(EVENTO[x['EVENTO']])}, "
            f"{sql(DEFASAGEM[x['COMPETENCIA_REFERENCIA']])}, {sql(formatos)}::jsonb, "
            f"{sql(CRITICIDADE[x['CRITICIDADE_SUGERIDA']])}, {sql(c['SIGILO'])}, "
            f"{sql(x['FONTE_MESTRE'])}, {sql(c['CONDICIONAL_GRUPO'])}, {sql(ATOR)})"
        )
    L.append(",\n".join(linhas))
    L += ["ON CONFLICT (codigo) DO NOTHING;", ""]

    L += [
        f"-- --- aliases legados ({len(aliases)} distintos de "
        f"{sum(len(v) for v in por_normalizado.values())} brutos) -------------------",
        "-- Cap. 8.3: o alias dá bônus de score ao reconhecimento.",
        "INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)",
        "VALUES",
    ]
    linhas = [
        f"    ((SELECT id FROM tipo_documental WHERE codigo = {sql(cod)}), "
        f"{sql(bruto)}, {sql(chave)}, 'LEGADO', {sql(ATOR)})"
        for chave, cod, bruto in aliases
    ]
    L.append(",\n".join(linhas))
    L += ["ON CONFLICT (texto_normalizado) DO NOTHING;", ""]

    if colisoes:
        L += [
            "-- ---------------------------------------------------------------------",
            "-- ALIASES EM COLISÃO — NÃO CARREGADOS (achado E-02)",
            "--",
            "-- Cada um aponta para mais de um tipo canônico. Sob a normalização do",
            "-- cap. 5.1 eles colapsam na mesma chave, e a restrição",
            "-- tipo_alias_normalizado_unico rejeita a segunda linha.",
            "--",
            "-- Não são resolvidos aqui por desempate automático: escolher qual tipo",
            "-- fica com o alias é decisão da área demandante. Descomente a linha",
            "-- correta depois da decisão e regenere.",
            "-- ---------------------------------------------------------------------",
        ]
        for chave, ocorrencias in colisoes:
            L.append(f"-- colisão em '{chave}':")
            for cod, bruto in ocorrencias:
                L.append(
                    f"--   INSERT INTO tipo_alias (tipo_id, texto_original, texto_normalizado, origem, criado_por)"
                )
                L.append(
                    f"--   VALUES ((SELECT id FROM tipo_documental WHERE codigo = {sql(cod)}), "
                    f"{sql(bruto)}, {sql(chave)}, 'LEGADO', {sql(ATOR)});"
                )
        L.append("")

    L += [
        "-- --- registro da própria carga na trilha (cap. 5.2) -----------------------",
        "INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, resultado, detalhe)",
        f"VALUES ({sql(ATOR)}, 'MIGRACAO', 'CARGA_INICIAL', 'catalogo', 'SUCESSO',",
        f"        '{{\"tipos\": {len(catalogo)}, \"aliases\": {len(aliases)}, "
        f"\"aliases_em_colisao\": {len(colisoes)}, \"clientes\": {len(clientes)}, "
        f'"fonte": "Anexo 1 v1"}}\'::jsonb);',
        "",
        "COMMIT;",
        "",
    ]

    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text("\n".join(L), encoding="utf-8")

    print(f"{SAIDA}")
    print(f"  clientes ............ {len(clientes)}")
    print(f"  tipos documentais ... {len(catalogo)}")
    print(f"  aliases carregados .. {len(aliases)}")
    print(f"  aliases em colisão .. {len(colisoes)} (comentados — E-02)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
