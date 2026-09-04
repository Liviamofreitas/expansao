package br.com.engesoftware.sgdf.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * O serviço.
 *
 * <p>Só esta camada conhece o Spring. Os pacotes {@code extracao},
 * {@code documento}, {@code validacao}, {@code classificacao},
 * {@code conciliacao}, {@code book}, {@code privacidade} e {@code seguranca}
 * não importam nada do framework — a web chama o domínio, nunca o contrário.
 * É o que mantém a lógica de negócio testável sem subir aplicação, e o que
 * permitiria trocar o framework sem reescrever nenhuma regra.
 */
@SpringBootApplication
public class SgdfApplication {

    public static void main(String[] args) {
        SpringApplication.run(SgdfApplication.class, args);
    }
}
