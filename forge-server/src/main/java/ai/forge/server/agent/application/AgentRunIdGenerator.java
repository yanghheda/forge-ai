package ai.forge.server.agent.application;

import java.security.SecureRandom;
import java.time.Instant;

final class AgentRunIdGenerator {

    /* ULID 使用的 Crockford Base32 字母表。 */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /* 生成 ULID 随机部分的密码学安全随机源。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private AgentRunIdGenerator() {
    }

    static String next() {
        char[] value = new char[26];
        long timestamp = Instant.now().toEpochMilli();
        for (int index = 9; index >= 0; index--) {
            value[index] = ALPHABET[(int) (timestamp & 31)];
            timestamp >>>= 5;
        }
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        int buffer = 0;
        int bits = 0;
        int byteIndex = 0;
        for (int index = 10; index < value.length; index++) {
            while (bits < 5) {
                buffer = (buffer << 8) | (random[byteIndex++] & 0xff);
                bits += 8;
            }
            bits -= 5;
            value[index] = ALPHABET[(buffer >>> bits) & 31];
        }
        return new String(value);
    }
}
