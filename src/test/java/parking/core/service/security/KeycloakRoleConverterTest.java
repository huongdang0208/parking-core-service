package parking.core.service.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {
    @Test
    void mapsOnlyAllowListedRealmRoles() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles",
                List.of("admin", "offline_access", "uma_authorization", "VIEWER", "admin"))));

        assertThat(KeycloakRoleConverter.authorities(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_VIEWER");
    }

    @Test
    void tokenWithoutRealmAccess_hasNoAuthorities() {
        assertThat(KeycloakRoleConverter.authorities(jwt(Map.of("scope", "openid")))).isEmpty();
    }

    @Test
    void malformedRolesClaim_isIgnored() {
        assertThat(KeycloakRoleConverter.authorities(jwt(Map.of("realm_access", Map.of("roles", "ADMIN")))))
                .isEmpty();
    }

    @Test
    void principalNameIsPreferredUsername() {
        Jwt jwt = jwt(Map.of("preferred_username", "alice"));

        assertThat(new KeycloakRoleConverter().convert(jwt).getName()).isEqualTo("alice");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }
}
