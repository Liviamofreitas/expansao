package br.com.engesoftware.sgdf.notificacao;

import br.com.engesoftware.sgdf.book.Hash;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * O texto do aviso e o hash do que teria sido enviado.
 *
 * <p><b>Por que o hash existe.</b> {@code notificacao.conteudo_hash} é o que
 * liga o registro do modo sombra ao que a régua realmente diria. Sem ele, a
 * calibragem da F3-01 compararia apenas contagens — "o sistema mandaria 14
 * avisos" — e não teria como mostrar que dois deles diziam a coisa errada. Com
 * ele, e com o template versionado e imutável, o conteúdo de qualquer aviso é
 * reproduzível meses depois.
 *
 * <p>O hash cobre o template, o destinatário, o ciclo, a data e os itens na
 * ordem canônica — não o texto renderizado. Renderização muda com espaçamento e
 * quebra de linha; o que se quer identificar é <i>o que foi dito</i>.
 */
public final class Conteudo {

    /**
     * Aviso montado, pronto para ser registrado — e nada mais.
     *
     * <p>Não há campo de anexo, e não é omissão: o cap. 11.2 diz "nunca anexar
     * documento; somente link autenticado com expiração e log de acesso". Um
     * campo de anexo aqui seria a porta por onde o primeiro PDF sairia.
     */
    public record Montado(String assunto, String corpo, String hash, String templateVersao) {}

    private Conteudo() {}

    public static Montado montar(Aviso aviso, Template template, String linkAutenticado) {
        String canonico = canonico(aviso, template.versao());
        String corpo = template.corpo()
                .replace("{itens}", listar(aviso.itens()))
                .replace("{link}", linkAutenticado == null ? "(link não configurado)"
                        : linkAutenticado);
        String assunto = template.assunto()
                .replace("{quantidade}", String.valueOf(total(aviso.itens())));
        return new Montado(assunto, corpo,
                Hash.de(canonico.getBytes(StandardCharsets.UTF_8)), template.versao());
    }

    static String canonico(Aviso aviso, String templateVersao) {
        StringBuilder sb = new StringBuilder();
        sb.append("template:").append(templateVersao).append('\n');
        sb.append("destinatario:").append(aviso.destinatario().email()).append('\n');
        sb.append("ciclo:").append(aviso.cicloId()).append('\n');
        sb.append("data:").append(aviso.dataReferencia()).append('\n');
        for (Aviso.Item i : aviso.itens()) {
            sb.append(i.momento()).append('|').append(i.tipoCodigo()).append('|')
                    .append(i.prazo()).append('|').append(i.quantidade()).append('|')
                    .append(i.papelRecebido()).append('\n');
        }
        return sb.toString();
    }

    private static String listar(List<Aviso.Item> itens) {
        StringBuilder sb = new StringBuilder();
        for (Aviso.Item i : itens) {
            sb.append("- ").append(i.tipoCodigo());
            if (i.quantidade() > 1) {
                sb.append(" (").append(i.quantidade()).append(" pendentes)");
            }
            if (i.prazo() != null) {
                sb.append(" — prazo ").append(i.prazo());
            }
            if (i.copia()) {
                sb.append(" [cópia]");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static int total(List<Aviso.Item> itens) {
        return itens.stream().mapToInt(Aviso.Item::quantidade).sum();
    }

    /** Template versionado do cadastro (cap. 11.2). */
    public record Template(Momento momento, String versao, String assunto, String corpo) {}
}
