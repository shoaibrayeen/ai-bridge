package com.aibridge.filter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EndpointUrlValidatorTest {

    @InjectMocks
    private EndpointUrlValidator validator;

    @BeforeEach
    void injectConfigDefaults() throws Exception {
        setConfig(false, Optional.of(List.of("example.com", "*.example.com", "*.cloud.ibm.com", "*.nip.io")));
    }

    private void setConfig(boolean allowHttp, Optional<List<String>> allowedHosts) throws Exception {
        Field allow = EndpointUrlValidator.class.getDeclaredField("allowHttp");
        allow.setAccessible(true);
        allow.setBoolean(validator, allowHttp);
        Field hosts = EndpointUrlValidator.class.getDeclaredField("allowedProviderHosts");
        hosts.setAccessible(true);
        hosts.set(validator, allowedHosts);
    }

    @Test
    void validate_nullUrl_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test
    void validate_blankUrl_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("   "));
    }

    @Test
    void validate_validHttpsUrl_succeeds() {
        assertDoesNotThrow(() -> validator.validate("https://example.com/v1"));
    }

    @Test
    void validate_httpsUrlTrimmed_succeeds() {
        assertDoesNotThrow(() -> validator.validate("  https://example.com/path  "));
    }

    @Test
    void validate_httpWhenNotAllowed_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("http://example.com/"));
    }

    @Test
    void validate_httpWhenAllowed_succeeds() throws Exception {
        setConfig(true, Optional.of(List.of("example.com")));
        assertDoesNotThrow(() -> validator.validate("http://example.com/"));
    }

    @Test
    void validate_unsupportedScheme_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("ftp://example.com/x"));
    }

    @Test
    void validate_localhost_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://localhost/api"));
    }

    @Test
    void validate_subdomainLocalhost_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://app.localhost/"));
    }

    @Test
    void validate_loopbackIpv4Literal_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://127.0.0.1/"));
    }

    @Test
    void validate_loopbackIpv6Literal_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://[::1]/"));
    }

    @Test
    void validate_schemeWithoutHost_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https:"));
    }

    @Test
    void validate_httpsWithEmptyHost_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https:///path"));
    }

    @Test
    void validate_unparseableUri_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("http://[not-ipv6"));
    }

    @Test
    void validate_emptyAllowlist_throws() throws Exception {
        setConfig(false, Optional.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://example.com/"));
    }

    @Test
    void validate_missingAllowlistConfig_throws() throws Exception {
        setConfig(false, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://example.com/"));
    }

    @Test
    void validate_hostNotInAllowlist_throws() throws Exception {
        setConfig(false, Optional.of(List.of("other.example")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://example.com/"));
    }

    @Test
    void validate_wildcardCloudIbmHost_succeeds() {
        assertDoesNotThrow(() -> validator.validate("https://us-south.ml.cloud.ibm.com/v1"));
    }

    @Test
    void validate_wildcardExampleCom_succeeds() {
        assertDoesNotThrow(() -> validator.validate("https://www.example.com/"));
    }

    @Test
    void validate_exactHostCaseInsensitive_succeeds() {
        assertDoesNotThrow(() -> validator.validate("https://EXAMPLE.COM/path"));
    }

    @Test
    void validate_unknownHost_throws() throws Exception {
        setConfig(false, Optional.of(List.of("zzzz-nonexistent-host.invalid")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://zzzz-nonexistent-host.invalid/"));
    }

    @Test
    void validate_resolvesToLoopbackViaNipIo_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://127.0.0.1.nip.io/"));
    }

    @Test
    void isPrivateOrNonRoutable_loopbackIpv4_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByName("127.0.0.1")));
    }

    @Test
    void isPrivateOrNonRoutable_loopbackIpv6_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByName("::1")));
    }

    @Test
    void isPrivateOrNonRoutable_linkLocalIpv6_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByName("fe80::1")));
    }

    @Test
    void isPrivateOrNonRoutable_siteLocalIpv4_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 192, (byte) 168, 1, 1 })));
    }

    @Test
    void isPrivateOrNonRoutable_classA10_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { 10, 0, 0, 1 })));
    }

    @Test
    void isPrivateOrNonRoutable_classB172_low_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 172, 16, 0, 1 })));
    }

    @Test
    void isPrivateOrNonRoutable_classB172_high_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 172, 31, (byte) 255, (byte) 255 })));
    }

    @Test
    void isPrivateOrNonRoutable_classB172_belowRange_false() throws UnknownHostException {
        assertFalse(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 172, 15, 0, 1 })));
    }

    @Test
    void isPrivateOrNonRoutable_classB172_aboveRange_false() throws UnknownHostException {
        assertFalse(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 172, 32, 0, 1 })));
    }

    @Test
    void isPrivateOrNonRoutable_linkLocal169_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 169, (byte) 254, 1, 1 })));
    }

    @Test
    void isPrivateOrNonRoutable_uniqueLocalIpv6_fc_true() throws UnknownHostException {
        byte[] addr = new byte[16];
        addr[0] = (byte) 0xfc;
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(addr)));
    }

    @Test
    void isPrivateOrNonRoutable_uniqueLocalIpv6_fd_true() throws UnknownHostException {
        byte[] addr = new byte[16];
        addr[0] = (byte) 0xfd;
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(addr)));
    }

    @Test
    void isPrivateOrNonRoutable_publicIpv4_false() throws UnknownHostException {
        assertFalse(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { 8, 8, 8, 8 })));
    }

    @Test
    void isPrivateOrNonRoutable_publicIpv6_false() throws UnknownHostException {
        byte[] addr = new byte[16];
        addr[0] = 0x20;
        addr[1] = 0x01;
        assertFalse(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(addr)));
    }

    @Test
    void isPrivateOrNonRoutable_anyLocalIpv4_true() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { 0, 0, 0, 0 })));
    }

    @Test
    void hostMatchesAllowlist_exactMatch_true() {
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("example.com", List.of("example.com")));
    }

    @Test
    void hostMatchesAllowlist_exactMatch_caseInsensitive() {
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("ExAmPlE.CoM", List.of("example.com")));
    }

    @Test
    void hostMatchesAllowlist_wildcardSuffix_true() {
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("us-south.ml.cloud.ibm.com", List.of("*.cloud.ibm.com")));
    }

    @Test
    void hostMatchesAllowlist_wildcardEqualsSuffixHost_true() {
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("cloud.ibm.com", List.of("*.cloud.ibm.com")));
    }

    @Test
    void hostMatchesAllowlist_noMatch_false() {
        assertFalse(EndpointUrlValidator.hostMatchesAllowlist("evil.com", List.of("example.com", "*.ibm.com")));
    }

    @Test
    void hostMatchesAllowlist_nullPattern_skipped() {
        List<String> patterns = new ArrayList<>();
        patterns.add(null);
        patterns.add("ok.com");
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("ok.com", patterns));
    }

    @Test
    void hostMatchesAllowlist_emptyPattern_skipped() {
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("ok.com", List.of("  ", "", "ok.com")));
    }

    @Test
    void hostMatchesAllowlist_wildcardOnlyStarDot_skipped() {
        assertFalse(EndpointUrlValidator.hostMatchesAllowlist("anything.com", List.of("*.")));
    }

    @Test
    void hostMatchesAllowlist_wildcardEmptySuffix_skipped() {
        assertFalse(EndpointUrlValidator.hostMatchesAllowlist("x.com", List.of("*. ")));
    }

    @Test
    void hostMatchesAllowlist_patternWithoutStarPrefix_requiresExactLiteral() {
        assertFalse(EndpointUrlValidator.hostMatchesAllowlist("sub.example.com", List.of("*example.com")));
        assertTrue(EndpointUrlValidator.hostMatchesAllowlist("*example.com", List.of("*example.com")));
    }

    @Test
    void isPrivateOrNonRoutable_ipv4_127_2_returnsTrue() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { 127, 0, 0, 2 })));
    }

    @Test
    void isPrivateOrNonRoutable_ipv6_linkLocal_fe80_returnsTrue() throws UnknownHostException {
        byte[] addr = new byte[16];
        addr[0] = (byte) 0xfe;
        addr[1] = (byte) 0x80;
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(addr)));
    }

    @Test
    void isPrivateOrNonRoutable_globalIpv6_returnsTrue() throws UnknownHostException {
        byte[] addr = new byte[16];
        addr[0] = (byte) 0xfe;
        addr[1] = (byte) 0x00;
        assertFalse(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(addr)));
    }

    @Test
    void validate_ipv4LiteralHost_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://10.0.0.1/api"));
    }

    @Test
    void validate_ipv6Bracket_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://[::1]/api"));
    }

    @Test
    void validate_169_254_ipLiteral_throws() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate("https://169.254.1.1/api"));
    }

    @Test
    void isPrivateOrNonRoutable_172_16_exact_boundary() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 172, 16, 0, 0 })));
    }

    @Test
    void isPrivateOrNonRoutable_172_31_exact_boundary() throws UnknownHostException {
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(InetAddress.getByAddress(new byte[] { (byte) 172, 31, (byte) 255, (byte) 254 })));
    }

    @Test
    void isPrivateOrNonRoutable_ipv4_mapped_ipv6() throws UnknownHostException {
        byte[] addr = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 10, 0, 0, 1};
        InetAddress a = InetAddress.getByAddress(addr);
        assertTrue(EndpointUrlValidator.isPrivateOrNonRoutable(a));
    }
}
