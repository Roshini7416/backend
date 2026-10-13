package com.pulsevote.model;

// JSON sent by React when voting: { "optionId": 3 }
public class VoteRequest {

    private Long optionId;

    public Long getOptionId() { return optionId; }
    public void setOptionId(Long optionId) { this.optionId = optionId; }
}
