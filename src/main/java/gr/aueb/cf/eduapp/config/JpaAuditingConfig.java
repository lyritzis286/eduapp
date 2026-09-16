package gr.aueb.cf.eduapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept off the {@code @SpringBootApplication} class on purpose: Spring Boot's
 * test slices (e.g. {@code @WebMvcTest}) derive their configuration from the
 * nearest {@code @SpringBootConfiguration} and process any annotation present
 * on it directly, bypassing the slice's auto-configuration filtering. With
 * {@code @EnableJpaAuditing} there, a web-layer slice (no datasource/EntityManagerFactory)
 * fails to start with "JPA metamodel must not be empty". A separate
 * {@code @Configuration} class isn't picked up by those slices, while still being
 * discovered by the full application context via component scanning.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
