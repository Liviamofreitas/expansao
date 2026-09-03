#!/usr/bin/env python3
"""Exporta as abas do Anexo 1 (.xlsx) para CSV versionáveis em dados/.

Os CSV alimentam a migration de carga inicial (cap. 17 da documentação) e
tornam o diff entre versões do anexo revisável em code review.

Uso:  python3 tools/exportar_anexo1.py [caminho_do_xlsx]
"""
import csv
import re
import sys
import unicodedata
from pathlib import Path

PADRAO = Path("docs/anexos/Anexo1_Diagnostico_Checklist_Faturamento.xlsx")
DESTINO = Path("dados")


def slug(texto: str) -> str:
    texto = unicodedata.normalize("NFKD", texto).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "_", texto.lower()).strip("_")


def main() -> int:
    try:
        import openpyxl
    except ImportError:
        print("openpyxl não instalado: pip install openpyxl", file=sys.stderr)
        return 1

    origem = Path(sys.argv[1]) if len(sys.argv) > 1 else PADRAO
    if not origem.exists():
        print(f"anexo não encontrado: {origem}", file=sys.stderr)
        return 1

    DESTINO.mkdir(exist_ok=True)
    planilha = openpyxl.load_workbook(origem, data_only=True)

    for aba in planilha.worksheets:
        caminho = DESTINO / f"{slug(aba.title)}.csv"
        with caminho.open("w", newline="", encoding="utf-8") as arquivo:
            escritor = csv.writer(arquivo)
            for linha in aba.iter_rows(values_only=True):
                if all(c is None or str(c).strip() == "" for c in linha):
                    continue
                escritor.writerow(["" if c is None else str(c).strip() for c in linha])
        print(caminho)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
