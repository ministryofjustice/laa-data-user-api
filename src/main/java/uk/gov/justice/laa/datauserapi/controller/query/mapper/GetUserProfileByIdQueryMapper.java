package uk.gov.justice.laa.datauserapi.controller.query.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.getuserprofilebyid.GetUserProfileByIdQuery;
import uk.gov.justice.laa.datauserapi.shared.Mapper;
import uk.gov.justice.laa.datauserapi.shared.MapperUtil;

import java.util.UUID;

@Component
public class GetUserProfileByIdQueryMapper implements Mapper<String, GetUserProfileByIdQuery> {

    @Override
    public GetUserProfileByIdQuery map(String id) {
        UUID uuid = MapperUtil.requestStringToUUID(id);
        return new GetUserProfileByIdQuery(uuid);
    }
}
