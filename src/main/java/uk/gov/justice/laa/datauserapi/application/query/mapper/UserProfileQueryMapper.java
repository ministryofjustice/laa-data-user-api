package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.ProfileVisibilityContext;
import uk.gov.justice.laa.datauserapi.dto.UserProfileDto;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.Optional;

@Component
public class UserProfileQueryMapper {

    private final ModelMapper modelMapper;

    public UserProfileQueryMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    /**
     * Maps a single loaded profile to both its detail DTO and its visibility facts in one pass, so callers
     * outside the mapper/repository packages never need to hold a reference to the {@link UserProfile} entity
     * themselves.
     */
    public Optional<ActiveProfileLookup> toActiveProfileLookup(Optional<UserProfile> profile) {
        return profile.map(value -> new ActiveProfileLookup(
                modelMapper.map(value, UserProfileDto.class),
                new ProfileVisibilityContext(true, value.getId(), value.getUserType(),
                        value.getFirm() == null ? null : value.getFirm().getId())));
    }

    public record ActiveProfileLookup(UserProfileDto profile, ProfileVisibilityContext visibilityContext) {
    }
}

