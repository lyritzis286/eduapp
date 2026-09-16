package gr.aueb.cf.eduapp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import gr.aueb.cf.eduapp.authentication.AuthenticationService;
import gr.aueb.cf.eduapp.authentication.JwtService;
import gr.aueb.cf.eduapp.dto.AuthenticationRequestDTO;
import gr.aueb.cf.eduapp.dto.AuthenticationResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice test: only {@link AuthRestController} and the web layer are
 * loaded, {@link AuthenticationService} is a Mockito double. Security filters
 * are disabled since this endpoint is permit-all and authentication itself is
 * the collaborator under test's responsibility, not the controller's.
 */
@WebMvcTest(AuthRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Built directly rather than @Autowired: Boot 4's auto-configured ObjectMapper
    // bean is the new Jackson 3 (tools.jackson) type, not com.fasterxml.jackson's;
    // a plain instance is all this simple request DTO needs.
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthenticationService authenticationService;

    // Not used directly: @WebMvcTest picks up JwtAuthenticationFilter (a Filter bean)
    // regardless of addFilters=false, so its constructor dependencies must resolve
    // for the context to start.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void authenticate_returns200WithToken_onValidCredentials() throws Exception {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO("jdoe", "Passw0rd!");
        when(authenticationService.authenticate(any(AuthenticationRequestDTO.class)))
                .thenReturn(new AuthenticationResponseDTO("jwt-token-value"));

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-value"));
    }

    @Test
    void authenticate_returns401_whenCredentialsAreInvalid() throws Exception {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO("jdoe", "wrong-password");
        when(authenticationService.authenticate(any(AuthenticationRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void authenticate_returns500_whenUnexpectedErrorOccurs() throws Exception {
        AuthenticationRequestDTO request = new AuthenticationRequestDTO("jdoe", "Passw0rd!");
        when(authenticationService.authenticate(any(AuthenticationRequestDTO.class)))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/v1/auth/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}
