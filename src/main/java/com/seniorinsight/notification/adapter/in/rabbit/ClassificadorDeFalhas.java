package com.seniorinsight.notification.adapter.in.rabbit;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Decide se uma falha ao processar uma mensagem é transitória (vale tentar de novo depois) ou
 * permanente (a mesma mensagem vai falhar sempre e deve ir para a dead-letter).
 */
final class ClassificadorDeFalhas {

    private ClassificadorDeFalhas() {
    }

    /**
     * Transitórias: problemas de infraestrutura que se resolvem sozinhos (banco fora do ar,
     * conexão perdida, timeout, deadlock). Todo o resto — JSON inválido, evento sem
     * identificador, violação de constraint, bug inesperado — é tratado como permanente, para
     * que uma mensagem defeituosa não fique voltando para a fila indefinidamente.
     */
    static boolean ehTransitoria(Throwable falha) {
        // O conjunto de visitadas protege contra cadeias de causa circulares (A -> B -> A).
        Set<Throwable> visitadas = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable atual = falha; atual != null && visitadas.add(atual); atual = atual.getCause()) {
            if (atual instanceof TransientDataAccessException
                    || atual instanceof RecoverableDataAccessException
                    // Inclui CannotGetJdbcConnectionException: o banco está inacessível.
                    || atual instanceof DataAccessResourceFailureException
                    || atual instanceof CannotCreateTransactionException
                    || atual instanceof SQLTransientException
                    || atual instanceof SQLRecoverableException) {
                return true;
            }
        }
        return false;
    }
}
