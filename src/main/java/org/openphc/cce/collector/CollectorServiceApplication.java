package org.openphc.cce.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Beans taken from cce-common-util are wired in
 * {@link org.openphc.cce.collector.config.CommonUtilConfig}.
 */
@SpringBootApplication
public class CollectorServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorServiceApplication.class, args);
    }
}
