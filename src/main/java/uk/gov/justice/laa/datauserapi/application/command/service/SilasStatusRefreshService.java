package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class SilasStatusRefreshService {

    private final SilasStatusRefreshBatchService batchService;

    public SilasStatusRefreshService(SilasStatusRefreshBatchService batchService) {
        this.batchService = batchService;
    }

    @Async("silasStatusRefreshExecutor")
    public void refresh(String actorOid) {
        log.info("Starting asynchronous SiLAS status refresh by {}", actorOid);
        UUID afterId = null;
        int updatedAccounts = 0;
        int updatedProfiles = 0;
        while (true) {
            SilasStatusRefreshBatchService.BatchResult batch = batchService.refreshBatch(afterId, actorOid);
            if (batch.lastUserId() == null) {
                break;
            }
            afterId = batch.lastUserId();
            updatedAccounts += batch.updatedAccounts();
            updatedProfiles += batch.updatedProfiles();
            log.info("SiLAS refresh batch committed through user {}. Updated accounts: {}, updated profiles: {}",
                    afterId, batch.updatedAccounts(), batch.updatedProfiles());
        }
        log.info("SiLAS status refresh completed by {}. Updated accounts: {}, updated profiles: {}",
                actorOid, updatedAccounts, updatedProfiles);
    }
}
