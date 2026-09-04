package br.com.engesoftware.sgdf.notificacao;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma exigência que a régua pode cobrar.
 *
 * <p><b>Não carrega o profissional, e isso é estrutural.</b> Exigência de escopo
 * PROFISSIONAL é uma por trabalhador; um aviso que as listasse nominalmente
 * mandaria nome de gente para uma caixa de área. O capítulo 11.2 proíbe anexar
 * documento e manda usar link autenticado — o mesmo raciocínio vale para o
 * conteúdo: o aviso diz <i>quantas</i>, e quem tem acesso vê <i>quais</i> atrás
 * da autenticação. Como o campo não existe aqui, não há como vazá-lo adiante.
 *
 * @param tipoCodigo o código do tipo documental — é o que a pessoa precisa entregar
 * @param familia    resolve o destinatário (contrato × família, cap. 11.2)
 * @param prazo      o prazo calculado da exigência
 * @param resolvidaEm quando a entrega foi detectada; nulo enquanto aberta
 */
public record PendenciaAberta(UUID exigenciaId, String tipoCodigo, String familia,
                              LocalDate prazo, LocalDate resolvidaEm) {

    public PendenciaAberta {
        if (tipoCodigo == null || familia == null || prazo == null) {
            throw new IllegalArgumentException(
                    "pendência sem tipo, família ou prazo não tem como ser cobrada");
        }
    }

    public boolean aberta() {
        return resolvidaEm == null;
    }
}
