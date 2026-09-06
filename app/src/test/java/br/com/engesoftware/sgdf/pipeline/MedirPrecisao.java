package br.com.engesoftware.sgdf.pipeline;

import br.com.engesoftware.sgdf.classificacao.CargaDeRegras;
import br.com.engesoftware.sgdf.classificacao.Classificador;
import br.com.engesoftware.sgdf.coleta.VeredictoAntivirus;
import br.com.engesoftware.sgdf.extracao.ExtratorPdfBox;
import br.com.engesoftware.sgdf.validacao.ValidacaoDeSeguranca;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Roda o pipeline sobre a massa real e mede a precisao do cap. 19.
 *
 * <p>Meta: <b>≥ 95% no bloco corporativo antes da 1b</b>. Esta e metade da
 * F3-01, e a metade que NAO depende de conferencia manual nova — o gabarito ja
 * existe, conferido a olho nos documentos de 06 e 07/2026.
 *
 * <p><b>A massa nao esta no repositorio, e isso e o desenho.</b> O cap. 19 pede
 * ambiente controlado, e o RELATORIO_GUIA_DO_FGTS sozinho carrega 160 CPFs com
 * nome e remuneracao individual. O diretorio vem em {@code -Dsgdf.massa}; sem
 * ele o medidor se pula e diz por que. Silencio seria pior: esconderia que a
 * medicao nao rodou.
 *
 * <p><b>O nome do arquivo e gabarito e nao entra na classificacao.</b> O
 * {@code Pipeline} passa ao classificador apenas o texto extraido. Um
 * COMPROVANTE_PG_FGTS.pdf reconhecido por se chamar assim nao teria sido
 * reconhecido — e a medicao estaria medindo a convencao de nomes da pasta.
 */
public final class MedirPrecisao {

    static final ObjectMapper JSON = new ObjectMapper();

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String dir = System.getProperty("sgdf.massa");
        if (dir == null || dir.isBlank()) {
            System.out.println("  (pulado: -Dsgdf.massa nao informado — a massa real nao "
                    + "vive no repositorio, cap. 19)");
            System.out.println("0/0 testes passaram.");
            return;
        }
        Path massa = Path.of(dir);
        if (!Files.isDirectory(massa)) {
            System.out.println("  (pulado: " + massa + " nao e um diretorio)");
            System.out.println("0/0 testes passaram.");
            return;
        }

        JsonNode spec = JSON.readTree(Path.of(System.getProperty("sgdf.raiz", "."))
                .resolve("especificacao/precisao/gabarito.json").toFile());
        Map<String, String> gabarito = new LinkedHashMap<>();
        spec.get("gabarito").fields()
                .forEachRemaining(e -> gabarito.put(e.getKey(), e.getValue().asText()));
        Set<String> corporativos = new java.util.LinkedHashSet<>();
        spec.get("corporativo").forEach(n -> corporativos.add(n.asText()));

        Pipeline pipeline = new Pipeline(new ExtratorPdfBox(),
                new Classificador(CargaDeRegras.todas()),
                new ValidacaoDeSeguranca(50L * 1024 * 1024));

        MedicaoDePrecisao geral = new MedicaoDePrecisao();
        MedicaoDePrecisao corporativa = new MedicaoDePrecisao();
        List<String> ausentes = new ArrayList<>();

        for (Map.Entry<String, String> e : gabarito.entrySet()) {
            Path arquivo = massa.resolve(e.getKey());
            if (!Files.isRegularFile(arquivo)) {
                ausentes.add(e.getKey());
                continue;
            }
            byte[] conteudo = Files.readAllBytes(arquivo);
            DocumentoProcessado p = pipeline.processar(e.getKey(), conteudo,
                    VeredictoAntivirus.limpo(), Set.of(), null);
            Pipeline.Encaminhamento enc = Pipeline.encaminhamentoDe(p);

            geral.registrar(e.getKey(), e.getValue(), p.tipo(), p.decisao(), enc);
            if (corporativos.contains(e.getValue())) {
                corporativa.registrar(e.getKey(), e.getValue(), p.tipo(), p.decisao(), enc);
            }
        }

        System.out.println();
        System.out.println("--- massa completa (" + geral.total() + " arquivos) ---");
        System.out.println(geral.relatorio());
        System.out.println("--- bloco corporativo, o que o cap. 19 exige ---");
        System.out.println(corporativa.relatorio());

        if (!geral.divergencias().isEmpty()) {
            System.out.println("--- o que nao fechou ---");
            geral.divergencias().forEach(d -> System.out.printf(
                    "  %-12s %-34s esperado %-24s obtido %-24s (%s)%n",
                    d.situacao(), d.arquivo(), d.esperado(),
                    d.obtido() == null ? "—" : d.obtido(), d.encaminhamento()));
            System.out.println();
        }

        // AUSENTE NAO E ACERTO NEM ERRO — E MASSA QUE FALTA.
        //
        // Somar arquivos ausentes ao denominador baixaria a precisao por um
        // motivo que nao e do classificador; ignora-los em silencio deixaria a
        // medicao afirmar sobre 27 arquivos tendo lido 12. E por isso que isto
        // e uma FALHA, e nao um aviso.
        ok("Cap. 19 . os " + gabarito.size() + " arquivos do gabarito estao na massa"
                + (ausentes.isEmpty() ? "" : " — faltam " + ausentes), ausentes.isEmpty());

        ok("Cap. 19 . precisao do bloco corporativo >= 95%", corporativa.atingeAMeta());
        ok("Cap. 19 . e o bloco corporativo nao foi medido sobre abstencao — "
                        + "cobertura de " + String.format("%.0f%%", corporativa.cobertura() * 100),
                corporativa.cobertura() > 0.5);
        ok("Cap. 19 . nenhum ERRO de tipo na massa inteira — o caso caro e vincular "
                        + "o documento a obrigacao errada",
                geral.divergencias().stream()
                        .noneMatch(d -> d.situacao() == MedicaoDePrecisao.Situacao.ERRO));

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
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

    private MedirPrecisao() {}
}
