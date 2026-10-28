package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserAccountQueryMapperTest {

    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final UserAccountQueryMapper mapper = new UserAccountQueryMapper(modelMapper);

    @Test
    void mapsFoundUser() {
        EntraUser user = EntraUser.builder().build();
        EntraUserDto dto = new EntraUserDto();
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(dto);

        assertThat(mapper.toDto(Optional.of(user), "oid")).isSameAs(dto);
    }

    @Test
    void reportsMissingUserWithoutMapping() {
        assertThatThrownBy(() -> mapper.toDto(Optional.empty(), "missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: missing");
        verifyNoInteractions(modelMapper);
    }

    @Test
    void mapsPageWhilePreservingPagination() {
        EntraUser user = EntraUser.builder().build();
        EntraUserDto dto = new EntraUserDto();
        when(modelMapper.map(user, EntraUserDto.class)).thenReturn(dto);
        Page<EntraUser> users = new PageImpl<>(List.of(user), PageRequest.of(1, 10), 25);

        Page<EntraUserDto> result = mapper.toPage(users);

        assertThat(result.getContent()).containsExactly(dto);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(25);
    }

    @Test
    void mapsEmptyPageWithoutMappingUsers() {
        Page<EntraUserDto> result = mapper.toPage(Page.empty(PageRequest.of(0, 10)));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getSize()).isEqualTo(10);
        verifyNoInteractions(modelMapper);
    }
}
