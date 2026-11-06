package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.dao.RetailShippingQuoteRepository;
import edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.entity.RetailShippingQuote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes a shipping quote after the GHTK HTTP call completed outside any transaction. */
@Service
@RequiredArgsConstructor
public class DurableShippingQuoteWriter {
    private final RetailShippingQuoteRepository quotes;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetailShippingQuote store(RetailShippingQuote quote) {
        return quotes.saveAndFlush(quote);
    }
}
