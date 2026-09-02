package ai.forge.server.architecture.fixture.swagger;

import io.swagger.v3.oas.annotations.Operation;

public class BadAnnotatedService {

    @Operation(summary = "不应出现在服务类")
    public void execute() {}
}
