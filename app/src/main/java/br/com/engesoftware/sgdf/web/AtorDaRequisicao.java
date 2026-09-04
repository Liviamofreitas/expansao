package br.com.engesoftware.sgdf.web;

import br.com.engesoftware.sgdf.seguranca.Ator;
import br.com.engesoftware.sgdf.seguranca.Papel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Traduz o token OIDC em {@link Ator}.
 *
 * <p>A autenticação — quem é, com MFA — é do provedor de identidade. Aqui só se
 * lê o que ele afirmou. A tradução é estreita de propósito: um grupo que não
 * corresponde a papel do cap. 15.1 é <b>descartado</b>, não convertido no papel
 * de menor privilégio.
 */
@Component
public class AtorDaRequisicao {

    /** Claim onde o IdP publica os grupos do diretório. */
    static final String CLAIM_GRUPOS = "groups";

    /** Claim com os contratos-serviço que o ator enxerga. */
    static final String CLAIM_CONTRATOS = "contratos";

    public Ator atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return de(jwt);
    }

    static Ator de(Jwt jwt) {
        Set<Papel> papeis = new LinkedHashSet<>();
        for (String grupo : lista(jwt, CLAIM_GRUPOS)) {
            Papel papel = Papel.doGrupo(grupo);
            if (papel != null) {
                papeis.add(papel);
            }
        }
        Set<UUID> contratos = new LinkedHashSet<>();
        for (String contrato : lista(jwt, CLAIM_CONTRATOS)) {
            try {
                contratos.add(UUID.fromString(contrato));
            } catch (IllegalArgumentException e) {
                // Um contrato mal formado no token não vira acesso a nada.
            }
        }
        String identificador = jwt.getSubject();
        if (identificador == null || identificador.isBlank()) {
            return null;
        }
        String nome = jwt.getClaimAsString("name");
        return new Ator(identificador, nome == null ? identificador : nome, papeis, contratos);
    }

    private static List<String> lista(Jwt jwt, String claim) {
        Object valor = jwt.getClaim(claim);
        List<String> itens = new ArrayList<>();
        if (valor instanceof List<?> lista) {
            lista.forEach(i -> itens.add(String.valueOf(i)));
        } else if (valor instanceof String texto && !texto.isBlank()) {
            for (String parte : texto.split("[,\\s]+")) {
                itens.add(parte);
            }
        }
        return itens;
    }
}
