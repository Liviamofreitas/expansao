package br.com.engesoftware.sgdf.book;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Armazenamento em diretório, para desenvolvimento e teste.
 *
 * <p><b>Não é o bucket de produção</b>, e a diferença precisa ficar dita: aqui
 * a imutabilidade é imposta pelo código, lá é imposta pelo object lock do
 * provedor — que é o que sobrevive a um erro de código. Esta implementação
 * existe para que a lógica de montagem do book seja testável sem infraestrutura,
 * e é a razão de {@link ArmazenamentoImutavel} ser uma interface.
 *
 * <p>Para não dar falsa segurança, ela marca o arquivo como somente-leitura ao
 * gravar e mantém a retenção declarada, que os testes conferem.
 */
public final class ArmazenamentoEmDiretorio implements ArmazenamentoImutavel {

    private final Path raiz;
    private final Map<String, Retencao> retencoes = new LinkedHashMap<>();

    public ArmazenamentoEmDiretorio(Path raiz) {
        this.raiz = raiz;
    }

    @Override
    public void gravar(String chave, byte[] conteudo, Retencao retencao) {
        if (existe(chave)) {
            throw new ObjetoJaExiste(chave);
        }
        Path destino = raiz.resolve(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo, StandardOpenOption.CREATE_NEW);
            destino.toFile().setReadOnly();
            retencoes.put(chave, retencao);
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao gravar " + chave, e);
        }
    }

    @Override
    public boolean existe(String chave) {
        return Files.exists(raiz.resolve(chave));
    }

    @Override
    public byte[] ler(String chave) {
        try {
            return Files.readAllBytes(raiz.resolve(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler " + chave, e);
        }
    }

    /** A retenção com que o objeto foi gravado — o que o teste confere. */
    public Retencao retencaoDe(String chave) {
        return retencoes.get(chave);
    }
}
