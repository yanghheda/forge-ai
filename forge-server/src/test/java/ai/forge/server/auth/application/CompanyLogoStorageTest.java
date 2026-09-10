package ai.forge.server.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompanyLogoStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesCompanyLogoInBackendDirectory() throws Exception {
        CompanyLogoStorage storage = new CompanyLogoStorage(temporaryDirectory.toString());
        byte[] logo = "RIFF0000WEBP".getBytes();

        storage.save(logo);

        assertThat(Files.readAllBytes(temporaryDirectory.resolve("company-logo.webp"))).isEqualTo(logo);
        assertThat(storage.load().exists()).isTrue();
    }
}
