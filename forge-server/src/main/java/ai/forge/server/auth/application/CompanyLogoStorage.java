package ai.forge.server.auth.application;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class CompanyLogoStorage {

    /* 公司 Logo 的后端本地存储目录，可在部署时映射为持久卷。 */
    private final Path storageDirectory;

    public CompanyLogoStorage(@Value("${forge.branding.storage-directory:data/branding}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public void save(byte[] content) {
        try {
            Files.createDirectories(storageDirectory);
            Path temporary = Files.createTempFile(storageDirectory, "company-logo-", ".webp.tmp");
            try {
                Files.write(temporary, content);
                moveIntoPlace(temporary, logoPath());
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to store company logo", exception);
        }
    }

    public Resource load() {
        return new FileSystemResource(logoPath());
    }

    private Path logoPath() {
        return storageDirectory.resolve("company-logo.webp");
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
