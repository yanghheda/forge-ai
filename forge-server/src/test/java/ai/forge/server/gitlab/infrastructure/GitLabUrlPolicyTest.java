package ai.forge.server.gitlab.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitLabUrlPolicyTest {

    @Test
    void acceptsHttpsPublicAddressAndNormalizesTrailingSlash() throws Exception {
        GitLabUrlPolicy policy = new GitLabUrlPolicy(
                false,
                List.of(),
                host -> new InetAddress[] {InetAddress.getByName("203.0.113.10")});

        assertThat(policy.validate("https://gitlab.example.com/"))
                .isEqualTo(URI.create("https://gitlab.example.com"));
    }

    @Test
    void rejectsMetadataPrivateLoopbackCredentialsAndNonHttps() throws Exception {
        GitLabUrlPolicy metadata = policyFor("169.254.169.254", false, List.of());
        GitLabUrlPolicy privateAddress = policyFor("10.2.3.4", false, List.of());
        GitLabUrlPolicy loopback = policyFor("127.0.0.1", false, List.of());

        assertThatThrownBy(() -> metadata.validate("https://metadata.example"))
                .isInstanceOf(UnsafeGitLabUrlException.class);
        assertThatThrownBy(() -> privateAddress.validate("https://gitlab.internal"))
                .isInstanceOf(UnsafeGitLabUrlException.class);
        assertThatThrownBy(() -> loopback.validate("https://localhost"))
                .isInstanceOf(UnsafeGitLabUrlException.class);
        assertThatThrownBy(() -> privateAddress.validate("http://gitlab.internal"))
                .isInstanceOf(UnsafeGitLabUrlException.class);
        assertThatThrownBy(() -> privateAddress.validate("https://token@gitlab.internal"))
                .isInstanceOf(UnsafeGitLabUrlException.class);
    }

    @Test
    void allowsExplicitPrivateCidrButNeverMetadataAndLimitsHttpToDevelopmentLocalhost() throws Exception {
        assertThat(policyFor("10.2.3.4", false, List.of("10.2.0.0/16"))
                        .validate("https://gitlab.internal"))
                .isEqualTo(URI.create("https://gitlab.internal"));
        assertThatThrownBy(() -> policyFor("169.254.169.254", false, List.of("169.254.0.0/16"))
                        .validate("https://metadata.internal"))
                .isInstanceOf(UnsafeGitLabUrlException.class);
        assertThat(policyFor("127.0.0.1", true, List.of()).validate("http://localhost:8080"))
                .isEqualTo(URI.create("http://localhost:8080"));
    }

    private static GitLabUrlPolicy policyFor(String address, boolean localHttp, List<String> cidrs)
            throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new GitLabUrlPolicy(localHttp, cidrs, host -> new InetAddress[] {resolved});
    }
}
