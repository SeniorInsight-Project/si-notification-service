package com.seniorinsight.notification.adapter.in.rabbit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;

class ClassificadorDeFalhasTest {

    static Stream<Throwable> transitorias() {
        return Stream.of(
                new CannotGetJdbcConnectionException("banco inacessível"),
                new CannotCreateTransactionException("não abriu transação"),
                new QueryTimeoutException("timeout"),
                new CannotAcquireLockException("deadlock"),
                new PessimisticLockingFailureException("lock"),
                new TransientDataAccessResourceException("recurso temporariamente indisponível"),
                new RecoverableDataAccessException("recuperável"),
                new SQLTransientConnectionException("conexão"),
                new SQLRecoverableException("conexão perdida"));
    }

    static Stream<Throwable> permanentes() {
        return Stream.of(
                new JsonParseException(null, "json inválido"),
                new IOException("corpo ilegível"),
                new IllegalArgumentException("evento sem alarmeId"),
                new DataIntegrityViolationException("constraint"),
                new DuplicateKeyException("chave duplicada"),
                new BadSqlGrammarException("insert", "INSERT ...", new SQLException("relation does not exist")),
                new IllegalStateException("bug"),
                new NullPointerException(),
                new SQLException("erro genérico"));
    }

    @ParameterizedTest
    @MethodSource("transitorias")
    void reconheceFalhasTransitorias(Throwable falha) {
        assertTrue(ClassificadorDeFalhas.ehTransitoria(falha), falha.getClass().getSimpleName());
    }

    @ParameterizedTest
    @MethodSource("permanentes")
    void trataDemaisFalhasComoPermanentes(Throwable falha) {
        assertFalse(ClassificadorDeFalhas.ehTransitoria(falha), falha.getClass().getSimpleName());
    }

    @Test
    void procuraCausaTransitoriaEmQualquerNivel() {
        Throwable falha = new IllegalStateException("nível 1",
                new RuntimeException("nível 2", new SQLTransientConnectionException("nível 3")));

        assertTrue(ClassificadorDeFalhas.ehTransitoria(falha));
    }

    @Test
    void naoEntraEmLoopComCadeiaDeCausasCircular() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b");
        a.initCause(b);
        b.initCause(a);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertFalse(ClassificadorDeFalhas.ehTransitoria(a)));
    }

    @Test
    void falhaNulaNaoEhTransitoria() {
        assertFalse(ClassificadorDeFalhas.ehTransitoria(null));
    }
}
