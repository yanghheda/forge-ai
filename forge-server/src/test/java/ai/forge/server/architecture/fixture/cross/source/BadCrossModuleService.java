package ai.forge.server.architecture.fixture.cross.source;

import ai.forge.server.architecture.fixture.cross.target.repository.TargetRepository;

public class BadCrossModuleService {

    private final TargetRepository repository = new TargetRepository();

    public String readOtherModule() {
        return repository.read();
    }
}
