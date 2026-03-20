package io.github.vfedoriv.expensetracker.exception;

public class DeletionBlockedException extends RuntimeException {

    public DeletionBlockedException(String message) {
        super(message);
    }
}
