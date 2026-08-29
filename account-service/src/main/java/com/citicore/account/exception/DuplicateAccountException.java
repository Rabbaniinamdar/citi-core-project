
package com.citicore.account.exception;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String s) {
        super(s);
    }
}