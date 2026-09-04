package br.com.engesoftware.sgdf.web;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uma {@link Connection} que devolve linhas combinadas por um trecho do SQL.
 *
 * <p><b>Por que não um banco de verdade aqui.</b> O que estes testes verificam é
 * a <i>ordem</i>: autorizar primeiro, consultar depois. Um banco real responderia
 * igual nos dois casos e esconderia justamente o defeito que importa — o
 * controlador que consulta e só então decide, vazando a existência do dado para
 * quem não pode vê-lo (e, num sistema com trilha, deixando no log a leitura de
 * quem não tinha direito a ela).
 *
 * <p>Por isso a conexão sem nenhuma resposta registrada <b>explode</b> em vez de
 * devolver vazio: se o controlador tocar no banco quando deveria ter negado, o
 * teste falha alto. Silêncio ali seria um teste que passa pelo motivo errado.
 */
final class ConexaoDeMentira {

    private final Map<String, List<Object[]>> respostas = new LinkedHashMap<>();
    private final List<String> consultasFeitas = new ArrayList<>();

    /**
     * Registra as linhas devolvidas para todo SQL que contenha {@code trecho}.
     *
     * <p>O parâmetro é {@code Object[]...} e não {@code Object...} de propósito:
     * com o segundo, uma chamada com uma única linha seria espalhada em colunas
     * soltas pelo próprio compilador, e o teste passaria a verificar outra coisa
     * sem avisar ninguém.
     */
    ConexaoDeMentira respondendo(String trecho, Object[]... linhas) {
        respostas.put(trecho, new ArrayList<>(List.of(linhas)));
        return this;
    }

    List<String> consultasFeitas() {
        return consultasFeitas;
    }

    Connection conexao() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "prepareStatement":
                            return prepara((String) args[0]);
                        case "getAutoCommit":
                            return Boolean.TRUE;
                        case "createArrayOf":
                            return Proxy.newProxyInstance(
                                    java.sql.Array.class.getClassLoader(),
                                    new Class<?>[] {java.sql.Array.class},
                                    (a, m, r) -> padrao(m));
                        case "close":
                        case "setAutoCommit":
                        case "commit":
                        case "rollback":
                            return null;
                        default:
                            return padrao(metodo);
                    }
                });
    }

    private PreparedStatement prepara(String sql) {
        consultasFeitas.add(sql);
        List<Object[]> linhas = null;
        for (Map.Entry<String, List<Object[]>> e : respostas.entrySet()) {
            if (sql.contains(e.getKey())) {
                linhas = e.getValue();
                break;
            }
        }
        if (linhas == null) {
            throw new AssertionError("consulta ao banco que este teste não esperava — "
                    + "a decisão de autorização deveria ter vindo antes: " + sql.strip());
        }
        List<Object[]> conteudo = linhas;
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                (proxy, metodo, args) -> {
                    if ("executeQuery".equals(metodo.getName())) {
                        return resultado(conteudo);
                    }
                    return padrao(metodo);
                });
    }

    private ResultSet resultado(List<Object[]> linhas) {
        int[] posicao = {-1};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "next":
                            return ++posicao[0] < linhas.size();
                        case "getString":
                            Object texto = coluna(linhas, posicao[0], args);
                            return texto == null ? null : String.valueOf(texto);
                        case "getInt":
                            return ((Number) coluna(linhas, posicao[0], args)).intValue();
                        case "getBigDecimal":
                            Object numero = coluna(linhas, posicao[0], args);
                            return numero == null ? null
                                    : new BigDecimal(String.valueOf(numero));
                        case "getObject":
                            return coluna(linhas, posicao[0], args);
                        default:
                            return padrao(metodo);
                    }
                });
    }

    private static Object coluna(List<Object[]> linhas, int posicao, Object[] args) {
        return linhas.get(posicao)[((Number) args[0]).intValue() - 1];
    }

    /** Métodos sem resposta útil: nulo para objeto, falso/zero para primitivo. */
    private static Object padrao(Method metodo) {
        Class<?> retorno = metodo.getReturnType();
        if (retorno == boolean.class) {
            return Boolean.FALSE;
        }
        if (retorno == int.class) {
            return 0;
        }
        return null;
    }
}
