#!/usr/bin/env python3
"""Executa a suíte de conformidade da abertura de ciclo.

    python3 especificacao/materializacao/verificar.py
    python3 especificacao/materializacao/verificar.py --comando './minha-impl'

O contrato do executável externo é o mesmo da suíte de prazo: lê a entrada
resolvida em JSON no stdin e escreve {"exigencias": [...], "alertas": [...]}
ou {"erro": "CODIGO"} no stdout.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).parent


def _carregar(nome: str, caminho: Path):
    spec = importlib.util.spec_from_file_location(nome, caminho)
    modulo = importlib.util.module_from_spec(spec)
    sys.modules[nome] = modulo
    spec.loader.exec_module(modulo)
    return modulo


_ref = _carregar("sgdf_materializacao", AQUI / "referencia.py")


def montar_entrada(caso, suite):
    """Expande o caso nos valores padrão da suíte."""
    regras = []
    for r in caso["regras"]:
        regras.append({
            "tipo": r["tipo"],
            "alvo": r["alvo"],
            "alvo_id": r["alvo_id"],
            "obrigatoriedade": r.get("obrigatoriedade", "OBRIGATORIO"),
            "criticidade": r.get("criticidade"),
            "prazo": r.get("prazo", suite["prazo_padrao"]),
            "responsavel": r.get("responsavel", "FINANCEIRO"),
            "vigencia_ini": r.get("vigencia_ini", "2025-01-01"),
            "vigencia_fim": r.get("vigencia_fim"),
        })
    return {
        "contrato": caso["contrato"],
        "competencia": caso["competencia"],
        "tipos": suite["tipos_padrao"],
        "matriz": {"versao": "1.0", "regras": regras},
        "alocacoes": caso.get("alocacoes", []),
        "contexto": caso.get("contexto", {}),
        "feriados": caso.get("feriados", suite["feriados_padrao"]),
    }


def executar_referencia(entrada):
    try:
        r = _ref.abrir_ciclo(entrada)
        return {
            "exigencias": [
                {"tipo": e.tipo, "escopo": e.escopo, "profissional": e.profissional,
                 "prazo": e.prazo.isoformat() if e.prazo else None,
                 "criticidade": e.criticidade, "responsavel": e.responsavel,
                 "condicional_grupo": e.condicional_grupo, "avisos": list(e.avisos)}
                for e in r.exigencias
            ],
            "alertas": r.alertas,
        }
    except (_ref.AberturaInvalida,) as e:
        return {"erro": e.codigo}


def executar_comando(comando, entrada):
    p = subprocess.run(comando, shell=True, input=json.dumps(entrada, ensure_ascii=False),
                       capture_output=True, text=True)
    if p.returncode != 0:
        return {"erro": f"PROCESSO_FALHOU: {p.stderr.strip()[:200]}"}
    try:
        return json.loads(p.stdout)
    except json.JSONDecodeError:
        return {"erro": f"SAIDA_NAO_JSON: {p.stdout.strip()[:200]}"}


def comparar(caso, obtido):
    """Compara só os campos que o caso declara — o resto é livre."""
    if "erro" in caso:
        if obtido.get("erro") != caso["erro"]:
            return False, f"esperava erro {caso['erro']}, obteve {obtido.get('erro') or 'sucesso'}"
        return True, ""

    if "erro" in obtido:
        return False, f"erro inesperado: {obtido['erro']}"

    obtidas = obtido["exigencias"]
    esperadas = caso["esperado"]

    if len(obtidas) != len(esperadas):
        return False, (f"esperava {len(esperadas)} exigência(s), obteve {len(obtidas)}: "
                       + ", ".join(f"{e['tipo']}/{e['profissional'] or '-'}" for e in obtidas))

    # Ordem estável: a resolução ordena por (tipo, evento) e por matrícula.
    for esperada, obtida in zip(esperadas, obtidas):
        for campo, valor in esperada.items():
            se_obtido = obtida.get(campo)
            if campo == "avisos":
                if sorted(se_obtido or []) != sorted(valor):
                    return False, f"{esperada['tipo']}: avisos {se_obtido} != {valor}"
            elif se_obtido != valor:
                return False, f"{esperada['tipo']}: {campo} = {se_obtido!r}, esperado {valor!r}"

    for trecho in caso.get("alertas_contem", []):
        if not any(trecho in a for a in obtido.get("alertas", [])):
            return False, f"alerta contendo {trecho!r} não foi emitido"

    return True, ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--comando")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    suite = json.loads((AQUI / "casos.json").read_text(encoding="utf-8"))
    resultados = []

    for caso in suite["casos"]:
        entrada = montar_entrada(caso, suite)
        obtido = (executar_comando(args.comando, entrada) if args.comando
                  else executar_referencia(entrada))
        passou, motivo = comparar(caso, obtido)
        resultados.append({"id": caso["id"], "passou": passou, "motivo": motivo,
                           "descricao": caso["descricao"]})

    falhas = [r for r in resultados if not r["passou"]]

    if args.json:
        print(json.dumps({"total": len(resultados), "falhas": len(falhas),
                          "resultados": resultados}, ensure_ascii=False, indent=2))
    else:
        for r in resultados:
            print(f"  {'ok  ' if r['passou'] else 'FALHA'} {r['id']:<10} {r['motivo']}")
            if not r["passou"]:
                print(f"        {r['descricao'][:110]}")
        print()
        print(f"{len(resultados) - len(falhas)}/{len(resultados)} casos passaram.")

    return 1 if falhas else 0


if __name__ == "__main__":
    raise SystemExit(main())
