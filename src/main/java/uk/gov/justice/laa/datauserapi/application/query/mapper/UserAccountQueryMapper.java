package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.Optional;

@Component
public class UserAccountQueryMapper {

    private final ModelMapper modelMapper;

    public UserAccountQueryMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public EntraUserDto toDto(Optional<EntraUser> user, String entraOid) {
        return modelMapper.map(user.orElseThrow(
                () -> new ResourceNotFoundException("User not found: " + entraOid)), EntraUserDto.class);
    }

    public Page<EntraUserDto> toPage(Page<EntraUser> users) {
        return users.map(user -> modelMapper.map(user, EntraUserDto.class));
    }
}
