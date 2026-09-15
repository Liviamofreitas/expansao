-- =============================================================================
-- V021 — a regra de reconhecimento passa a MORAR no banco, com exemplos.
--
-- POR QUE ESTA MIGRAÇÃO EXISTE
--
-- Até aqui o classificador usava `CargaDeRegras.todas()` — regras compiladas em
-- Java. A tabela `regra_reconhecimento` existia, era semeada pela V103 e NINGUÉM
-- A LIA em tempo de execução. Medido: 18 tipos no código, 14 no seed, e zero
-- leituras em src/main.
--
-- A consequência prática: cadastrar um tipo novo pela aplicação gravaria numa
-- tabela que não muda nada. A varredura continuaria reconhecendo exatamente os
-- mesmos 18 tipos compilados.
--
-- O QUE SE PERDE AO TIRAR A REGRA DO CÓDIGO, E O QUE PAGA POR ISSO
--
-- Hoje as 20 regras são revisadas em code review e cobertas por testes contra
-- documentos reais. Uma regra digitada num formulário não teria teste nenhum —
-- e uma regra de reconhecimento errada não falha alto: ela classifica errado, em
-- silêncio, e o erro só aparece no book.
--
-- O EXEMPLO é o que paga essa conta. Ele deixa de ser documentação e vira a
-- verificação: a regra é recusada no cadastro se não reconhecer o próprio
-- exemplo, e recusada se roubar o exemplo de outro tipo. O teste sai do código e
-- vai para o dado, junto com a regra que ele prova.
-- =============================================================================

-- --- 1. como o tipo é identificado ------------------------------------------
--
-- O cap. 8.5 separa duas coisas que a tabela tratava como uma só. A maioria dos
-- tipos é reconhecida por ÂNCORA — termos estruturais no conteúdo. Os
-- comprovantes bancários, não: "sem âncora fixa — identificado pelo pareamento".
-- Eles reconhecem a família; qual obrigação o comprovante paga é o pareamento
-- que decide.
--
-- Essa distinção existia, mas implícita em QUAL ARQUIVO JAVA a regra morava:
-- `CargaDeRegras.corporativas()` ia para o seed, `comprovantesBancarios()` não.
-- Um teste guardava a diferença. Com a regra no banco, a distinção precisa ser
-- um dado — senão a próxima pessoa a cadastrar um comprovante vai exigir dele
-- uma âncora que o capítulo diz que ele não tem.
ALTER TABLE regra_reconhecimento
    ADD COLUMN identificacao text NOT NULL DEFAULT 'ANCORA';

ALTER TABLE regra_reconhecimento
    ADD CONSTRAINT regra_recon_identificacao CHECK (identificacao IN ('ANCORA', 'PAREAMENTO'));

COMMENT ON COLUMN regra_reconhecimento.identificacao IS
    'ANCORA: reconhecido por termos no conteúdo. PAREAMENTO: família reconhecida, '
    'obrigação decidida pelo pareamento (cap. 8.5 — comprovantes bancários).';

-- ÂNCORA SEM ÂNCORA É CLASSIFICAR PELO NOME DO ARQUIVO.
--
-- O construtor de RegraDeReconhecimento já recusa isso em Java, com estas
-- palavras: "classificaria pelo nome do arquivo, que é exatamente o que o
-- sistema existe para não fazer". Com a regra virando dado editável, a garantia
-- precisa estar onde o dado está — um INSERT direto no banco não passa pelo
-- construtor Java.
ALTER TABLE regra_reconhecimento
    ADD CONSTRAINT regra_recon_ancora_obrigatoria CHECK (
        identificacao <> 'ANCORA' OR jsonb_array_length(ancoras) > 0
    );

-- --- 2. a coluna que só existia no Java -------------------------------------
--
-- `RegraDeReconhecimento.pesoPorCampo` nunca teve coluna. Enquanto o banco era
-- um espelho não lido isso não aparecia; passando a ser a fonte, a ausência
-- viraria um peso silenciosamente zerado em toda regra carregada.
ALTER TABLE regra_reconhecimento
    ADD COLUMN peso_por_campo numeric(4, 3) NOT NULL DEFAULT 0.100;

ALTER TABLE regra_reconhecimento
    ADD CONSTRAINT regra_recon_peso_campo CHECK (peso_por_campo BETWEEN 0 AND 1);

-- --- 3. os exemplos ---------------------------------------------------------
--
-- TEXTO, E NÃO ARQUIVO, E A ESCOLHA É DO CAP. 17.
--
-- Guardar documentos reais como exemplo permanente seria manter massa real
-- dentro do sistema por tempo indeterminado — exatamente o que o cap. 17 e o
-- cap. 19 impedem. As âncoras são termos ESTRUTURAIS ("CERTIDÃO NEGATIVA DE
-- DÉBITOS", "RECEITA FEDERAL"), não dado pessoal: um trecho representativo
-- basta para provar a regra e não carrega ninguém junto.
--
-- `deve_reconhecer` existe porque exemplo negativo é tão necessário quanto
-- positivo. "Esta regra reconhece a CND da Receita" é metade da afirmação; a
-- outra metade é "e NÃO reconhece a CNDT", que é onde os erros reais moram.
CREATE TABLE regra_exemplo (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    regra_id         uuid        NOT NULL REFERENCES regra_reconhecimento (id) ON DELETE CASCADE,
    rotulo           text        NOT NULL,
    texto            text        NOT NULL,
    deve_reconhecer  boolean     NOT NULL,
    criado_em        timestamptz NOT NULL DEFAULT now(),
    criado_por       text        NOT NULL,

    CONSTRAINT regra_exemplo_rotulo_unico UNIQUE (regra_id, rotulo),

    -- Um exemplo curto demais não prova nada: casaria com qualquer coisa e
    -- daria à regra um aval que ela não conquistou.
    CONSTRAINT regra_exemplo_texto_util CHECK (length(btrim(texto)) >= 40),

    -- SEM CPF NO EXEMPLO, E A GARANTIA É ESTRUTURAL.
    --
    -- O exemplo fica gravado, é lido na tela de cadastro e roda a cada nova
    -- regra cadastrada. Um CPF colado aqui por descuido viraria dado pessoal
    -- guardado fora de todo o controle de retenção e de mascaramento que o
    -- resto do sistema aplica. A recusa é do esquema porque a aplicação não é
    -- o único caminho até esta tabela.
    CONSTRAINT regra_exemplo_sem_cpf CHECK (
        texto !~ '[0-9]{3}\.?[0-9]{3}\.?[0-9]{3}-?[0-9]{2}'
    )
);

CREATE INDEX ix_regra_exemplo_regra ON regra_exemplo (regra_id);

COMMENT ON TABLE regra_exemplo IS
    'O que a regra deve e não deve reconhecer. É a verificação da regra, não '
    'a sua documentação: o cadastro recusa regra que não reconhece o próprio '
    'exemplo positivo, ou que reconhece um exemplo negativo, ou que rouba o '
    'exemplo positivo de outro tipo.';

COMMENT ON COLUMN regra_exemplo.deve_reconhecer IS
    'true: a regra TEM de reconhecer este texto. false: TEM de recusá-lo.';
