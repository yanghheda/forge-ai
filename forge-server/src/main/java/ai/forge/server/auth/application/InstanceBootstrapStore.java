package ai.forge.server.auth.application;

import ai.forge.server.auth.domain.BootstrapCommand;
import ai.forge.server.auth.domain.BootstrapResult;

public interface InstanceBootstrapStore {

    boolean isInitialized();

    BootstrapResult create(BootstrapCommand command, String normalizedEmail, String passwordHash);
}
