package br.com.engesoftware.sgdf.web;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

/**
 * A fiacao do Spring, verificada sem subir o Spring.
 *
 * <p><b>Por que este arquivo existe.</b> O mesmo defeito derrubou a aplicacao
 * TRES VEZES numa unica sessao:
 *
 * <ol>
 *   <li>dezesseis classes de persistencia eram {@code public final class} e
 *       @Bean com {@code ScopedProxyMode.TARGET_CLASS} — a aplicacao nunca
 *       subiu, em nenhuma versao, e 1320 assercoes verdes nao diziam nada;
 *   <li>tres horas depois, {@code VarreduraDeCiclo} foi registrada como bean do
 *       mesmo jeito, e ela tambem era final;
 *   <li>e o {@code Pipeline}, que e final, quase virou bean de requisicao.
 * </ol>
 *
 * <p>Proxy CGLIB funciona criando uma SUBCLASSE. Classe final nao tem subclasse,
 * e o contexto morre na partida com "Cannot subclass final class". O teste de
 * fumaca do CI pega isso — mas so depois de construir a imagem, subir banco e
 * aplicacao, o que leva minutos e acontece longe de quem escreveu a linha.
 *
 * <p>Aqui custa milissegundos e roda antes. Nao substitui a fumaca: aquela prova
 * que a aplicacao SOBE; esta prova que esta armadilha especifica nao voltou.
 */
public final class TestesDeFiacao {

    static int passaram = 0;
    static final List<String> falhas = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        List<String> proibidas = new ArrayList<>();
        int proxiados = 0;

        for (Class<?> classe : classesDoProjeto()) {
            for (Method metodo : metodosDe(classe)) {
                if (!metodo.isAnnotationPresent(Bean.class)) {
                    continue;
                }
                Scope escopo = metodo.getAnnotation(Scope.class);
                if (escopo == null || escopo.proxyMode() != ScopedProxyMode.TARGET_CLASS) {
                    continue;
                }
                proxiados++;
                Class<?> tipo = metodo.getReturnType();
                if (Modifier.isFinal(tipo.getModifiers())) {
                    proibidas.add(classe.getSimpleName() + "." + metodo.getName()
                            + " devolve " + tipo.getSimpleName() + ", que e FINAL");
                }
            }
        }

        // A CONTAGEM VEM PRIMEIRO, E NAO E ZELO EXCESSIVO.
        //
        // Sem ela, este teste passaria com zero beans encontrados — e zero e
        // exatamente o que ele devolveria se alguem renomeasse o pacote, se o
        // diretorio de classes mudasse de lugar, ou se a varredura quebrasse em
        // silencio. Um teste que nao acha nada e um teste que aprova tudo.
        ok("Fiacao . a varredura encontrou beans com proxy de classe (achei "
                + proxiados + ")", proxiados >= 10);

        ok("Fiacao . nenhum bean com ScopedProxyMode.TARGET_CLASS e de classe final"
                + (proibidas.isEmpty() ? "" : " — " + proibidas), proibidas.isEmpty());

        System.out.println();
        falhas.forEach(f -> System.out.println("  FALHA " + f));
        System.out.printf("%d/%d testes passaram.%n", passaram, passaram + falhas.size());
        if (!falhas.isEmpty()) {
            System.exit(1);
        }
    }

    /** Todas as classes compiladas do projeto, a partir de onde ESTA classe esta. */
    static List<Class<?>> classesDoProjeto() throws Exception {
        File raiz = new File(TestesDeFiacao.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        // As classes de teste e as de producao podem estar em diretorios
        // diferentes (target/test-classes e target/classes). Procura as duas.
        List<File> raizes = new ArrayList<>();
        raizes.add(raiz);
        File irma = new File(raiz.getParentFile(), "classes");
        if (irma.isDirectory() && !irma.equals(raiz)) {
            raizes.add(irma);
        }

        List<Class<?>> classes = new ArrayList<>();
        for (File r : raizes) {
            File pacote = new File(r, "br/com/engesoftware/sgdf");
            if (pacote.isDirectory()) {
                coletar(r, pacote, classes);
            }
        }
        return classes;
    }

    static void coletar(File raiz, File dir, List<Class<?>> destino) {
        File[] itens = dir.listFiles();
        if (itens == null) {
            return;
        }
        for (File f : itens) {
            if (f.isDirectory()) {
                coletar(raiz, f, destino);
            } else if (f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String nome = raiz.toPath().relativize(f.toPath()).toString()
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                try {
                    destino.add(Class.forName(nome, false,
                            TestesDeFiacao.class.getClassLoader()));
                } catch (Throwable ignorado) {
                    // Classe que nao carrega sem dependencia opcional nao e
                    // @Configuration; ignorar aqui e melhor que derrubar a
                    // varredura inteira por causa dela.
                }
            }
        }
    }

    static Method[] metodosDe(Class<?> classe) {
        try {
            return classe.getDeclaredMethods();
        } catch (Throwable ignorado) {
            return new Method[0];
        }
    }

    static void ok(String nome, boolean condicao) {
        if (condicao) {
            passaram++;
            System.out.println("  ok    " + nome);
        } else {
            falhas.add(nome);
        }
    }
}
