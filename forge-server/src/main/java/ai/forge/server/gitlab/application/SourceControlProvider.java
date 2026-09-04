package ai.forge.server.gitlab.application;

public interface SourceControlProvider {

    ConnectionTestResult test(String baseUrl, String token);

    RepositoryDto getRepository(String baseUrl, String token, String remoteProjectId);
}
