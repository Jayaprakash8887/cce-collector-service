package org.openphc.cce.collector.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestLoggingFilter}.
 *
 * <p>Verifies that the MDC {@code requestId} is populated during the filter
 * chain and cleared after the request completes.</p>
 */
@DisplayName("RequestLoggingFilter")
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Nested
    @DisplayName("MDC Management")
    class MdcManagement {

        @Test
        @DisplayName("sets requestId in MDC during request processing")
        void setsRequestId() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, (req, res) -> {
                String requestId = MDC.get("requestId");
                assertThat(requestId).isNotNull().hasSize(8);
            });
        }

        @Test
        @DisplayName("clears MDC after request completes")
        void clearsMdcAfterRequest() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, (req, res) -> {
                // MDC is populated during chain
            });

            // MDC should be cleared after filter completes
            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("clears MDC even when filter chain throws")
        void clearsMdcOnException() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            FilterChain throwingChain = (req, res) -> {
                throw new ServletException("test error");
            };

            try {
                filter.doFilterInternal(request, response, throwingChain);
            } catch (ServletException e) {
                // expected
            }

            assertThat(MDC.get("requestId")).isNull();
        }

        @Test
        @DisplayName("generates unique requestId per request")
        void uniqueRequestIds() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();
            String[] ids = new String[2];

            filter.doFilterInternal(request, response, (req, res) ->
                    ids[0] = MDC.get("requestId"));

            filter.doFilterInternal(request, response, (req, res) ->
                    ids[1] = MDC.get("requestId"));

            assertThat(ids[0]).isNotEqualTo(ids[1]);
        }
    }
}
