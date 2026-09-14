-- Rollback da V020.
--
-- ATENÇÃO: desfaz o mecanismo de expurgo E o registro do que já foi expurgado.
-- O segundo é grave: depois do rollback, uma eliminação executada não tem mais
-- como ser demonstrada. Se houver linha em `expurgo`, exporte-a antes.
--
-- As RULEs impedem DELETE mas não DROP TABLE — o dono da tabela continua dono.
BEGIN;
DROP TABLE IF EXISTS expurgo_item;
DROP TABLE IF EXISTS expurgo;
DROP TABLE IF EXISTS temporalidade;
COMMIT;
