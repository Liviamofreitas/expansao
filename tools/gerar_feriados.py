#!/usr/bin/env python3
"""Gera o calendário de feriados do SGDF (história F0-06, cap. 7.3).

Saídas   dados/feriados.csv
         db/seed/V101__calendario_feriados.sql

Os feriados móveis derivam da Páscoa (algoritmo de Meeus/Jones/Butcher para o
calendário gregoriano). Os fixos vêm da legislação federal; os estaduais, da
legislação da UF.

ATENÇÃO — dias facultativos. Carnaval (segunda e terça) e Corpus Christi NÃO são
feriados nacionais: são ponto facultativo federal. Entram no calendário porque na
prática ninguém trabalha, mas ficam marcados com o prefixo "[FACULTATIVO]" na
descrição para que a área demandante possa removê-los por cadastro. A escolha tem
efeito direto no prazo: contá-los como úteis adianta a data (mais seguro para o
cumprimento) e a faz cair em dia sem expediente (pior para a operação).
Ver docs/ERRATA-V1.md, achado E-07.
"""
import csv
import sys
from datetime import date, timedelta
from pathlib import Path

ANO_INICIAL, ANO_FINAL = 2025, 2032
SAIDA_CSV = Path("dados/feriados.csv")
SAIDA_SQL = Path("db/seed/V101__calendario_feriados.sql")
ATOR = "carga-inicial-feriados"


def pascoa(ano: int) -> date:
    """Domingo de Páscoa no calendário gregoriano (Meeus/Jones/Butcher)."""
    a = ano % 19
    b, c = divmod(ano, 100)
    d, e = divmod(b, 4)
    f = (b + 8) // 25
    g = (b - f + 1) // 3
    h = (19 * a + b - d - g + 15) % 30
    i, k = divmod(c, 4)
    l = (32 + 2 * e + 2 * i - h - k) % 7
    m = (a + 11 * h + 22 * l) // 451
    mes, dia = divmod(h + l - 7 * m + 114, 31)
    return date(ano, mes, dia + 1)


# (mês, dia, descrição) — feriados nacionais fixos.
NACIONAIS_FIXOS = [
    (1, 1, "Confraternização Universal"),
    (4, 21, "Tiradentes"),
    (5, 1, "Dia do Trabalho"),
    (9, 7, "Independência do Brasil"),
    (10, 12, "Nossa Senhora Aparecida"),
    (11, 2, "Finados"),
    (11, 15, "Proclamação da República"),
    (11, 20, "Dia Nacional de Zumbi e da Consciência Negra"),
    (12, 25, "Natal"),
]

# (UF, mês, dia, descrição) — estaduais das UFs com contrato ativo.
ESTADUAIS_FIXOS = [
    ("CE", 3, 25, "Data Magna do Ceará"),
    ("RS", 9, 20, "Revolução Farroupilha"),
]


def feriados_do_ano(ano: int):
    p = pascoa(ano)
    yield from ((date(ano, m, d), None, desc) for m, d, desc in NACIONAIS_FIXOS)
    yield (p - timedelta(days=2), None, "Sexta-Feira Santa")
    yield (p - timedelta(days=48), None, "[FACULTATIVO] Carnaval (segunda-feira)")
    yield (p - timedelta(days=47), None, "[FACULTATIVO] Carnaval (terça-feira)")
    yield (p + timedelta(days=60), None, "[FACULTATIVO] Corpus Christi")
    yield from ((date(ano, m, d), uf, desc) for uf, m, d, desc in ESTADUAIS_FIXOS)


def sql(valor) -> str:
    return "NULL" if valor is None else "'" + str(valor).replace("'", "''") + "'"


def main() -> int:
    linhas = sorted(
        (d, uf, desc)
        for ano in range(ANO_INICIAL, ANO_FINAL + 1)
        for d, uf, desc in feriados_do_ano(ano)
    )

    SAIDA_CSV.parent.mkdir(parents=True, exist_ok=True)
    with SAIDA_CSV.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["DATA", "UF", "DESCRICAO"])
        w.writerows([d.isoformat(), uf or "", desc] for d, uf, desc in linhas)

    corpo = [
        "-- ===========================================================================",
        "-- SGDF — V101 · Calendário de feriados",
        "-- GERADO por tools/gerar_feriados.py — NÃO EDITAR À MÃO.",
        "--",
        f"-- História F0-06 (cap. 7.3). Anos {ANO_INICIAL}–{ANO_FINAL}.",
        "-- Móveis derivados da Páscoa; fixos da legislação federal e estadual.",
        "-- Linhas com [FACULTATIVO] na descrição são ponto facultativo federal, não",
        "-- feriado: mantê-las ou não é decisão de cadastro. Ver ERRATA E-07.",
        "--",
        "-- Feriados MUNICIPAIS não estão aqui: dependem do município de cada",
        "-- contrato e não constam de nenhuma fonte do pacote. Cadastrar antes de",
        "-- usar prazo em dia útil para contratos cujo município tenha feriado local.",
        "-- ===========================================================================",
        "",
        "BEGIN;",
        "",
        "INSERT INTO calendario_feriados (uf, municipio, data, descricao, criado_por)",
        "VALUES",
    ]
    corpo.append(",\n".join(
        f"    ({sql(uf)}, NULL, '{d.isoformat()}', {sql(desc)}, {sql(ATOR)})"
        for d, uf, desc in linhas
    ))
    corpo += [
        "ON CONFLICT DO NOTHING;",
        "",
        "INSERT INTO log_auditoria (ator, papel, acao, objeto_tipo, resultado, detalhe)",
        f"VALUES ({sql(ATOR)}, 'MIGRACAO', 'CARGA_FERIADOS', 'calendario_feriados', 'SUCESSO',",
        f"        '{{\"linhas\": {len(linhas)}, \"ano_inicial\": {ANO_INICIAL}, "
        f'"ano_final": {ANO_FINAL}}}\'::jsonb);',
        "",
        "COMMIT;",
        "",
    ]
    SAIDA_SQL.parent.mkdir(parents=True, exist_ok=True)
    SAIDA_SQL.write_text("\n".join(corpo), encoding="utf-8")

    print(f"{SAIDA_CSV} e {SAIDA_SQL}")
    print(f"  {len(linhas)} linhas, {ANO_INICIAL}–{ANO_FINAL}")
    for ano in (2026,):
        print(f"  Páscoa {ano}: {pascoa(ano)}  ·  Sexta-Feira Santa: {pascoa(ano) - timedelta(days=2)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
