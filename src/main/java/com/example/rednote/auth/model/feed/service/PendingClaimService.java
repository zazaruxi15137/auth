package com.example.rednote.auth.model.feed.service;

import io.lettuce.core.StreamMessage;

public interface PendingClaimService {
    public void reclaimOnce(String startId);

}
