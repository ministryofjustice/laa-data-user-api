package uk.gov.justice.laa.datauserapi.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.gov.justice.laa.datauserapi.dto.UserDto;
import uk.gov.justice.laa.datauserapi.dto.UserSearchCriteria;
import uk.gov.justice.laa.datauserapi.service.UserService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getUsers_shouldReturnOkAndPassParameters() throws Exception {
        when(userService.findUsers(any(UserSearchCriteria.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/users")
                        .param("id", "silas-1")
                        .param("oid", "oid-1", "oid-2")
                        .param("role-name", "supervisor")
                        .param("firm", "123")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "email,asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(userService).findUsers(
                argThat(criteria -> 
                    criteria.ids().contains("silas-1") &&
                    criteria.oids().containsAll(List.of("oid-1", "oid-2")) &&
                    criteria.roleNames().contains("supervisor") &&
                    criteria.firms().contains("123") &&
                    criteria.emails() == null
                ),
                argThat(pageable -> 
                    pageable.getPageNumber() == 0 &&
                    pageable.getPageSize() == 20 &&
                    pageable.getSort().getOrderFor("email").isAscending()
                )
        );
    }
}
