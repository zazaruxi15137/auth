package com.example.gateway_service.gateway_service.model.feed.service;

import io.lettuce.core.StreamMessage;

public interface PendingClaimService {
    public void reclaimOnce(String startId);

}
