package br.com.engesoftware.sgdf.seguranca;

import java.util.Set;
import java.util.UUID;

/**
 * Quem está pedindo — o resultado da autenticação, já traduzido para o domínio.
 *
 * <p>Deliberadamente independente do mecanismo: um token OIDC, um certificado
 * de serviço ou um teste produzem o mesmo {@code Ator}, e o {@link Autorizador}
 * decide sobre ele sem saber de onde veio. É o que permite testar a coluna
 * "não pode" do cap. 15.1 sem subir um provedor de identidade.
 *
 * @param identificador quem é, como aparece na trilha de auditoria
 * @param nome          para a tela
 * @param papeis        os papéis vindos dos grupos do diretório
 * @param contratos     os contratos-serviço que este ator enxerga; vazio quando
 *                      o papel tem visão global
 */
public record Ator(String identificador, String nome, Set<Papel> papeis, Set<UUID> contratos) {

    public Ator {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException(
                    "ator sem identificador não é rastreável na trilha de auditoria (cap. 16)");
        }
        papeis = Set.copyOf(papeis);
        contratos = Set.copyOf(contratos);
    }

    /**
     * Papéis de visão global não têm recorte por contrato.
     *
     * <p>APROVADOR_DAF e AUDITORIA veem tudo por definição do cap. 15.1; o
     * ADMIN_SISTEMA vê o painel de tudo mas não o conteúdo, o que já é barrado
     * pela permissão, não pelo escopo.
     */
    public boolean enxergaTodosOsContratos() {
        return papeis.contains(Papel.APROVADOR_DAF)
                || papeis.contains(Papel.AUDITORIA)
                || papeis.contains(Papel.ADMIN_SISTEMA)
                || papeis.contains(Papel.CURADOR_MATRIZ);
    }

    /** Conta de serviço — login interativo negado (cap. 15.1). */
    public boolean deServico() {
        return !papeis.isEmpty() && papeis.stream().allMatch(Papel::deServico);
    }
}
