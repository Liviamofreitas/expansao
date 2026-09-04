#!/usr/bin/env python3
"""Implementação de REFERÊNCIA do prazo estruturado — cap. 7.3, história F0-06.

Isto NÃO é código de produção. É o oráculo da suíte de conformidade
(`casos.json`): a implementação definitiva, na linguagem que a pendência A13
decidir, tem de produzir exatamente os mesmos resultados para os mesmos casos.

Mantida em Python porque o repositório já usa Python para geradores e porque
não prejulga a stack.

--------------------------------------------------------------------------------
SEMÂNTICA DA CONTAGEM — a ambiguidade do cap. 7.3 e como está resolvida aqui
--------------------------------------------------------------------------------
O documento dá dois exemplos que não podem ser satisfeitos pela mesma regra:

    {INICIO_COMPETENCIA, CORRIDO, 21}  = "até o dia 21"   -> ORDINAL
    {ATESTE,             CORRIDO,  3}  = "D+3 da política" -> ADITIVA

Sob contagem aditiva, o primeiro daria dia 22 (1 + 21). Sob contagem ordinal, o
segundo daria o próprio dia do ateste mais dois. Logo, `offset` tem duas
semânticas, escolhidas pela âncora:

    INICIO_COMPETENCIA          -> ORDINAL  ("o N-ésimo dia do mês")
    ATESTE, SOLICITACAO_FATURAMENTO, EVENTO -> ADITIVA ("base + N dias")

Esta leitura é a única que satisfaz os dois exemplos do próprio documento, e é
coerente com os dados do Anexo 1: as 95 linhas com âncora "Início da competência"
usam limites de 1 a 31 (ordinais de dia do mês) e as 81 com "Ateste do cliente"
usam limite 0 (D+0, "sob faturamento").

Confirmar com a área demandante — ver docs/ERRATA-V1.md, achado E-06.

--------------------------------------------------------------------------------
ÂNCORA FIM_COMPETENCIA
--------------------------------------------------------------------------------
Oito linhas do Anexo 1 pedem o dia 30 ou 31 da competência ("ENTRE DIA 20 A 30",
"ENTRE DIA 30 A 31"). Em fevereiro esses dias não existem. Sob leitura ordinal
estrita, a abertura do ciclo de fevereiro falharia para seis contratos.

A intenção real dessas linhas é "até o fim do mês", e é isso que a âncora
FIM_COMPETENCIA expressa — decisão tomada sobre o achado E-08. Ela é ADITIVA,
com base no último dia da competência:

    {FIM_COMPETENCIA, CORRIDO, 0}  -> último dia do mês
    {FIM_COMPETENCIA, UTIL,    0}  -> último dia ÚTIL do mês
    {FIM_COMPETENCIA, CORRIDO, 5}  -> 5 dias corridos após o fim do mês

Atenção ao sentido do arredondamento: em dia útil, esta âncora rola para TRÁS,
não para frente. "Último dia útil de abril" tem de cair em abril; rolar para
frente cairia em maio e mudaria a competência do prazo. É o inverso do que
fazem as âncoras de evento, e é intencional.

O ajuste AJUSTE_FIM_DE_PERIODO permanece como rede de segurança para cadastros
antigos ou malfeitos que ainda usem offset ordinal alto: o princípio 1 do cap. 1
diz que o sistema nunca trava o faturamento por falta de configuração própria.
Com as 8 linhas recadastradas (dados/correcoes_matriz.csv), o aviso deixa de
aparecer na operação normal.
"""
from __future__ import annotations

from calendar import monthrange
from dataclasses import dataclass
from datetime import date, timedelta

ANCORAS_ORDINAIS = {"INICIO_COMPETENCIA"}
ANCORAS_ADITIVAS = {"ATESTE", "SOLICITACAO_FATURAMENTO", "EVENTO"}
ANCORAS_FIM = {"FIM_COMPETENCIA"}
ANCORAS = ANCORAS_ORDINAIS | ANCORAS_ADITIVAS | ANCORAS_FIM
TIPOS_DIA = {"UTIL", "CORRIDO"}


class PrazoInvalido(Exception):
    """Cadastro de prazo malformado ou insatisfazível. Carrega um código estável."""

    def __init__(self, codigo: str, detalhe: str = "") -> None:
        super().__init__(f"{codigo}: {detalhe}" if detalhe else codigo)
        self.codigo = codigo


@dataclass(frozen=True)
class Resolucao:
    """Resultado da resolução: a data e os ajustes que precisaram ser feitos.

    Devolver os avisos junto com a data é deliberado. Um ajuste que não aparece
    em lugar nenhum vira uma data inexplicável na tela do operador seis meses
    depois; quem chama grava os avisos na trilha e os exibe ao lado do prazo.
    """

    data: date
    avisos: tuple[str, ...] = ()


@dataclass(frozen=True)
class Calendario:
    """Feriados aplicáveis a um contrato, já resolvidos para a sua UF e município.

    A resolução por escopo (nacional + estadual + municipal) acontece na consulta
    ao cadastro, não aqui: esta classe recebe o conjunto final de datas não úteis.
    """

    feriados: frozenset[date]

    @classmethod
    def de_iso(cls, datas) -> "Calendario":
        return cls(frozenset(date.fromisoformat(d) for d in datas))

    def e_util(self, d: date) -> bool:
        return d.weekday() < 5 and d not in self.feriados

    def proximo_util(self, d: date) -> date:
        while not self.e_util(d):
            d += timedelta(days=1)
        return d

    def anterior_util(self, d: date) -> date:
        while not self.e_util(d):
            d -= timedelta(days=1)
        return d


def _ordinal(inicio: date, fim: date, offset: int, tipo_dia: str, cal: Calendario) -> Resolucao:
    """N-ésimo dia (útil ou corrido) do intervalo [inicio, fim].

    Se o período não tiver `offset` dias do tipo pedido, devolve o último dia
    disponível com o aviso AJUSTE_FIM_DE_PERIODO (ver cabeçalho do módulo).
    """
    if offset < 1:
        raise PrazoInvalido(
            "OFFSET_ORDINAL_INVALIDO",
            f"âncora ordinal exige offset >= 1, recebido {offset}",
        )
    contados, d, ultimo = 0, inicio, None
    while d <= fim:
        if tipo_dia == "CORRIDO" or cal.e_util(d):
            contados += 1
            ultimo = d
            if contados == offset:
                return Resolucao(d)
        d += timedelta(days=1)
    if ultimo is None:
        raise PrazoInvalido(
            "PERIODO_SEM_DIA_UTIL",
            f"o período {inicio}..{fim} não tem nenhum dia do tipo {tipo_dia}",
        )
    return Resolucao(ultimo, ("AJUSTE_FIM_DE_PERIODO",))


def _aditivo(base: date, offset: int, tipo_dia: str, cal: Calendario) -> Resolucao:
    """base + offset dias. Em dia útil, a própria base rola para o próximo útil."""
    if offset < 0:
        raise PrazoInvalido("OFFSET_NEGATIVO", f"offset deve ser >= 0, recebido {offset}")
    if tipo_dia == "CORRIDO":
        return Resolucao(base + timedelta(days=offset))
    d = cal.proximo_util(base)
    avisos = ("AJUSTE_BASE_NAO_UTIL",) if d != base else ()
    for _ in range(offset):
        d = cal.proximo_util(d + timedelta(days=1))
    return Resolucao(d, avisos)


def _fim_competencia(fim: date, offset: int, tipo_dia: str, cal: Calendario) -> Resolucao:
    """Último dia da competência, mais offset.

    Em dia útil, a base rola para TRÁS (ver cabeçalho): o "último dia útil de
    abril" tem de ficar em abril.
    """
    if offset < 0:
        raise PrazoInvalido("OFFSET_NEGATIVO", f"offset deve ser >= 0, recebido {offset}")
    if tipo_dia == "CORRIDO":
        return Resolucao(fim + timedelta(days=offset))
    d = cal.anterior_util(fim)
    for _ in range(offset):
        d = cal.proximo_util(d + timedelta(days=1))
    return Resolucao(d)


def resolver_prazo(prazo: dict, contexto: dict, calendario: Calendario) -> Resolucao:
    """Resolve um prazo estruturado. Função pura, sem acesso a relógio nem a banco.

    prazo    {"ancora": ..., "tipo_dia": "UTIL"|"CORRIDO", "offset": int}
    contexto {"competencia": "AAAA-MM", "ateste": "AAAA-MM-DD", ...}
    """
    for campo in ("ancora", "tipo_dia", "offset"):
        if campo not in prazo:
            raise PrazoInvalido("CAMPO_AUSENTE", campo)

    ancora, tipo_dia, offset = prazo["ancora"], prazo["tipo_dia"], prazo["offset"]

    if ancora not in ANCORAS:
        raise PrazoInvalido("ANCORA_DESCONHECIDA", ancora)
    if tipo_dia not in TIPOS_DIA:
        raise PrazoInvalido("TIPO_DIA_DESCONHECIDO", tipo_dia)
    if not isinstance(offset, int) or isinstance(offset, bool):
        raise PrazoInvalido("OFFSET_NAO_INTEIRO", repr(offset))

    if ancora in ANCORAS_ORDINAIS:
        competencia = contexto.get("competencia")
        if not competencia:
            raise PrazoInvalido("CONTEXTO_AUSENTE", "competencia")
        try:
            ano, mes = (int(p) for p in competencia.split("-"))
            primeiro = date(ano, mes, 1)
        except (ValueError, TypeError):
            raise PrazoInvalido("COMPETENCIA_INVALIDA", str(competencia)) from None
        ultimo = date(ano, mes, monthrange(ano, mes)[1])
        return _ordinal(primeiro, ultimo, offset, tipo_dia, calendario)

    if ancora in ANCORAS_FIM:
        competencia = contexto.get("competencia")
        if not competencia:
            raise PrazoInvalido("CONTEXTO_AUSENTE", "competencia")
        try:
            ano, mes = (int(p) for p in competencia.split("-"))
            ultimo = date(ano, mes, monthrange(ano, mes)[1])
        except (ValueError, TypeError):
            raise PrazoInvalido("COMPETENCIA_INVALIDA", str(competencia)) from None
        return _fim_competencia(ultimo, offset, tipo_dia, calendario)

    chave = {
        "ATESTE": "ateste",
        "SOLICITACAO_FATURAMENTO": "solicitacao_faturamento",
        "EVENTO": "evento",
    }[ancora]
    bruto = contexto.get(chave)
    if not bruto:
        # Cap. 7.3: documentos "sob faturamento" só entram na régua de cobrança
        # APÓS o evento. Sem o evento, não há prazo — e isso não é erro de
        # cadastro, é ausência de gatilho. Quem chama trata como "sem prazo ainda".
        raise PrazoInvalido("ANCORA_SEM_EVENTO", chave)
    try:
        base = date.fromisoformat(bruto)
    except (ValueError, TypeError):
        raise PrazoInvalido("DATA_BASE_INVALIDA", str(bruto)) from None

    return _aditivo(base, offset, tipo_dia, calendario)
