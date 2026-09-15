package br.com.engesoftware.sgdf.classificacao;

import br.com.engesoftware.sgdf.extracao.Glifo;
import br.com.engesoftware.sgdf.extracao.PaginaExtraida;
import br.com.engesoftware.sgdf.extracao.TextoExtraido;
import java.util.ArrayList;
import java.util.List;

/**
 * Prova a regra contra os exemplos, no momento do cadastro.
 *
 * <p><b>Por que isto existe.</b> Enquanto as regras viviam em Java, cada uma era
 * revisada em code review e coberta por teste contra documento real. Com o
 * cadastro pela aplicação (ADR-004), uma regra passa a ser digitada num
 * formulário — e uma regra de reconhecimento errada <b>não falha alto</b>: ela
 * classifica errado, em silêncio, e o erro só aparece no book.
 *
 * <p>O exemplo é o que substitui o code review. Ele deixa de ser documentação e
 * vira a verificação, guardada junto da regra que prova.
 *
 * <p><b>Três recusas, e a terceira é a que ninguém lembra de pedir.</b>
 *
 * <ol>
 *   <li><b>Não reconhece o próprio exemplo.</b> A regra afirma reconhecer um
 *       documento e não reconhece nem o que ela mesma apresentou como tal.
 *   <li><b>Reconhece um exemplo negativo.</b> "Reconhece a CND da Receita" é
 *       metade da afirmação; a outra é "e não reconhece a CNDT".
 *   <li><b>Rouba o exemplo de outro tipo.</b> Uma âncora ampla demais passa nas
 *       duas primeiras e quebra o que já funcionava — sem que nada apite,
 *       porque o documento roubado continua sendo classificado, só que errado.
 *       É o único dos três que exige olhar para fora da regra que está entrando.
 * </ol>
 */
public final class VerificadorDeRegra {

    /** Um exemplo guardado: de qual tipo é, e se a regra dele deve reconhecê-lo. */
    public record Exemplo(String tipo, String rotulo, String texto, boolean deveReconhecer) {}

    /** O que impediu a regra de entrar. Lista vazia = aprovada. */
    public record Veredito(List<String> recusas) {

        public Veredito {
            recusas = List.copyOf(recusas);
        }

        public boolean aprovada() {
            return recusas.isEmpty();
        }
    }

    private VerificadorDeRegra() {}

    /**
     * Verifica {@code candidata} contra todos os exemplos.
     *
     * @param candidata   a regra que está entrando
     * @param demais      as regras ativas, JÁ SEM nenhuma versão da candidata —
     *                    senão a versão antiga competiria com a nova e o
     *                    resultado não diria nada sobre nenhuma das duas
     * @param exemplosDaCandidata os exemplos que vieram no cadastro
     * @param exemplosExistentes  os que já estão guardados, de todos os tipos
     */
    public static Veredito verificar(RegraDeReconhecimento candidata,
                                     List<RegraDeReconhecimento> demais,
                                     List<Exemplo> exemplosDaCandidata,
                                     List<Exemplo> exemplosExistentes) {
        List<String> recusas = new ArrayList<>();

        if (exemplosDaCandidata.stream().noneMatch(Exemplo::deveReconhecer)) {
            // SEM EXEMPLO POSITIVO NÃO HÁ O QUE VERIFICAR.
            //
            // Aceitar a regra aqui seria aceitar uma afirmação sobre o mundo sem
            // nenhuma forma de conferi-la — e depois nunca mais haveria momento
            // melhor para pedir o exemplo do que este.
            recusas.add("a regra não trouxe nenhum exemplo POSITIVO: sem um texto que ela "
                    + "deva reconhecer, não há como provar que ela reconhece alguma coisa");
            return new Veredito(recusas);
        }

        List<RegraDeReconhecimento> comACandidata = new ArrayList<>(demais);
        comACandidata.add(candidata);
        Classificador motor = new Classificador(comACandidata);

        // 1 e 2 — os exemplos da própria regra.
        for (Exemplo e : exemplosDaCandidata) {
            Classificacao c = motor.classificar(comoTexto(e.texto()));
            boolean reconheceu = candidata.tipo().equals(c.tipo()) && c.automatica();
            if (e.deveReconhecer() && !reconheceu) {
                recusas.add("o exemplo '" + e.rotulo() + "' deveria ser reconhecido como "
                        + candidata.tipo() + " e não foi: " + comoFoi(c)
                        + ". Fortaleça as âncoras ou reveja o limiar automático ("
                        + candidata.limiarAuto() + ")");
            }
            if (!e.deveReconhecer() && reconheceu) {
                recusas.add("o exemplo '" + e.rotulo() + "' NÃO deveria ser reconhecido como "
                        + candidata.tipo() + ", e foi: " + comoFoi(c)
                        + ". Alguma âncora está ampla demais");
            }
        }

        // 3 — o que já existia continua funcionando?
        //
        // O CRITÉRIO É "CONTINUA SENDO RECONHECIDO AUTOMATICAMENTE", E NÃO SÓ
        // "CONTINUA SENDO DO MESMO TIPO". A diferença apareceu ao testar.
        //
        // Uma regra que COPIA a âncora de outra não rouba o documento: ela
        // EMPATA com a original. E empate não dá vencedor — manda para triagem.
        // Medido: uma candidata com a âncora discriminante da CNDT fez o exemplo
        // da CNDT sair de AUTOMATICA para "TRIAGEM, score 1.000", ainda como
        // CER.CNDT.
        //
        // Pelo critério antigo isso passaria: o tipo não mudou. E o estrago é
        // real — todo documento daquele tipo, que era vinculado sozinho, passa a
        // exigir uma pessoa. O sistema não erra a classificação; ele para de
        // classificar, e ninguém liga a lentidão nova à regra que entrou.
        for (Exemplo e : exemplosExistentes) {
            if (!e.deveReconhecer() || e.tipo().equals(candidata.tipo())) {
                continue;
            }
            Classificacao c = motor.classificar(comoTexto(e.texto()));
            if (!e.tipo().equals(c.tipo())) {
                recusas.add("a regra nova ROUBA o exemplo '" + e.rotulo() + "', que é de "
                        + e.tipo() + " e passou a ser " + comoFoi(c)
                        + ". Uma âncora que serve a dois tipos não identifica nenhum");
            } else if (!c.automatica()) {
                recusas.add("a regra nova REBAIXA o exemplo '" + e.rotulo() + "', de "
                        + e.tipo() + ": ele era reconhecido automaticamente e passou a "
                        + comoFoi(c) + ". Provavelmente uma âncora repetida entre os dois "
                        + "tipos — empate não elege ninguém, manda para triagem");
            }
        }
        return new Veredito(recusas);
    }

    private static String comoFoi(Classificacao c) {
        if (c.tipo() == null) {
            return "nenhum tipo candidato (" + c.motivo() + ")";
        }
        return c.tipo() + " com decisão " + c.decisao()
                + (c.melhor().isPresent()
                        ? String.format(" e score %.3f", c.melhor().get().score()) : "");
    }

    /**
     * O exemplo é texto; o classificador espera texto extraído de documento.
     *
     * <p>Os glifos são sintéticos e o motivo é estrutural: {@code PaginaExtraida}
     * exige um glifo por caractere — a garantia que sustenta a extração
     * posicional, onde a posição na página é o que localiza o valor. Um exemplo
     * colado não tem página, e nada que dependa de coordenada faz sentido sobre
     * ele. Por isso os glifos vão com coordenada zero: eles satisfazem o
     * contrato sem afirmar uma posição que não existe.
     *
     * <p>A classificação por âncora não usa coordenada nenhuma — ela casa
     * expressões contra o texto normalizado. É exatamente a parte da regra que o
     * cadastro edita, e exatamente a que este verificador prova.
     */
    private static TextoExtraido comoTexto(String texto) {
        List<Glifo> glifos = new ArrayList<>(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            glifos.add(new Glifo(texto.charAt(i), 1, 0f, 0f, 0f, 0f));
        }
        return new TextoExtraido(List.of(new PaginaExtraida(1, texto, glifos)),
                TextoExtraido.Origem.PDF_NATIVO, List.of(), List.of());
    }
}
