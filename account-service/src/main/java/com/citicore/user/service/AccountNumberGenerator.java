package com.citicore.user.service;

import com.citicore.user.entity.AccountSequence;
import com.citicore.user.repository.AccountSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AccountNumberGenerator {

    private final AccountSequenceRepository sequenceRepository;

    public AccountNumberGenerator(AccountSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateAccountNumber() {

        AccountSequence seq = sequenceRepository.findById(1L)
                .orElseGet(() -> {
                    AccountSequence newSeq = new AccountSequence();
                    newSeq.setId(1L);
                    newSeq.setNextVal(1L); // start from 1
                    return sequenceRepository.save(newSeq);
                });

        Long current = seq.getNextVal();

        seq.setNextVal(current + 1);
        sequenceRepository.save(seq);

        String formattedNumber = String.format("%012d", current);

        return "CITI" + formattedNumber;
    }
}