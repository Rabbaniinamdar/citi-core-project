
package com.citicore.user.exception;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String s) {
        super(s);
    }
}