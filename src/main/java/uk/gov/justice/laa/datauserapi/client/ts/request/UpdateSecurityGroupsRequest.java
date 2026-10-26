package uk.gov.justice.laa.datauserapi.client.ts.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSecurityGroupsRequest implements Serializable {
    private Set<String> groups;
}
