package fi.digitraffic.tis.vaco.badges;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class BadgeControllerTests {

    @Mock
    private HttpServletResponse response;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(response);
    }

    @Test
    void statusResourceIsNotAvailable_resultIsNotPresent() {
        Optional<ClassPathResource> statusOpt = Optional.empty();
        Optional<ClassPathResource> result = BadgeController.getResource(response, statusOpt);
        assertThat(result.isPresent(), equalTo(false));
    }

    @Test
    void statusResourceIsAvailable_resultIsPresent() {
        ClassPathResource cpr = new ClassPathResource("file.txt");
        Optional<ClassPathResource> statusOpt = Optional.of(cpr);

        Optional<ClassPathResource> result = BadgeController.getResource(response, statusOpt);

        assertThat(result.isPresent(), equalTo(true));
        verify(response, times(1)).addHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"file.txt\"");
    }
}
