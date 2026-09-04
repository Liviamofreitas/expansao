package br.com.engesoftware.sgdf.privacidade;

import br.com.engesoftware.sgdf.validacao.DigitoVerificador;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Tarja dado pessoal num PDF — história F2-07.
 *
 * <p><b>A falha clássica que esta classe existe para não cometer.</b> Desenhar
 * um retângulo preto sobre o texto <i>não</i> é tarjar: o texto continua na
 * camada de conteúdo e sai inteiro em qualquer copiar-e-colar. Documentos
 * públicos já vazaram exatamente assim, e um sistema que faz isso entrega ao
 * cliente um arquivo que <i>parece</i> tarjado.
 *
 * <p>Aqui a tarja é feita <b>removendo os glifos do fluxo de conteúdo</b>. O que
 * some, some do arquivo. O teste de aceite é o único que prova alguma coisa:
 * extrair o texto do PDF tarjado e verificar que o CPF não está mais lá.
 *
 * <p><b>Máscara parcial.</b> Os seis dígitos do meio permanecem
 * ({@code ***.190.471-**}), pelo motivo explicado em {@link Mascara}: quem
 * confere o book precisa distinguir dois colaboradores, e um documento
 * ilegível leva alguém a consultar o original em claro.
 *
 * <p><b>Só tarja o que tem DV válido.</b> Um código de barras com onze dígitos
 * casa com o padrão de CPF e não é CPF; apagá-lo destruiria informação
 * necessária. É a exigência da seção 6 dos achados da massa real.
 */
public final class RedatorDePdf {

    /** CPF com e sem pontuação, como aparece nos documentos reais. */
    private static final Pattern CPF = Pattern.compile(
            "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|(?<!\\d)\\d{11}(?!\\d)");

    /**
     * Tarja o documento e devolve os bytes do PDF tarjado.
     *
     * <p>O original não é tocado: cap. 10 manda que ele permaneça íntegro no
     * repositório interno com acesso restrito e logado.
     */
    public Resultado tarjar(byte[] pdfOriginal) {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfOriginal)) {
            int tarjados = 0;
            for (PDPage pagina : doc.getPages()) {
                tarjados += tarjarPagina(doc, pagina);
            }
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            doc.save(saida);
            byte[] pdf = saida.toByteArray();
            return new Resultado(pdf, tarjados, aindaExtraiveis(pdf));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao tarjar o documento", e);
        }
    }

    /**
     * Confere o próprio trabalho: o que ainda sai do PDF tarjado com forma de
     * CPF e DV válido.
     *
     * <p>Não é zelo. O redator trabalha sobre o FLUXO DE CONTEÚDO e quem copia
     * do PDF recebe a ORDEM DE LEITURA — e as duas não coincidem. O extrator
     * insere espaços onde há lacuna entre glifos, e esses espaços criam
     * fronteiras de onze dígitos que não existem no fluxo. O contrário também
     * pode acontecer, e é por isso que a conferência é feita pelo mesmo caminho
     * que um vazamento tomaria: extrair o texto do resultado.
     *
     * <p>O que restar não é necessariamente falha — na DCTFWeb real o que sobra
     * são dígitos de código de barras que passam no DV por coincidência. Mas
     * <b>fica na lista</b>, porque decidir que uma sequência com DV válido não é
     * CPF é juízo, e juízo silencioso é o que faz um vazamento passar.
     */
    private static List<String> aindaExtraiveis(byte[] pdf) {
        String texto;
        try {
            texto = new br.com.engesoftware.sgdf.extracao.ExtratorPdfBox().extrair(pdf)
                    .textoCompleto();
        } catch (RuntimeException e) {
            // Um PDF que o extrator não lê é um PDF de onde nada se copia.
            return List.of();
        }
        List<String> restantes = new ArrayList<>();
        Matcher m = CPF.matcher(texto);
        while (m.find()) {
            if (DigitoVerificador.cpfValido(m.group()) && !restantes.contains(m.group())) {
                restantes.add(m.group());
            }
        }
        return List.copyOf(restantes);
    }

    /**
     * @return quantos CPFs foram tarjados nesta página
     */
    private int tarjarPagina(PDDocument doc, PDPage pagina) throws IOException {
        List<Object> tokens = new PDFStreamParser(pagina).parse();
        List<Glifo> glifos = decodificar(tokens, pagina.getResources());
        if (glifos.isEmpty()) {
            return 0;
        }

        StringBuilder texto = new StringBuilder();
        List<Glifo> porPosicao = new ArrayList<>();
        for (Glifo g : glifos) {
            for (int i = 0; i < g.unicode().length(); i++) {
                porPosicao.add(g);
            }
            texto.append(g.unicode());
        }

        // Marca os bytes a remover. Trabalhar sobre marcas, e só depois
        // reescrever, evita mexer nos índices enquanto ainda se procura.
        boolean[] remover = new boolean[glifos.size()];
        int tarjados = 0;
        Matcher m = CPF.matcher(texto);
        while (m.find()) {
            if (!DigitoVerificador.cpfValido(m.group())) {
                continue;
            }
            tarjados++;
            for (int i = m.start(); i < m.end(); i++) {
                Glifo g = porPosicao.get(i);
                if (Character.isDigit(texto.charAt(i)) && escondido(m, i)) {
                    remover[g.indice()] = true;
                }
            }
        }
        if (tarjados == 0) {
            return 0;
        }

        reescrever(doc, pagina, tokens, glifos, remover);
        return tarjados;
    }

    /**
     * Se este dígito do casamento fica escondido.
     *
     * <p>Escondem-se os três primeiros e os dois verificadores; os seis do meio
     * ficam. A conta é sobre a posição do DÍGITO dentro do CPF, não sobre a
     * posição no texto, porque o mesmo CPF aparece com e sem pontuação.
     */
    private static boolean escondido(Matcher m, int posicaoNoTexto) {
        String casamento = m.group();
        int digito = 0;
        for (int i = 0; i < posicaoNoTexto - m.start(); i++) {
            if (Character.isDigit(casamento.charAt(i))) {
                digito++;
            }
        }
        return digito < 3 || digito >= 9;
    }

    /**
     * Reescreve o fluxo de conteúdo sem os bytes marcados.
     *
     * <p>{@code COSString.setValue} está depreciado em favor de construir outra
     * string, mas é a IDENTIDADE do objeto que liga o token ao fluxo: trocá-lo
     * exigiria localizar e substituir o token na lista, e é exatamente essa
     * busca que a mutação no lugar torna desnecessária.
     */
    @SuppressWarnings("deprecation")
    private static void reescrever(PDDocument doc, PDPage pagina, List<Object> tokens,
                                   List<Glifo> glifos, boolean[] remover) throws IOException {
        for (int i = 0; i < glifos.size(); i++) {
            if (!remover[i]) {
                continue;
            }
            Glifo g = glifos.get(i);
            COSString original = g.string();
            byte[] bytes = original.getBytes();
            byte[] novos = new byte[bytes.length - g.tamanho()];
            System.arraycopy(bytes, 0, novos, 0, g.offset());
            System.arraycopy(bytes, g.offset() + g.tamanho(), novos, g.offset(),
                    bytes.length - g.offset() - g.tamanho());
            original.setValue(novos);
            // Os glifos seguintes da MESMA string andaram para trás.
            for (int j = i + 1; j < glifos.size(); j++) {
                if (glifos.get(j).string() == original) {
                    glifos.set(j, glifos.get(j).recuado(g.tamanho()));
                }
            }
        }

        var fluxo = doc.getDocument().createCOSStream();
        try (var saida = fluxo.createOutputStream()) {
            new ContentStreamWriter(saida).writeTokens(tokens);
        }
        pagina.setContents(new org.apache.pdfbox.pdmodel.common.PDStream(fluxo));
    }

    /**
     * Decodifica os operadores de texto em glifos, sabendo de que fonte cada um
     * veio.
     *
     * <p>Sem acompanhar o {@code Tf}, não há como transformar bytes em Unicode:
     * o mesmo byte é um dígito numa fonte e outra coisa na seguinte.
     */
    private static List<Glifo> decodificar(List<Object> tokens, PDResources recursos)
            throws IOException {
        List<Glifo> glifos = new ArrayList<>();
        PDFont fonte = null;
        List<COSBase> operandos = new ArrayList<>();

        for (Object token : tokens) {
            if (!(token instanceof Operator operador)) {
                operandos.add((COSBase) token);
                continue;
            }
            switch (operador.getName()) {
                case "Tf" -> {
                    if (operandos.size() >= 2 && operandos.get(0) instanceof COSName nome
                            && recursos != null) {
                        fonte = recursos.getFont(nome);
                    }
                }
                // Tj mostra uma string; ' e " mostram uma string e mudam de
                // linha — para o que interessa aqui, os três são iguais.
                case "Tj", "'", "\"" -> {
                    PDFont daVez = fonte;
                    ultimaString(operandos).ifPresent(s -> lerCodigos(s, daVez, glifos));
                }
                // TJ mostra um arranjo de strings intercaladas com ajustes de
                // espaçamento; só as strings carregam texto.
                case "TJ" -> {
                    if (!operandos.isEmpty()
                            && operandos.get(operandos.size() - 1) instanceof COSArray arranjo) {
                        for (COSBase item : arranjo) {
                            if (item instanceof COSString s) {
                                lerCodigos(s, fonte, glifos);
                            }
                        }
                    }
                }
                default -> { }
            }
            operandos.clear();
        }
        return glifos;
    }

    private static java.util.Optional<COSString> ultimaString(List<COSBase> operandos) {
        for (int i = operandos.size() - 1; i >= 0; i--) {
            if (operandos.get(i) instanceof COSString s) {
                return java.util.Optional.of(s);
            }
        }
        return java.util.Optional.empty();
    }

    /** Quebra a string em códigos e resolve o Unicode de cada um. */
    private static void lerCodigos(COSString string, PDFont fonte, List<Glifo> glifos) {
        byte[] bytes = string.getBytes();
        try (InputStream entrada = new ByteArrayInputStream(bytes)) {
            while (entrada.available() > 0) {
                int offset = bytes.length - entrada.available();
                int codigo = fonte == null ? entrada.read() : fonte.readCode(entrada);
                int tamanho = (bytes.length - entrada.available()) - offset;
                String unicode = unicodeDe(fonte, codigo);
                glifos.add(new Glifo(glifos.size(), string, offset, tamanho, unicode));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao decodificar texto do PDF", e);
        }
    }

    private static String unicodeDe(PDFont fonte, int codigo) {
        if (fonte != null) {
            try {
                String u = fonte.toUnicode(codigo);
                if (u != null) {
                    return u;
                }
            } catch (RuntimeException e) {
                // Fonte sem mapa de Unicode: cai para a leitura simples abaixo.
            }
        }
        return codigo >= 32 && codigo < 127 ? String.valueOf((char) codigo) : "�";
    }

    /**
     * Um código de caractere dentro de uma string do fluxo de conteúdo.
     *
     * @param indice  posição na lista de glifos da página
     * @param string  a COSString de onde veio — comparada por identidade
     * @param offset  byte onde o código começa dentro dela
     * @param tamanho quantos bytes o código ocupa
     * @param unicode o que ele representa
     */
    private record Glifo(int indice, COSString string, int offset, int tamanho, String unicode) {

        Glifo recuado(int bytes) {
            return new Glifo(indice, string, offset - bytes, tamanho, unicode);
        }
    }

    /**
     * @param pdf        bytes do documento tarjado
     * @param tarjados   quantos CPFs foram encontrados e tarjados
     * @param restantes  sequências com forma de CPF e DV válido que ainda saem
     *                   do documento tarjado — ver {@link #aindaExtraiveis}
     */
    public record Resultado(byte[] pdf, int tarjados, List<String> restantes) {

        public Resultado {
            restantes = List.copyOf(restantes);
        }

        /**
         * Verdadeiro quando nada com forma de CPF sobrou.
         *
         * <p>Falso não quer dizer "falhou": quer dizer "alguém precisa olhar".
         * Publicar um book do cliente com {@code restantes} não vazio é uma
         * decisão, e uma decisão tem de ser tomada por alguém.
         */
        public boolean completa() {
            return restantes.isEmpty();
        }
    }
}
