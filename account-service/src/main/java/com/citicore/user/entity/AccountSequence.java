
package com.citicore.user.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "account_sequence")
public class AccountSequence {

    @Id
    private Long id;

    private Long nextVal;
    public AccountSequence(){

    }
    public AccountSequence(Long id, Long nextVal) {
        this.id = id;
        this.nextVal = nextVal;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setNextVal(Long nextVal) {
        this.nextVal = nextVal;
    }

    public Long getId() {
        return id;
    }

    public Long getNextVal() {
        return nextVal;
    }
}