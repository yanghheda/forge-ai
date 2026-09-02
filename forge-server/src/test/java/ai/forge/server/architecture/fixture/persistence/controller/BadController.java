package ai.forge.server.architecture.fixture.persistence.controller;

import ai.forge.server.architecture.fixture.persistence.repository.BadRepository;

public class BadController {

    private final BadRepository repository = new BadRepository();

    public String readDirectly() {
        return repository.read();
    }
}
