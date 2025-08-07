package com.sarinah.sales.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommonUtils {

    public URI dynamicParamBuilder (ObjectNode request, String url) {
        UriComponentsBuilder param = UriComponentsBuilder.fromUriString(url);
        Iterator<Map.Entry<String, JsonNode>> requestIterator = request.fields();

        while (requestIterator.hasNext()) {
            Map.Entry<String, JsonNode> requestEntry = requestIterator.next();
            param.queryParam(requestEntry.getKey(), requestEntry.getValue().asText());
        }
        return param.build().encode().toUri();
    }
}
