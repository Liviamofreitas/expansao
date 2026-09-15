-- =============================================================================
-- V108 — a regra passa a ser lida do banco, e esta carga diz o que fica onde.
--
-- Até a V021 o classificador usava `CargaDeRegras.todas()`: regras compiladas.
-- A tabela `regra_reconhecimento` era um espelho que ninguém lia. Medido:
-- 18 tipos no código, 14 no seed.
--
-- POR QUE NÃO HÁ NENHUM CMP.* AQUI, E POR QUE ISSO NÃO É OMISSÃO
--
-- A primeira versão desta carga tentava inserir as cinco regras de comprovante
-- bancário. Os INSERT rodaram sem erro e gravaram ZERO LINHAS: eram
-- `INSERT ... SELECT FROM tipo_documental WHERE codigo = 'CMP.x'`, e
-- CMP.DARF, CMP.BOLETO, CMP.TRANSFERENCIA e CMP.LOTE_SALARIOS NÃO EXISTEM em
-- `tipo_documental`. Nenhuma linha casou, nenhum erro apareceu.
--
-- E a ausência está certa. `tipo_documental` é o CHECKLIST — o que um contrato
-- deve entregar. Um comprovante bancário não é item de checklist: é a prova de
-- que um item foi pago. O cap. 8.5 diz que ele não tem âncora fixa e é
-- "identificado pelo pareamento", e `documento.tipo_id` é anulável exatamente
-- porque comprovante não carrega tipo documental.
--
-- Daí a fronteira, que vale mais que este arquivo:
--
--   A REGRA MORA JUNTO DO QUE ELA IDENTIFICA.
--
--   tipo do checklist        -> é cadastro -> regra no BANCO, editável
--   família de comprovante   -> não é cadastro -> regra no CÓDIGO, com teste
--
-- Não são duas verdades sobre a mesma coisa: são duas coisas diferentes, cada
-- uma onde faz sentido. Empurrar os comprovantes para dentro de
-- `tipo_documental` só para unificar a origem os faria aparecer no checklist,
-- que é o oposto do que o capítulo manda.
-- =============================================================================

-- --- peso_por_campo, vindo do código ----------------------------------------
--
-- A coluna nasceu na V021 com default 0.100 porque nunca existiu no banco.
-- Estes valores são os que o código usava; sem eles, toda regra carregada
-- passaria a pontuar campo com um peso que ninguém escolheu.
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'BEN.RELACAO_PLANO_SAUDE';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'BEN.RELACAO_VA_VR';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.CNDT';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.CND_CIVEL_CRIMINAL';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.CND_ESTADUAL';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.CND_FALENCIA';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.CND_RFB';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.CRF_FGTS';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'CER.SICAF';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'FGT.GUIA';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'FGT.RELATORIO_DIGITAL';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'FOL.CONTRACHEQUE';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'FOL.FOPAG';
UPDATE regra_reconhecimento r SET peso_por_campo = 0.0 FROM tipo_documental t WHERE t.id = r.tipo_id AND t.codigo = 'INS.DCTFWEB';

-- --- os primeiros exemplos --------------------------------------------------
--
-- SÃO UM PAR, E O PAR É O PONTO.
--
-- "A regra da CND da Receita reconhece este texto" é metade da afirmação. A
-- outra metade — a que pega os erros de verdade — é "e NÃO reconhece a CNDT",
-- que é outra certidão negativa, de layout parecido, de outro órgão. Duas
-- regras que se confundem produzem o pior defeito de classificação: o documento
-- entra, é aceito, e satisfaz a exigência errada.
--
-- Os textos são os mesmos que a suíte de testes usa. São trechos ESTRUTURAIS —
-- nome de órgão, título da certidão, termos do corpo. Nenhum CPF: a constraint
-- `regra_exemplo_sem_cpf` recusaria, e é do esquema justamente porque esta
-- tabela é relida a cada regra nova.
--
-- O conjunto cresce com o cadastro: cada regra nova traz os seus e passa a ser
-- verificada contra todos os que já existem.

INSERT INTO regra_exemplo (regra_id, rotulo, texto, deve_reconhecer, criado_por)
SELECT r.id, 'CND da Receita Federal', 'MINISTERIO DA FAZENDA Secretaria da Receita Federal do Brasil Procuradoria-Geral da Fazenda Nacional CERTIDAO POSITIVA COM EFEITOS DE NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS E A DIVIDA ATIVA DA UNIAO Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Ressalvado o direito de a Fazenda Nacional cobrar e inscrever quaisquer dividas de responsabilidade do sujeito passivo acima identificada que vierem a ser apuradas. Validade: 20/12/2026', true, 'carga-v108'
  FROM regra_reconhecimento r JOIN tipo_documental t ON t.id = r.tipo_id
 WHERE t.codigo = 'CER.CND_RFB' AND r.ativo;

INSERT INTO regra_exemplo (regra_id, rotulo, texto, deve_reconhecer, criado_por)
SELECT r.id, 'CND da Receita Federal (nao e CNDT)', 'MINISTERIO DA FAZENDA Secretaria da Receita Federal do Brasil Procuradoria-Geral da Fazenda Nacional CERTIDAO POSITIVA COM EFEITOS DE NEGATIVA DE DEBITOS RELATIVOS AOS TRIBUTOS FEDERAIS E A DIVIDA ATIVA DA UNIAO Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Ressalvado o direito de a Fazenda Nacional cobrar e inscrever quaisquer dividas de responsabilidade do sujeito passivo acima identificada que vierem a ser apuradas. Validade: 20/12/2026', false, 'carga-v108'
  FROM regra_reconhecimento r JOIN tipo_documental t ON t.id = r.tipo_id
 WHERE t.codigo = 'CER.CNDT' AND r.ativo;

INSERT INTO regra_exemplo (regra_id, rotulo, texto, deve_reconhecer, criado_por)
SELECT r.id, 'CNDT do TST', 'PODER JUDICIARIO JUSTICA DO TRABALHO CERTIDAO NEGATIVA DE DEBITOS TRABALHISTAS Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Certidao no: 12345678/2026 Expedicao: 01/07/2026 Validade: 28/12/2026 Certifica-se que ENGESOFTWARE TECNOLOGIA S/A NAO CONSTA do Banco Nacional de Devedores Trabalhistas.', true, 'carga-v108'
  FROM regra_reconhecimento r JOIN tipo_documental t ON t.id = r.tipo_id
 WHERE t.codigo = 'CER.CNDT' AND r.ativo;

INSERT INTO regra_exemplo (regra_id, rotulo, texto, deve_reconhecer, criado_por)
SELECT r.id, 'CNDT do TST (nao e CND da Receita)', 'PODER JUDICIARIO JUSTICA DO TRABALHO CERTIDAO NEGATIVA DE DEBITOS TRABALHISTAS Nome: ENGESOFTWARE TECNOLOGIA S/A CNPJ: 03.681.946/0001-30 Certidao no: 12345678/2026 Expedicao: 01/07/2026 Validade: 28/12/2026 Certifica-se que ENGESOFTWARE TECNOLOGIA S/A NAO CONSTA do Banco Nacional de Devedores Trabalhistas.', false, 'carga-v108'
  FROM regra_reconhecimento r JOIN tipo_documental t ON t.id = r.tipo_id
 WHERE t.codigo = 'CER.CND_RFB' AND r.ativo;

-- --- a carga confere o que gravou -------------------------------------------
--
-- O motivo desta guarda está no cabeçalho: um `INSERT ... SELECT` que não casa
-- nada é um sucesso silencioso. Uma carga que promete e não entrega tem de
-- gritar aqui, não na primeira varredura que classificar errado.
DO $$
DECLARE
    n_regras   integer;
    n_exemplos integer;
BEGIN
    SELECT count(*) INTO n_regras FROM regra_reconhecimento WHERE ativo;
    IF n_regras < 14 THEN
        RAISE EXCEPTION 'V108: esperava ao menos 14 regras ativas, achei %', n_regras;
    END IF;

    SELECT count(*) INTO n_exemplos FROM regra_exemplo;
    IF n_exemplos <> 4 THEN
        RAISE EXCEPTION 'V108: esperava 4 exemplos, achei % — algum INSERT ... SELECT '
            'nao casou tipo nenhum', n_exemplos;
    END IF;
END $$;
