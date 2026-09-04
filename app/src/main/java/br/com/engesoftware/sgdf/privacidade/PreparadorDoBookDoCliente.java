package br.com.engesoftware.sgdf.privacidade;

import br.com.engesoftware.sgdf.book.DocumentoPublicavel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Passa os documentos pelo redator antes do book do cliente — história F2-07.
 *
 * <p>Cap. 10: tipos com sigilo {@code PESSOAL} ou {@code PESSOAL_SENSIVEL}
 * passam pelo redator antes da cópia; <b>o original íntegro permanece no
 * repositório interno</b>. Esta classe produz a cópia; ela não toca no original.
 *
 * <p><b>Tarja incompleta não vira publicação silenciosa.</b> Quando o redator
 * relata sequências com forma de CPF ainda extraíveis, elas sobem no resultado.
 * Quem publica decide — e decidir exige ver.
 */
public final class PreparadorDoBookDoCliente {

    private final RedatorDePdf redator = new RedatorDePdf();

    public Resultado preparar(List<DocumentoPublicavel> documentos) {
        List<DocumentoPublicavel> paraOCliente = new ArrayList<>();
        Map<String, List<String>> pendencias = new LinkedHashMap<>();
        int tarjados = 0;

        for (DocumentoPublicavel d : documentos) {
            if (!d.sigilo().exigeTarjamento()) {
                paraOCliente.add(d);
                continue;
            }
            RedatorDePdf.Resultado r = redator.tarjar(d.conteudo());
            tarjados += r.tarjados();
            if (!r.completa()) {
                pendencias.put(d.tipo(), r.restantes());
            }
            paraOCliente.add(new DocumentoPublicavel(d.tipo(), d.familia(), d.sigilo(),
                    r.pdf(), d.origem(), d.validadoEm(), true));
        }
        return new Resultado(List.copyOf(paraOCliente), tarjados, Map.copyOf(pendencias));
    }

    /**
     * @param documentos os mesmos tipos, com os pessoais já tarjados
     * @param tarjados   quantos CPFs foram removidos ao todo
     * @param pendencias por tipo, o que o redator não conseguiu garantir
     */
    public record Resultado(List<DocumentoPublicavel> documentos, int tarjados,
                            Map<String, List<String>> pendencias) {

        /** Verdadeiro quando nenhum documento deixou sequência com forma de CPF. */
        public boolean semPendencia() {
            return pendencias.isEmpty();
        }

        /** A pendência em português, para quem decide publicar. */
        public String descricaoDasPendencias() {
            List<String> linhas = new ArrayList<>();
            pendencias.forEach((tipo, restantes) -> linhas.add(tipo + ": "
                    + String.join(", ", restantes)));
            return String.join("; ", linhas);
        }
    }
}
