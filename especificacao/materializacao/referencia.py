#!/usr/bin/env python3
"""Implementação de REFERÊNCIA da abertura de ciclo — cap. 7.1, história F0-07.

Oráculo da suíte `casos.json`, como em especificacao/prazo. Não é código de
produção.

A abertura de ciclo é decomposta em duas partes, e só a primeira está aqui:

    1. RESOLVER  (função pura)  — dado contrato, competência, matriz, tipos e
       alocações, decidir QUAIS exigências existem e com que prazo.
    2. PERSISTIR (efeito)       — gravar o resultado, congelar versao_matriz_id
       no ciclo, abrir pendências.

A decomposição não é estética: a parte 1 concentra toda a regra de negócio e é
testável sem banco, sem relógio e sem fila. A parte 2 é mecânica. Misturar as
duas produziria a situação em que a única forma de testar a regra é subir a
infraestrutura inteira.

--------------------------------------------------------------------------------
DUAS DECISÕES QUE O CAP. 7.1 NÃO FECHA
--------------------------------------------------------------------------------
1. PRAZO DA EXIGÊNCIA CORPORATIVA. Ela é compartilhada entre os ciclos, mas o
   prazo vem de uma regra que pode variar por contrato. Se o BNB quer a CND no
   5º dia útil e a CEF no 10º, a CND compartilhada precisa estar lá no 5º.
   Adotado o MENOR prazo entre os contratos que a exigem. Ver ERRATA E-10.

2. ALOCAÇÃO "ATIVA NA COMPETÊNCIA". O cap. 7.1 diz "profissional alocado ativo
   na competência" sem definir o critério. Adotada a INTERSEÇÃO: basta um dia de
   sobreposição entre o período de alocação e o mês. Quem foi desligado no dia 3
   trabalhou 3 dias, tem contracheque e encargos — não exigir seria deixar
   passar exatamente o caso que a responsabilidade subsidiária alcança.
   Ver ERRATA E-10.
"""
from __future__ import annotations

import importlib.util
import sys
from calendar import monthrange
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path


def _carregar(nome: str, caminho: Path):
    """Carrega um módulo por caminho.

    As duas especificações têm um arquivo `referencia.py` cada; um import por
    sys.path pegaria o módulo errado conforme a ordem de invocação.
    """
    spec = importlib.util.spec_from_file_location(nome, caminho)
    modulo = importlib.util.module_from_spec(spec)
    # Registrar ANTES de executar: @dataclass resolve as anotações consultando
    # sys.modules[cls.__module__], e sem o registro isso estoura em módulos que
    # usam `from __future__ import annotations`.
    sys.modules[nome] = modulo
    spec.loader.exec_module(modulo)
    return modulo


_prazo = _carregar("sgdf_prazo", Path(__file__).parent.parent / "prazo" / "referencia.py")
Calendario, PrazoInvalido, resolver_prazo = (
    _prazo.Calendario, _prazo.PrazoInvalido, _prazo.resolver_prazo
)

ESCOPOS = {"CORPORATIVO", "CONTRATO", "PROFISSIONAL"}


class AberturaInvalida(Exception):
    def __init__(self, codigo: str, detalhe: str = "") -> None:
        super().__init__(f"{codigo}: {detalhe}" if detalhe else codigo)
        self.codigo = codigo


@dataclass(frozen=True)
class Exigencia:
    tipo: str
    escopo: str
    evento: str
    criticidade: str
    responsavel: str
    prazo: date | None
    profissional: str | None = None
    condicional_grupo: str | None = None
    avisos: tuple[str, ...] = ()

    def chave(self) -> tuple:
        return (self.tipo, self.evento, self.profissional)


@dataclass
class Abertura:
    """Resultado da resolução: o que criar, e o que o operador precisa saber."""

    exigencias: list[Exigencia] = field(default_factory=list)
    alertas: list[str] = field(default_factory=list)


def _limites(competencia: str) -> tuple[date, date]:
    try:
        ano, mes = (int(p) for p in competencia.split("-"))
        return date(ano, mes, 1), date(ano, mes, monthrange(ano, mes)[1])
    except (ValueError, TypeError):
        raise AberturaInvalida("COMPETENCIA_INVALIDA", str(competencia)) from None


def _vigente(regra: dict, inicio: date, fim: date) -> bool:
    """Cap. 7.1 passo 1: regra vigente na competência.

    A vigência é comparada contra o MÊS inteiro, não contra o dia 1: uma regra
    que passa a valer no meio do mês vale para aquela competência.
    """
    v_ini = date.fromisoformat(regra["vigencia_ini"])
    v_fim = date.fromisoformat(regra["vigencia_fim"]) if regra.get("vigencia_fim") else None
    return v_ini <= fim and (v_fim is None or v_fim >= inicio)


def _resolver_conflitos(regras: list[dict], contrato: str) -> list[dict]:
    """Cap. 7.1 passo 2: contrato sobrepõe modalidade; a mais específica vence.

    O desempate é por (tipo, evento), não por tipo: um mesmo tipo documental
    pode ter regras distintas para eventos distintos, e colapsá-las descartaria
    exigências legítimas.
    """
    escolhidas: dict[tuple, dict] = {}
    for r in regras:
        chave = (r["tipo"], r.get("evento", "*"))
        atual = escolhidas.get(chave)
        if atual is None:
            escolhidas[chave] = r
            continue
        # CONTRATO vence MODALIDADE. Empate no mesmo alvo é erro de cadastro:
        # escolher em silêncio esconderia matriz ambígua.
        if r["alvo"] == atual["alvo"]:
            raise AberturaInvalida(
                "REGRA_AMBIGUA",
                f"duas regras de alvo {r['alvo']} para {chave} no contrato {contrato}",
            )
        if r["alvo"] == "CONTRATO":
            escolhidas[chave] = r
    return list(escolhidas.values())


def _alocados(alocacoes: list[dict], inicio: date, fim: date) -> list[str]:
    """Profissionais com ao menos um dia de alocação dentro da competência."""
    ativos = []
    for a in alocacoes:
        a_ini = date.fromisoformat(a["inicio"])
        a_fim = date.fromisoformat(a["fim"]) if a.get("fim") else None
        if a_ini <= fim and (a_fim is None or a_fim >= inicio):
            ativos.append(a["matricula"])
    return sorted(set(ativos))


def abrir_ciclo(entrada: dict) -> Abertura:
    """Resolve as exigências de um ciclo. Função pura.

    entrada = {
      "contrato":    {"numero", "modalidade", "empresa_cnpj"},
      "competencia": "AAAA-MM",
      "tipos":       {"COD": {"escopo", "evento", "criticidade", "condicional_grupo"}},
      "matriz":      {"versao", "regras": [...]},
      "alocacoes":   [{"matricula", "inicio", "fim"}],
      "contexto":    {"ateste": ...},          # âncoras de evento, quando houver
      "feriados":    ["AAAA-MM-DD", ...]
    }
    """
    for campo in ("contrato", "competencia", "tipos", "matriz"):
        if campo not in entrada:
            raise AberturaInvalida("CAMPO_AUSENTE", campo)

    contrato = entrada["contrato"]
    competencia = entrada["competencia"]
    inicio, fim = _limites(competencia)
    tipos = entrada["tipos"]
    calendario = Calendario.de_iso(entrada.get("feriados", []))
    contexto = dict(entrada.get("contexto", {}))
    contexto["competencia"] = competencia

    # Passo 1: regras vigentes que alcançam este contrato.
    aplicaveis = [
        r for r in entrada["matriz"]["regras"]
        if _vigente(r, inicio, fim)
        and ((r["alvo"] == "MODALIDADE" and r["alvo_id"] == contrato["modalidade"])
             or (r["alvo"] == "CONTRATO" and r["alvo_id"] == contrato["numero"]))
    ]

    # Passo 2: desempate por especificidade.
    regras = _resolver_conflitos(aplicaveis, contrato["numero"])

    resultado = Abertura()
    alocados = _alocados(entrada.get("alocacoes", []), inicio, fim)

    for regra in sorted(regras, key=lambda r: (r["tipo"], r.get("evento", ""))):
        if regra["obrigatoriedade"] == "DISPENSADO":
            continue

        tipo = tipos.get(regra["tipo"])
        if tipo is None:
            raise AberturaInvalida("TIPO_DESCONHECIDO", regra["tipo"])
        escopo = tipo["escopo"]
        if escopo not in ESCOPOS:
            raise AberturaInvalida("ESCOPO_DESCONHECIDO", f"{regra['tipo']}: {escopo}")

        try:
            resolucao = resolver_prazo(regra["prazo"], contexto, calendario)
            prazo, avisos = resolucao.data, resolucao.avisos
        except PrazoInvalido as e:
            if e.codigo == "ANCORA_SEM_EVENTO":
                # Cap. 7.3: documento "sob faturamento" existe, mas ainda não tem
                # prazo. A exigência é criada sem prazo e fica fora da régua até
                # o evento ocorrer. Suprimi-la esconderia o que falta.
                prazo, avisos = None, ("PRAZO_AGUARDA_EVENTO",)
            else:
                raise AberturaInvalida("PRAZO_INVALIDO", f"{regra['tipo']}: {e.codigo}") from None

        comum = {
            "tipo": regra["tipo"],
            "escopo": escopo,
            "evento": tipo["evento"],
            "criticidade": regra.get("criticidade") or tipo["criticidade"],
            "responsavel": regra["responsavel"],
            "prazo": prazo,
            "condicional_grupo": tipo.get("condicional_grupo"),
            "avisos": avisos,
        }

        if escopo == "PROFISSIONAL":
            if not alocados:
                resultado.alertas.append(
                    f"{regra['tipo']}: escopo profissional sem alocado ativo na competência"
                )
                continue
            resultado.exigencias.extend(
                Exigencia(**comum, profissional=m) for m in alocados
            )
        else:
            # CORPORATIVO e CONTRATO geram uma exigência cada. A diferença está
            # no endereçamento na persistência (V004), não na contagem aqui.
            resultado.exigencias.append(Exigencia(**comum))

    if not resultado.exigencias:
        resultado.alertas.append("nenhuma exigência materializada para a competência")

    return resultado


def prazo_corporativo(prazos: list[date | None]) -> date | None:
    """Prazo de uma exigência corporativa compartilhada: o menor entre os ciclos.

    Ver decisão 1 no cabeçalho. Prazos ausentes (aguardando evento) não contam.
    """
    definidos = [p for p in prazos if p is not None]
    return min(definidos) if definidos else None
