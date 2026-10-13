package uk.gov.justice.laa.datauserapi.config.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevJwtDecoderConfigTest {

    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        jwtDecoder = new DevJwtDecoderConfig().jwtDecoder();
    }

    @Test
    void shouldDecodeSyntheticTokenPattern() {
        String oid = UUID.randomUUID().toString();
        String syntheticToken = "oid__" + oid + "_scope__user_data.admin";

        Jwt jwt = jwtDecoder.decode(syntheticToken);

        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaimAsString("oid")).isEqualTo(oid);
        assertThat(jwt.getClaimAsString("scp")).isEqualTo("user_data.admin");
    }

    @Test
    void shouldDecodeRealOrMockJwtWithoutSignatureVerification() throws Exception {
        String oid = UUID.randomUUID().toString();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("oid", oid)
                .claim("scp", "user_data.read")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(new MACSigner("01234567890123456789012345678901"));
        String jwtString = signedJWT.serialize();

        Jwt jwt = jwtDecoder.decode(jwtString);

        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaimAsString("oid")).isEqualTo(oid);
        assertThat(jwt.getClaimAsString("scp")).isEqualTo("user_data.read");
        assertThat(jwt.getClaimAsString("scp")).doesNotContain("user_data.admin");
    }

    @Test
    void shouldAppendApiScopesWhenTokenHasGraphScopes() throws Exception {
        String oid = UUID.randomUUID().toString();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("oid", oid)
                .claim("scp", "Directory.Read.All User.Read")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(new MACSigner("01234567890123456789012345678901"));
        String jwtString = signedJWT.serialize();

        Jwt jwt = jwtDecoder.decode(jwtString);

        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaimAsString("oid")).isEqualTo(oid);
        assertThat(jwt.getClaimAsString("scp")).contains("Directory.Read.All", "User.Read", "user_data.read");
        assertThat(jwt.getClaimAsString("scp")).doesNotContain("user_data.admin");
    }

    @Test
    void shouldDefaultToReadScopeWhenJwtLacksScpOrScopeClaim() throws Exception {
        String oid = UUID.randomUUID().toString();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("oid", oid)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(new MACSigner("01234567890123456789012345678901"));
        String jwtString = signedJWT.serialize();

        Jwt jwt = jwtDecoder.decode(jwtString);

        assertThat(jwt).isNotNull();
        assertThat(jwt.getClaimAsString("oid")).isEqualTo(oid);
        assertThat(jwt.getClaimAsString("scp")).isEqualTo("user_data.read");
    }

    @Test
    void shouldPreserveAdminScopeWhenPresentInSourceToken() throws Exception {
        String oid = UUID.randomUUID().toString();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-user")
                .claim("oid", oid)
                .claim("scp", "user_data.read user_data.admin")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600_000))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
        signedJWT.sign(new MACSigner("01234567890123456789012345678901"));

        Jwt jwt = jwtDecoder.decode(signedJWT.serialize());

        assertThat(jwt.getClaimAsString("scp")).contains("user_data.read", "user_data.admin");
    }

    @Test
    void shouldThrowWhenTokenIsMalformed() {
        assertThatThrownBy(() -> jwtDecoder.decode("not-a-valid-token"))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Malformed token");
    }

    @Test
    void shouldThrowWhenTokenIsNull() {
        assertThatThrownBy(() -> jwtDecoder.decode(null))
                .isInstanceOf(JwtException.class);
    }
}
