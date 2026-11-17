package parking.core.service.security;

import parking.core.service.api.SiteController;
import parking.core.service.common.VerificationMode;
import io.github.huongdang0208.accessgate.persistence.AccessSessionRepository;
import parking.core.service.persistence.SiteEntity;
import io.github.huongdang0208.accessgate.persistence.SiteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SiteController.class,
        properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test")
@Import(SecurityConfig.class)
class SiteControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SiteRepository sites;
    @MockitoBean AccessSessionRepository sessions;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/sites")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutKnownRole_isForbidden() throws Exception {
        mvc.perform(get("/api/v1/sites").with(jwt())).andExpect(status().isForbidden());
    }

    @Test
    void viewer_canRead() throws Exception {
        when(sites.findAll()).thenReturn(List.of(new SiteEntity("Demo", VerificationMode.PLATE_ONLY)));

        mvc.perform(get("/api/v1/sites").with(role("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verificationMode").value("PLATE_ONLY"));
    }

    @Test
    void viewerAndOperator_cannotChangeVerificationMode() throws Exception {
        mvc.perform(changeMode("PLATE_ONLY").with(role("VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(changeMode("PLATE_ONLY").with(role("OPERATOR"))).andExpect(status().isForbidden());
    }

    @Test
    void admin_canChangeVerificationMode() throws Exception {
        when(sites.findById(1L)).thenReturn(Optional.of(new SiteEntity("Demo", VerificationMode.FACE_PLATE)));

        mvc.perform(changeMode("PLATE_ONLY").with(role("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationMode").value("PLATE_ONLY"));
    }

    @Test
    void invalidMode_isBadRequest() throws Exception {
        mvc.perform(changeMode("TELEPATHY").with(role("ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void unknownSite_isNotFound() throws Exception {
        when(sites.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/sites/99/open-sessions").with(role("VIEWER")))
                .andExpect(status().isNotFound());
    }

    private static MockHttpServletRequestBuilder changeMode(String mode) {
        return put("/api/v1/sites/1/verification-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"" + mode + "\"}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
