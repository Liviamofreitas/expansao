#!/usr/bin/env python3
"""Gera a carga da matriz de exigibilidade — pendências A03/A04, achado E-04.

Entradas   dados/matriz_exigibilidade.csv    (176 linhas do Anexo 1)
           dados/contratos_servico.csv       (desdobramento por contrato-serviço)
           dados/correcoes_matriz.csv        (E-08: as 8 linhas com FIM_COMPETENCIA)
Saída      db/seed/V102__matriz_exigibilidade.sql

DECISÃO SOBRE A03/A04 (matriz separada por contrato-serviço)
------------------------------------------------------------
A coluna CONTRATO do Anexo 1 é CLIENTE, não contrato-serviço (achado E-04).
Como ninguém conferiu ainda se BNB-OUT e BNB-SUS exigem o mesmo, cada
contrato-serviço recebe a sua PRÓPRIA cópia das regras do cliente, com
alvo = CONTRATO.

É o cenário conservador de R-04: se depois se confirmar que são iguais, basta
consolidar em regras de MODALIDADE; o caminho inverso — assumir iguais e
descobrir que divergem — exigiria migração de dados e, no intervalo, o sistema
teria deixado de exigir um documento devido.

Toda regra sai com CONFIRMADO=NAO no CSV de contratos: a cópia é ponto de
partida para a conferência de A03/A04, não o resultado dela.
"""
import csv
import sys
from pathlib import Path

MATRIZ = Path("dados/matriz_exigibilidade.csv")
CONTRATOS = Path("dados/contratos_servico.csv")
CORRECOES = Path("dados/correcoes_matriz.csv")
SAIDA = Path("db/seed/V102__matriz_exigibilidade.sql")
ATOR = "carga-matriz-anexo1"

ANCORA = {"Ateste do cliente": "ATESTE", "Início da competência": "INICIO_COMPETENCIA"}
TIPO_DIA = {"Corrido": "CORRIDO", "Útil": "UTIL"}
CRITICIDADE = {"Bloqueante": "BLOQUEANTE", "Não bloqueante": "NAO_BLOQUEANTE"}
RESPONSAVEL = {"OPERAÇÃO": "OPERACAO", "FINANCEIRO": "FINANCEIRO", "DP": "DP"}


def sql(valor) -> str:
    if valor is None or valor == "":
        return "NULL"
    return "'" + str(valor).replace("'", "''") + "'"


def montar_prazo(linha: dict, correcao: dict | None) -> tuple[str, str]:
    """Devolve (json do prazo, origem). A correção de E-08 vence a linha original."""
    if correcao:
        return (
            f'{{"ancora":"{correcao["PRAZO_ANCORA_NOVA"]}",'
            f'"tipo_dia":"{correcao["PRAZO_TIPO_DIA"]}",'
            f'"offset":{int(correcao["PRAZO_OFFSET"])}}}',
            "E-08",
        )
    ancora = ANCORA.get(linha["PRAZO_ANCORA"])
    tipo_dia = TIPO_DIA.get(linha["PRAZO_TIPO_DIA"])
    if ancora is None or tipo_dia is None:
        raise SystemExit(f"{linha['ID']}: âncora/tipo_dia fora do domínio: "
                         f"{linha['PRAZO_ANCORA']!r}/{linha['PRAZO_TIPO_DIA']!r}")
    offset = int(linha["PRAZO_LIMITE"] or 0)
    # Cap. 7.3: âncora ordinal exige offset >= 1. Linha com 0 e âncora de início
    # é erro de cadastro; parar aqui é melhor que gravar prazo inválido.
    if ancora == "INICIO_COMPETENCIA" and offset < 1:
        raise SystemExit(f"{linha['ID']}: offset ordinal {offset} inválido")
    return f'{{"ancora":"{ancora}","tipo_dia":"{tipo_dia}","offset":{offset}}}', "ANEXO1"


def main() -> int:
    for p in (MATRIZ, CONTRATOS, CORRECOES):
        if not p.exists():
            print(f"faltando: {p}", file=sys.stderr)
            return 1

    matriz = list(csv.DictReader(MATRIZ.open(encoding="utf-8")))
    contratos = list(csv.DictReader(CONTRATOS.open(encoding="utf-8")))
    correcoes = {c["ID_LINHA"]: c for c in csv.DictReader(CORRECOES.open(encoding="utf-8"))}

    por_cliente: dict[str, list[dict]] = {}
    for linha in matriz:
        por_cliente.setdefault(linha["CONTRATO"], []).append(linha)

    orfaos = {c["CLIENTE_MATRIZ"] for c in contratos} - set(por_cliente)
    if orfaos:
        print(f"contrato-serviço sem linhas na matriz: {sorted(orfaos)}", file=sys.stderr)
        return 1
    sem_contrato = set(por_cliente) - {c["CLIENTE_MATRIZ"] for c in contratos}

    L = [
        "-- ===========================================================================",
        "-- SGDF — V102 · Matriz de exigibilidade",
        "-- GERADO por tools/gerar_matriz.py — NÃO EDITAR À MÃO.",
        "--",
        "-- Decisão sobre A03/A04: matriz separada por contrato-serviço. Cada um",
        "-- recebe a sua própria cópia das regras do cliente, com alvo = CONTRATO.",
        "-- A cópia é ponto de partida para a conferência, não o resultado dela.",
        "--",
        "-- Os 8 prazos corrigidos por E-08 usam a âncora FIM_COMPETENCIA.",
        "-- ===========================================================================",
        "",
        "BEGIN;",
        "",
        "-- --- empresa emitente ------------------------------------------------------",
        "-- CNPJ marcador: o Anexo 1 não carrega o CNPJ do prestador e nenhum é",
        "-- inventado. A empresa entra INATIVA; ativá-la exige o CNPJ real (A07).",
        "INSERT INTO empresa (id, razao_social, cnpj, matriz, ativo, criado_por) VALUES",
        f"    ('00000000-0000-4000-8000-000000000001', 'PRESTADOR A CADASTRAR', "
        f"'00000000000000', true, false, {sql(ATOR)})",
        "ON CONFLICT (cnpj) DO NOTHING;",
        "",
        "-- --- versão da matriz ------------------------------------------------------",
        "INSERT INTO versao_matriz (id, numero, publicada_por, motivo) VALUES",
        f"    ('00000000-0000-4000-8000-000000000002', '1.1', {sql(ATOR)},",
        "     'Carga da matriz do Anexo 1 desdobrada por contrato-serviço (A03/A04)')",
        "ON CONFLICT (numero) DO NOTHING;",
        "",
        f"-- --- contratos-serviço ({len(contratos)}) -----------------------------------",
        "-- Entram INATIVOS: ativação exige empresa emitente com CNPJ real (V004).",
    ]

    ids = {}
    for i, c in enumerate(contratos, start=1):
        cid = f"00000000-0000-4000-8001-{i:012d}"
        ids[(c["CLIENTE_MATRIZ"], c["NUMERO"], c["SERVICO"])] = cid
        L.append(
            f"INSERT INTO contrato_servico (id, cliente_id, numero, servico, modalidade_id,\n"
            f"    vigencia_ini, pasta_origem, data_contratual_faturamento, calendario_uf,\n"
            f"    empresa_id, ativo, criado_por)\n"
            f"SELECT '{cid}', cl.id, {sql(c['NUMERO'])}, {sql(c['SERVICO'])}, m.id,\n"
            f"    '2025-01-01', {sql(c['PASTA_ORIGEM'])},\n"
            f"    '{{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\",\"offset\":3}}',\n"
            f"    {sql(c['CALENDARIO_UF'])}, NULL, false, {sql(ATOR)}\n"
            f"FROM cliente cl, modalidade m\n"
            f"WHERE cl.nome = {sql(c['CLIENTE_MATRIZ'])} AND m.codigo = {sql(c['MODALIDADE'])}\n"
            f"ON CONFLICT DO NOTHING;"
        )

    L += ["", "-- --- regras de exigibilidade ----------------------------------------------",
          "INSERT INTO regra_exigibilidade",
          "    (tipo_id, alvo, alvo_contrato_id, obrigatoriedade, criticidade, prazo,",
          "     responsavel_titular, fundamento, vigencia_ini, versao_matriz_id, criado_por)",
          "VALUES"]

    regras, por_e08 = [], 0
    for c in contratos:
        cid = ids[(c["CLIENTE_MATRIZ"], c["NUMERO"], c["SERVICO"])]
        for linha in por_cliente[c["CLIENTE_MATRIZ"]]:
            correcao = correcoes.get(linha["ID"])
            prazo, origem = montar_prazo(linha, correcao)
            if origem == "E-08":
                por_e08 += 1
            resp = RESPONSAVEL.get(linha["RESPONSAVEL"])
            crit = CRITICIDADE.get(linha["CRITICIDADE"])
            if resp is None or crit is None:
                raise SystemExit(f"{linha['ID']}: responsável/criticidade fora do domínio")
            regras.append(
                f"    ((SELECT id FROM tipo_documental WHERE codigo = {sql(linha['COD_CANONICO'])}),\n"
                f"     'CONTRATO', '{cid}', 'OBRIGATORIO', {sql(crit)}, {sql(prazo)}::jsonb,\n"
                f"     {sql(resp)}, NULL, '2025-01-01',\n"
                f"     '00000000-0000-4000-8000-000000000002', {sql(ATOR)})"
            )
    L.append(",\n".join(regras))
    L += [";", ""]

    L += [
        "-- --- registro na trilha ----------------------------------------------------",
        "INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, resultado, detalhe)",
        f"VALUES ({sql(ATOR)}, 'MIGRACAO', 'CARGA_MATRIZ', 'regra_exigibilidade', 'SUCESSO',",
        f"        '{{\"contratos_servico\": {len(contratos)}, \"regras\": {len(regras)}, "
        f'"linhas_anexo1": {len(matriz)}, "prazos_corrigidos_e08": {por_e08}}}\'::jsonb);',
        "",
        "COMMIT;",
        "",
    ]

    SAIDA.parent.mkdir(parents=True, exist_ok=True)
    SAIDA.write_text("\n".join(L), encoding="utf-8")

    print(f"{SAIDA}")
    print(f"  contratos-serviço ... {len(contratos)}")
    print(f"  linhas do Anexo 1 ... {len(matriz)}")
    print(f"  regras geradas ...... {len(regras)}")
    print(f"  prazos por E-08 ..... {por_e08}")
    if sem_contrato:
        print(f"  AVISO clientes da matriz sem contrato-serviço: {sorted(sem_contrato)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
