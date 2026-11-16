package com.seniorinsight.notification.application;

/** O titular pediu a exclusão da conta: não se criam dados novos para ele. */
public class TitularExcluidoException extends AcessoNegadoException {

    public TitularExcluidoException() {
        super("A conta foi excluída; não é possível registrar aparelhos para ela.");
    }
}
