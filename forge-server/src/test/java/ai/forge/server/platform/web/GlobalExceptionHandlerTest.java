package ai.forge.server.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.forge.server.release.domain.ReleaseVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void releaseVersionConflictReturnsStableConflictResponse() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).thenReturn("req_release_conflict");

        ResponseEntity<ApiError> response = new GlobalExceptionHandler()
                .handleReleaseVersionConflict(new ReleaseVersionConflictException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RELEASE_VERSION_CONFLICT);
        assertThat(response.getBody().requestId()).isEqualTo("req_release_conflict");
    }
}
