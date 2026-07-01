package uk.gov.justice.laa.datauserapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.dto.UserDto;
import uk.gov.justice.laa.datauserapi.dto.UserSearchCriteria;

public interface UserService {
    Page<UserDto> findUsers(UserSearchCriteria criteria, Pageable pageable);
    java.util.Optional<uk.gov.justice.laa.datauserapi.dto.UserDetailDto> getUserById(java.util.UUID id);
}
