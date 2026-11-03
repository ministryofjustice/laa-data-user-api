package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
        log.info("Starting asynchronous SiLAS status refresh",
                kv("actorEntraOid", actorOid));
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
            log.info("SiLAS refresh batch committed",
                    kv("lastProcessedUserId", afterId),
                    kv("updatedAccounts", batch.updatedAccounts()),
                    kv("updatedProfiles", batch.updatedProfiles()),
                    kv("actorEntraOid", actorOid));
        }
        log.info("SiLAS status refresh completed",
                kv("actorEntraOid", actorOid),
                kv("updatedAccounts", updatedAccounts),
                kv("updatedProfiles", updatedProfiles));
    }
}
