package com.sarinah.sales.adaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.sales.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@RequiredArgsConstructor
@Component
public class SarinahGetModulServiceAdaptor {
    @Value("${sarinah-portal.loyalty.url}")
    private String loyaltyUrl;

    @Value("${sarinah-portal.pos-order-history.url}")
    private String posHistoryUrl;

    @Value("${sarinah-portal.journal-entries-coa-harmonisasi.url}")
    private String journalEntriesUrl;
    private final CommonUtils commonUtils;
    private final RestClient defaultPointRestClient;

    public ArrayNode getLoyalty(ObjectNode request) {

        JsonNode root = defaultPointRestClient
                .post()
                .uri(commonUtils.dynamicParamBuilder(request,loyaltyUrl))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);;                 // baca sebagai JsonNode


        if (root == null) {
            return JsonNodeFactory.instance.arrayNode();
        }


        if (root.isArray()) {
            return (ArrayNode) root;
        }


        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(root);
        return arr;
    }

    public ArrayNode getPosHistory(ObjectNode request) {
        JsonNode root = defaultPointRestClient
                .post()
                .uri(commonUtils.dynamicParamBuilder(request,posHistoryUrl))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);;                 // baca sebagai JsonNode


        if (root == null) {
            return JsonNodeFactory.instance.arrayNode();
        }


        if (root.isArray()) {
            return (ArrayNode) root;
        }


        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(root);
        return arr;

    }

    public ArrayNode getJournalEntries(ObjectNode request) {
        JsonNode root = defaultPointRestClient
                .post()
                .uri(commonUtils.dynamicParamBuilder(request,journalEntriesUrl))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);;                 // baca sebagai JsonNode


        if (root == null) {
            return JsonNodeFactory.instance.arrayNode();
        }


        if (root.isArray()) {
            return (ArrayNode) root;
        }


        ArrayNode arr = JsonNodeFactory.instance.arrayNode();
        arr.add(root);
        return arr;
    }

}
