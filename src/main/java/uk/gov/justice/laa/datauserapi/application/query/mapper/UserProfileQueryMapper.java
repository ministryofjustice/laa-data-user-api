package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

import java.util.Optional;

@Component
public class UserProfileQueryMapper {

    private final ModelMapper modelMapper;

    public UserProfileQueryMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public UserProfileDto toActiveProfile(Optional<UserProfile> profile, String userEntraObjectId) {
        return profile.map(value -> modelMapper.map(value, UserProfileDto.class))
                .orElseThrow(() -> UserProfileNotFoundException.activeProfileForUser(userEntraObjectId));
    }
}
