package org.openphc.cce.collector.config;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HAPI FHIR configuration.
 *
 * <p>{@link FhirContext} is expensive to create (class scanning, reflection)
 * but fully thread-safe once constructed. It is exposed as a singleton Spring
 * bean and shared across all FHIR parsing / validation components.</p>
 */
@Configuration
public class FhirConfig {

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }
}
