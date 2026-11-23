package uk.gov.justice.laa.datauserapi.application.command.service;

import uk.gov.justice.laa.datauserapi.application.command.dto.CcmsSyncProfile;

import java.time.LocalDateTime;
import java.util.Set;

public interface CcmsSyncSender {
    void sendSyncMessage(CcmsSyncProfile profile, String appOid, Set<String> roles, LocalDateTime timestamp);
}
