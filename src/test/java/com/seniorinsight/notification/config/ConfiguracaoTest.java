package com.seniorinsight.notification.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Regressão: fora do perfil "local" não pode haver credencial padrão no application.yml, para que
 * a produção nunca suba com a senha de desenvolvimento quando faltar um secret.
 */
class ConfiguracaoTest {

    @ParameterizedTest
    @CsvSource({
            "spring.datasource.username, ${DB_USERNAME}",
            "spring.datasource.password, ${DB_PASSWORD}",
            "spring.rabbitmq.username, ${RABBITMQ_USERNAME}",
            "spring.rabbitmq.password, ${RABBITMQ_PASSWORD}"})
    void credenciaisNaoTemValorPadrao(String propriedade, String esperado) throws IOException {
        assertEquals(esperado, String.valueOf(documentoPrincipal().getProperty(propriedade)));
    }

    @ParameterizedTest
    @CsvSource({
            "spring.rabbitmq.listener.simple.acknowledge-mode, manual",
            "spring.rabbitmq.listener.simple.default-requeue-rejected, false",
            "management.endpoints.web.exposure.include, health",
            "management.endpoint.health.show-details, never"})
    void mantemConfiguracoesDeSegurancaEConfiabilidade(String propriedade, String esperado) throws IOException {
        assertEquals(esperado, String.valueOf(documentoPrincipal().getProperty(propriedade)));
    }

    private static PropertySource<?> documentoPrincipal() throws IOException {
        List<PropertySource<?>> documentos = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        return documentos.get(0);
    }
}
