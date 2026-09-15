#!/usr/bin/env sh
# Aplica o esquema e a carga inicial num banco vazio. NÃO roda os testes de
# aceite — eles inserem e apagam dados, e não têm lugar num ambiente que vai
# receber uso.
#
# Chamado pelo serviço `migracao` do docker-compose. Usa as variáveis PG* do
# ambiente.
#
# TRÊS ESTADOS, E O TERCEIRO É UMA RECUSA
#
# Sem controle de versão de migração (Flyway ainda é pendência de stack), a
# única coisa honesta a fazer é olhar o banco e decidir:
#
#   vazio                -> aplica tudo
#   completo             -> não faz nada, e diz que não fez
#   parcialmente migrado -> RECUSA, alto e claro
#
# O terceiro caso é o que importa. Reaplicar migrations por cima de um banco
# meio migrado falharia no meio e deixaria um estado pior que o inicial; pular
# em silêncio deixaria a aplicação subir contra um esquema incompleto. Nenhuma
# das duas é aceitável, então este script para e manda alguém olhar.
set -eu

MARCO_INICIO=cliente        # existe depois da V001
MARCO_FIM=temporalidade     # existe depois da V020, a última

existe() {
    test "$(psql -tAc "SELECT to_regclass('public.$1') IS NOT NULL")" = t
}

if existe "$MARCO_FIM"; then
    echo "esquema já aplicado (encontrei '$MARCO_FIM'). Nada a fazer."
    exit 0
fi

if existe "$MARCO_INICIO"; then
    echo "RECUSADO: o banco está PARCIALMENTE migrado." >&2
    echo "  '$MARCO_INICIO' existe, '$MARCO_FIM' não." >&2
    echo "  Reaplicar por cima falharia no meio e deixaria um estado pior." >&2
    echo "  Restaure o banco ou complete a migração à mão antes de subir." >&2
    exit 1
fi

echo "banco vazio — aplicando esquema e carga."
for arquivo in /db/migracoes/V*__*.sql; do
    case "$arquivo" in *.rollback.sql) continue ;; esac
    echo "→ $(basename "$arquivo")"
    psql -v ON_ERROR_STOP=1 -q -f "$arquivo"
done
for arquivo in /db/seed/V*__*.sql; do
    echo "→ $(basename "$arquivo")"
    psql -v ON_ERROR_STOP=1 -q -f "$arquivo"
done

# A verificação final é do próprio critério que este script usa para decidir se
# já rodou. Se ela falhar aqui, o serviço para e a aplicação não sobe — em vez
# de subir contra um esquema que se acha completo e não está.
existe "$MARCO_FIM" || { echo "ERRO: apliquei tudo e '$MARCO_FIM' não existe." >&2; exit 1; }
echo "esquema e carga aplicados."
