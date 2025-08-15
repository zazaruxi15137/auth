package com.example.rednote.auth.model.feed.service;

import io.lettuce.core.StreamMessage;

public interface PendingClaimService {
    public String reclaimOnce(String startId);

}
