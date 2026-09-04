package ai.forge.server.gitlab.infrastructure;

import ai.forge.server.gitlab.application.GitLabBaseUrlPolicy;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitLabUrlPolicy implements GitLabBaseUrlPolicy {

    @FunctionalInterface
    interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /* 开发配置中唯一允许 HTTP localhost 的显式开关。 */
    private final boolean allowLocalHttp;

    /* 私有 GitLab DNS 结果可命中的显式 CIDR 白名单。 */
    private final List<IpCidr> privateAllowlist;

    /* 可替换的 DNS 解析边界，用于测试重绑定前置校验。 */
    private final DnsResolver resolver;

    @Autowired
    public GitLabUrlPolicy(
            @Value("${forge.gitlab.allow-local-http:false}") boolean allowLocalHttp,
            @Value("${forge.gitlab.private-cidr-allowlist:}") String privateAllowlist) {
        this(
                allowLocalHttp,
                Arrays.stream(privateAllowlist.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList(),
                InetAddress::getAllByName);
    }

    GitLabUrlPolicy(boolean allowLocalHttp, List<String> privateAllowlist, DnsResolver resolver) {
        this.allowLocalHttp = allowLocalHttp;
        this.privateAllowlist = privateAllowlist.stream().map(IpCidr::parse).toList();
        this.resolver = resolver;
    }

    @Override
    public URI validate(String rawBaseUrl) {
        URI uri = parse(rawBaseUrl);
        validateStructure(uri);
        InetAddress[] addresses = resolve(uri.getHost());
        if (addresses.length == 0) {
            throw new UnsafeGitLabUrlException("GitLab host has no DNS address");
        }
        for (InetAddress address : addresses) {
            validateAddress(uri, address);
        }
        try {
            return new URI(
                    uri.getScheme().toLowerCase(),
                    null,
                    uri.getHost().toLowerCase(),
                    uri.getPort(),
                    normalizePath(uri.getPath()),
                    null,
                    null);
        } catch (URISyntaxException exception) {
            throw new UnsafeGitLabUrlException("GitLab Base URL is invalid");
        }
    }

    private void validateStructure(URI uri) {
        boolean localDevelopment = allowLocalHttp
                && "http".equalsIgnoreCase(uri.getScheme())
                && "localhost".equalsIgnoreCase(uri.getHost());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !localDevelopment) {
            throw new UnsafeGitLabUrlException("GitLab Base URL must use HTTPS");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new UnsafeGitLabUrlException("GitLab Base URL must not contain credentials, query, or fragment");
        }
    }

    private void validateAddress(URI uri, InetAddress address) {
        if (isMetadata(address)) {
            throw new UnsafeGitLabUrlException("Cloud metadata addresses are forbidden");
        }
        boolean localDevelopment = allowLocalHttp
                && "http".equalsIgnoreCase(uri.getScheme())
                && "localhost".equalsIgnoreCase(uri.getHost())
                && address.isLoopbackAddress();
        boolean unsafe = address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
        if (unsafe && !localDevelopment && privateAllowlist.stream().noneMatch(cidr -> cidr.contains(address))) {
            throw new UnsafeGitLabUrlException("GitLab host resolves to a blocked network");
        }
    }

    private InetAddress[] resolve(String host) {
        try {
            return resolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new UnsafeGitLabUrlException("GitLab host cannot be resolved");
        }
    }

    private static boolean isMetadata(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && Byte.toUnsignedInt(bytes[0]) == 169
                && Byte.toUnsignedInt(bytes[1]) == 254;
    }

    private static URI parse(String rawBaseUrl) {
        try {
            return new URI(rawBaseUrl.trim());
        } catch (RuntimeException | URISyntaxException exception) {
            throw new UnsafeGitLabUrlException("GitLab Base URL is invalid");
        }
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return null;
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private record IpCidr(
            /* CIDR 网络地址的原始字节。 */ byte[] network,
            /* 参与前缀匹配的网络位数。 */ int prefixBits) {

        private static IpCidr parse(String value) {
            try {
                String[] parts = value.split("/", -1);
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > network.length * 8) {
                    throw new IllegalArgumentException("Invalid prefix");
                }
                return new IpCidr(network, prefix);
            } catch (RuntimeException | UnknownHostException exception) {
                throw new IllegalStateException("Invalid GitLab private CIDR allowlist entry", exception);
            }
        }

        private boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int wholeBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int index = 0; index < wholeBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (candidate[wholeBytes] & mask) == (network[wholeBytes] & mask);
        }
    }
}
