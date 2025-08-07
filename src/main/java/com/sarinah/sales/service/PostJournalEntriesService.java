package com.sarinah.sales.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.sales.adaptor.SarinahGetModulServiceAdaptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostJournalEntriesService {
    private final SarinahGetModulServiceAdaptor sarinahGetModulServiceAdaptor;

    public ArrayNode execute(ObjectNode request) {
        return sarinahGetModulServiceAdaptor.getJournalEntries(request);

    }
}
