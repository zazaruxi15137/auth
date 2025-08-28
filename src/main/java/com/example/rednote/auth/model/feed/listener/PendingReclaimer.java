package com.example.rednote.auth.model.feed.listener;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.rednote.auth.model.feed.service.PendingClaimService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// ---- Lettuce 底层 API ----
import jakarta.annotation.PostConstruct;


@Slf4j
@Component
@RequiredArgsConstructor
public class PendingReclaimer {

    private final PendingClaimService pendingClaimService;

    @Value("${app.feed.reclaim.fixed-delay-ms}") private long fixedDelay;
    // @Qualifier("reclaimExecutor") 
    // private final ScheduledExecutorService  reclaimExecutor;
    // @PostConstruct
    // public void init() {
    //     reclaimExecutor.scheduleWithFixedDelay(
    //         this::safeReclaimWrapper,
    //         0,
    //         fixedDelay,
    //         TimeUnit.MILLISECONDS);
    //     log.info("Feed pending reclaimer started via feedExecutor.");
    // }
    // @Scheduled(cron = "0 0 3 * * ?")
    @Scheduled(fixedDelayString = "${app.feed.reclaim.fixed-delay-ms}")
    private void safeReclaimWrapper() {
        try {
            pendingClaimService.reclaimOnce("0-0");
        } catch (Exception e) {
            log.error("Scheduled reclaim task failed", e);
        }
    }

    

}