package com.personal.chama;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Full application against a real Postgres. Every test class using this shares one
 * Spring context and one database, so tests must not depend on the data being empty.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
		"chama.admin.username=" + IntegrationTest.RECORDER,
		"chama.admin.password=" + IntegrationTest.RECORDER_PASSWORD })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {

	String RECORDER = "test-recorder";
	String RECORDER_PASSWORD = "test-password-123";
}
