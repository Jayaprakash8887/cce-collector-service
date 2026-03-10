package org.openphc.cce.collector;

import org.junit.jupiter.api.Test;
import org.openphc.cce.collector.kafka.InboundEventProducer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CollectorServiceApplicationTests {

    @MockitoBean
    private InboundEventProducer inboundEventProducer;

    @Test
    void contextLoads() {
        // Verifies the Spring application context can start.
        // InboundEventProducer is mocked because KafkaAutoConfiguration is
        // excluded in the test profile (no real broker / KafkaTemplate).
    }
}
