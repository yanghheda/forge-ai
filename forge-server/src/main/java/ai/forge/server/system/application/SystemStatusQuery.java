package ai.forge.server.system.application;

import ai.forge.server.system.domain.SystemStatus;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusQuery {

    public SystemStatus getStatus() {
        return new SystemStatus("forge-server", "UP");
    }
}
