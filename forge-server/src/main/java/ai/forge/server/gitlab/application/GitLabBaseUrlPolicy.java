package ai.forge.server.gitlab.application;

import java.net.URI;

public interface GitLabBaseUrlPolicy {

    URI validate(String rawBaseUrl);
}
