package com.sarinah.sales.controller;


import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sarinah.sales.service.PostJournalEntriesService;
import com.sarinah.sales.service.PostLoyaltyMemberService;
import com.sarinah.sales.service.PostPosHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sarinah-forwarder/v1/modul")
public class ModuleController {
    private final PostLoyaltyMemberService postLoyaltyMemberService;
    private final PostPosHistoryService postPosHistoryService;
    private final PostJournalEntriesService postJournalEntriesService;

    @PostMapping(value = "/member-loyalty")
    public ArrayNode postLoyaltyMemberResponse(@RequestBody ObjectNode request) {
        return postLoyaltyMemberService.execute(request);
    }

    @PostMapping(value = "/pos-order-history")
    public ArrayNode postOrderHistoryResponse(@RequestBody ObjectNode request) {
        return postPosHistoryService.execute(request);
    }

    @PostMapping(value = "/journal-entries")
    public ArrayNode postJournalEntriesResponse(@RequestBody ObjectNode request) {
        return postJournalEntriesService.execute(request);
    }


}
