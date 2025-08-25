package com.sarinah.sales.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.sarinah.sales.service.PostScanBarcodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sarinah-forwarder/v1/modul")
public class ScanPromotionController {
    private final PostScanBarcodeService postScanBarcodeService;

    @PostMapping(value = "/barcode")
    public ObjectNode postScanResponse(@RequestBody ObjectNode request) {
        return postScanBarcodeService.execute(request);
    }
}
