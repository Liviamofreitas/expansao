#!/usr/bin/env python3
"""Executa a suíte de conformidade do prazo estruturado contra uma implementação.

    python3 especificacao/prazo/verificar.py            # usa a de referência
    python3 especificacao/prazo/verificar.py --json     # saída para o CI

Para verificar OUTRA implementação (a definitiva, quando A13 for decidida),
exponha um executável que leia um caso em JSON no stdin e escreva
{"data": "AAAA-MM-DD", "avisos": [...]} ou {"erro": "CODIGO"} no stdout, e passe
o caminho em --comando. É assim que a suíte permanece agnóstica de linguagem.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

AQUI = Path(__file__).parent
sys.path.insert(0, str(AQUI))

from referencia import Calendario, PrazoInvalido, resolver_prazo  # noqa: E402


def executar_referencia(caso, feriados):
    cal = Calendario.de_iso(feriados[caso["calendario"]])
    try:
        r = resolver_prazo(caso["prazo"], caso["contexto"], cal)
        return {"data": r.data.isoformat(), "avisos": list(r.avisos)}
    except PrazoInvalido as e:
        return {"erro": e.codigo}


def executar_comando(comando, caso, feriados):
    entrada = json.dumps(
        {"prazo": caso["prazo"], "contexto": caso["contexto"],
         "feriados": feriados[caso["calendario"]]},
        ensure_ascii=False,
    )
    p = subprocess.run(comando, shell=True, input=entrada, capture_output=True, text=True)
    if p.returncode != 0:
        return {"erro": f"PROCESSO_FALHOU: {p.stderr.strip()[:200]}"}
    try:
        return json.loads(p.stdout)
    except json.JSONDecodeError:
        return {"erro": f"SAIDA_NAO_JSON: {p.stdout.strip()[:200]}"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--comando", help="executável a verificar; omitir usa a implementação de referência")
    ap.add_argument("--json", action="store_true", help="saída em JSON")
    args = ap.parse_args()

    suite = json.loads((AQUI / "casos.json").read_text(encoding="utf-8"))
    feriados = suite["feriados"]
    resultados = []

    for caso in suite["casos"]:
        obtido = (executar_comando(args.comando, caso, feriados) if args.comando
                  else executar_referencia(caso, feriados))

        if "erro" in caso:
            passou = obtido.get("erro") == caso["erro"]
            esperado_txt, obtido_txt = f"erro {caso['erro']}", f"erro {obtido.get('erro')}" if "erro" in obtido else obtido.get("data", "?")
        else:
            avisos_esperados = sorted(caso.get("avisos", []))
            passou = (obtido.get("data") == caso["esperado"]
                      and sorted(obtido.get("avisos", [])) == avisos_esperados)
            esperado_txt = caso["esperado"] + (f" {avisos_esperados}" if avisos_esperados else "")
            obtido_txt = (obtido.get("data", "") + (f" {sorted(obtido.get('avisos', []))}" if obtido.get("avisos") else "")
                          if "erro" not in obtido else f"erro {obtido['erro']}")

        resultados.append({"id": caso["id"], "passou": passou,
                           "esperado": esperado_txt, "obtido": obtido_txt,
                           "descricao": caso["descricao"]})

    falhas = [r for r in resultados if not r["passou"]]

    if args.json:
        print(json.dumps({"total": len(resultados), "falhas": len(falhas),
                          "resultados": resultados}, ensure_ascii=False, indent=2))
    else:
        for r in resultados:
            marca = "ok  " if r["passou"] else "FALHA"
            print(f"  {marca} {r['id']:<16} {r['obtido']}")
            if not r["passou"]:
                print(f"        esperado: {r['esperado']}")
                print(f"        {r['descricao'][:100]}")
        print()
        print(f"{len(resultados) - len(falhas)}/{len(resultados)} casos passaram.")

    return 1 if falhas else 0


if __name__ == "__main__":
    raise SystemExit(main())
