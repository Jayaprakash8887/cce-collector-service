package org.openphc.cce.collector.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openphc.cce.collector.domain.repository.InboundEventLogRepository;
import org.openphc.cce.collector.kafka.InboundEventProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for integration tests.
 *
 * <p>Provides shared infrastructure:</p>
 * <ul>
 *   <li>Full Spring Boot context with {@code RANDOM_PORT}</li>
 *   <li>H2 in-memory database (application-test.yml)</li>
 *   <li>{@code @MockitoBean InboundEventProducer} — replaces the real Kafka
 *       producer (KafkaAutoConfiguration is excluded in test profile)</li>
 *   <li>{@code MockMvc} for controller-level HTTP testing</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected InboundEventLogRepository inboundEventRepository;

    @MockitoBean
    protected InboundEventProducer inboundEventProducer;
}
