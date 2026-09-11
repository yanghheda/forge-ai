package ai.forge.server.auth.domain;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CompanyLogoPolicyTest {

    @Test
    void acceptsSquareWebpAtAnyDimensions() {
        assertThatNoException().isThrownBy(() -> CompanyLogoPolicy.validate(webp(28, 28)));
        assertThatNoException().isThrownBy(() -> CompanyLogoPolicy.validate(webp(512, 512)));
    }

    @Test
    void rejectsNonSquareWebp() {
        assertThatThrownBy(() -> CompanyLogoPolicy.validate(webp(29, 28)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanyLogoPolicy.validate(webp(28, 27)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private byte[] webp(int width, int height) {
        byte[] content = new byte[30];
        System.arraycopy("RIFF".getBytes(), 0, content, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(), 0, content, 8, 8);
        int encodedWidth = width - 1;
        int encodedHeight = height - 1;
        content[24] = (byte) encodedWidth;
        content[25] = (byte) (encodedWidth >> 8);
        content[26] = (byte) (encodedWidth >> 16);
        content[27] = (byte) encodedHeight;
        content[28] = (byte) (encodedHeight >> 8);
        content[29] = (byte) (encodedHeight >> 16);
        return content;
    }
}
