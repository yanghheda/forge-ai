package ai.forge.server.auth.domain;

public final class CompanyLogoPolicy {

    /* 公司 Logo 文件内容允许的最大字节数，为 2 MiB。 */
    private static final int MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024;

    private CompanyLogoPolicy() {}

    public static void validate(byte[] content) {
        if (content.length == 0 || content.length > MAX_FILE_SIZE_BYTES || !hasWebpSignature(content)) {
            throw new IllegalArgumentException("Invalid WebP logo");
        }
        int[] dimensions = dimensions(content);
        if (dimensions[0] != dimensions[1]) {
            throw new IllegalArgumentException("Company logo must have a 1:1 aspect ratio");
        }
    }

    private static boolean hasWebpSignature(byte[] content) {
        return content.length >= 30
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    private static int[] dimensions(byte[] content) {
        String chunk = new String(content, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (chunk) {
            case "VP8X" -> new int[] {1 + littleEndian24(content, 24), 1 + littleEndian24(content, 27)};
            case "VP8L" -> losslessDimensions(content);
            case "VP8 " -> lossyDimensions(content);
            default -> throw new IllegalArgumentException("Unsupported WebP logo encoding");
        };
    }

    private static int[] losslessDimensions(byte[] content) {
        if (content.length < 25 || unsigned(content[20]) != 0x2f) {
            throw new IllegalArgumentException("Invalid lossless WebP logo");
        }
        int width = 1 + unsigned(content[21]) + ((unsigned(content[22]) & 0x3f) << 8);
        int height = 1 + (unsigned(content[22]) >> 6) + (unsigned(content[23]) << 2)
                + ((unsigned(content[24]) & 0x0f) << 10);
        return new int[] {width, height};
    }

    private static int[] lossyDimensions(byte[] content) {
        if (content.length < 30 || unsigned(content[23]) != 0x9d
                || unsigned(content[24]) != 0x01 || unsigned(content[25]) != 0x2a) {
            throw new IllegalArgumentException("Invalid lossy WebP logo");
        }
        int width = (unsigned(content[26]) | (unsigned(content[27]) << 8)) & 0x3fff;
        int height = (unsigned(content[28]) | (unsigned(content[29]) << 8)) & 0x3fff;
        return new int[] {width, height};
    }

    private static int littleEndian24(byte[] content, int offset) {
        return unsigned(content[offset]) | (unsigned(content[offset + 1]) << 8) | (unsigned(content[offset + 2]) << 16);
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
