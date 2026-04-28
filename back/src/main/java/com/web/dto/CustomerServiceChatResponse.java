package com.web.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CustomerServiceChatResponse {

    private String answer;
    private BigDecimal confidence;
    private String route;
    private String sourceType;
    private List<CustomerServiceCitation> citations = new ArrayList<>();
    private List<CustomerServiceAction> actions = new ArrayList<>();
    private List<CustomerServiceHitLog> hitLogs = new ArrayList<>();
    private String fallbackReason;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public List<CustomerServiceCitation> getCitations() {
        return citations;
    }

    public void setCitations(List<CustomerServiceCitation> citations) {
        this.citations = citations;
    }

    public List<CustomerServiceAction> getActions() {
        return actions;
    }

    public void setActions(List<CustomerServiceAction> actions) {
        this.actions = actions;
    }

    public List<CustomerServiceHitLog> getHitLogs() {
        return hitLogs;
    }

    public void setHitLogs(List<CustomerServiceHitLog> hitLogs) {
        this.hitLogs = hitLogs;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }
}
