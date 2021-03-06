package org.fiware.mintaka;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.extern.slf4j.Slf4j;
import org.fiware.mintaka.persistence.EntityRepository;
import org.fiware.mintaka.persistence.LimitableResult;
import org.fiware.mintaka.persistence.TimescaleBackedEntityRepository;
import org.fiware.mintaka.service.EntityTemporalService;
import org.fiware.ngsi.api.TemporalRetrievalApiTestClient;
import org.fiware.ngsi.model.EntityTemporalVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.inject.Inject;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
@MicronautTest(environments = "exception-test")
public class ExceptionTest {

	@Inject
	@Client("/")
	RxHttpClient mintakaTestClient;

	@MockBean(EntityTemporalService.class)
	EntityTemporalService entityTemporalService() {
		return mock(EntityTemporalService.class);
	}

	@Inject
	private EntityTemporalService entityTemporalService;

	@DisplayName("Test request with an invalid context")
	@ParameterizedTest
	@ValueSource(strings = {"https://no-context.org"})
	public void testInvalidContextRequest(String invalidContext) {
		when(entityTemporalService.getEntitiesWithQuery(any(), any(), anyList(), anyList(), any(), any(), any(), anyInt(), anyBoolean(), anyBoolean(), anyInt(), any()))
				.thenReturn(new LimitableResult<List<EntityTemporalVO>>(List.of(new EntityTemporalVO().id(URI.create("my:entity"))), false));
		MutableHttpRequest getRequest = HttpRequest.GET("/temporal/entities/");
		getRequest.getHeaders()
				.add("Link", String.format("<%s>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json", invalidContext));
		try {
			mintakaTestClient.toBlocking().retrieve(getRequest);
			fail("Retrieval with invalid context should not be possible.");
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getResponse().getStatus(), "If context cannot be retrieved, a 503 should be returned.");
		}
	}

	@DisplayName("Test request with an invalid context uri")
	@ParameterizedTest
	@ValueSource(strings = {"invalidURI", "", "ht://some-url.com"})
	public void testInvalidContextURIRequest(String invalidContext) {
		MutableHttpRequest getRequest = HttpRequest.GET("/temporal/entities/");
		getRequest.getHeaders()
				.add("Link", String.format("<%s>; rel=\"http://www.w3.org/ns/json-ld#context\"; type=\"application/ld+json", invalidContext));
		try {
			mintakaTestClient.toBlocking().retrieve(getRequest);
			fail("Retrieval with invalid context should not be possible.");
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.BAD_REQUEST, e.getResponse().getStatus(), "If context uri is illeagel, 400 should be returned.");
		}
	}

	@Test
	public void testInternalErrorOnDBProblem() {

		when(entityTemporalService.getEntitiesWithQuery(any(), any(), anyList(), anyList(), any(), any(), any(), anyInt(), anyBoolean(), anyBoolean(), anyInt(), any()))
				.thenThrow(new RuntimeException());

		MutableHttpRequest getRequest = HttpRequest.GET("/temporal/entities/");
		try {
			mintakaTestClient.toBlocking().retrieve(getRequest);
			fail("In case of db errors, the retrieval should respond an error.");
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus(), "A 500 should have been returned.");
		}
	}

}
