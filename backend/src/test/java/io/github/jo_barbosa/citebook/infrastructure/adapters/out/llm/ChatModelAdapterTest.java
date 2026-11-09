package io.github.jo_barbosa.citebook.infrastructure.adapters.out.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.github.jo_barbosa.citebook.TestcontainersConfiguration;
import io.github.jo_barbosa.citebook.application.exceptions.ExternalServiceUnavailableException;
import io.github.jo_barbosa.citebook.application.ports.out.chat.ChatPort;

/**
 * Runs the real OpenAI auto-configuration (the openai-java SDK client, with the retries from application.yaml) against
 * a local server standing in for OpenRouter. The timeout is lowered to 1 s so the slow case stays fast.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChatModelAdapterTest {

	private static final String COMPLETION = """
			{"id":"c1","object":"chat.completion","created":1,"model":"test-model",
			 "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"A heap is a tree [1]."}}],
			 "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
			""";

	private static final HttpServer SERVER = startServer();

	/** One entry per request the server received: "METHOD path\nbody". */
	private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();

	private static volatile Reply reply;

	@Autowired
	private ChatPort chat;

	@DynamicPropertySource
	static void openRouterStub(DynamicPropertyRegistry registry) {
		registry.add("spring.ai.openai.base-url",
				() -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/api/v1");
		registry.add("spring.ai.openai.api-key", () -> "test-key");
		registry.add("spring.ai.openai.chat.timeout", () -> "1s");
	}

	@BeforeEach
	void reset() {
		REQUESTS.clear();
	}

	@AfterAll
	static void stopServer() {
		SERVER.stop(0);
		((ExecutorService) SERVER.getExecutor()).shutdownNow();
	}

	@Test
	void sendsBothMessagesAndReturnsTheAnswer() {
		reply = exchange -> respond(exchange, 200, COMPLETION);

		String answer = chat.complete("Answer only from the excerpts.", "What is a heap?");

		assertThat(answer).isEqualTo("A heap is a tree [1].");
		assertThat(REQUESTS).singleElement().satisfies(request -> assertThat(request)
				.startsWith("POST /api/v1/chat/completions")
				.contains("system", "Answer only from the excerpts.", "user", "What is a heap?")
				.contains("\"model\":\"z-ai/glm-5.3-flash\"", "\"max_tokens\":1024", "\"temperature\":0.2"));
	}

	@Test
	void rateLimitIsRetriedOnceThenReportedAsUnavailable() {
		reply = exchange -> respond(exchange, 429, "{\"error\":{\"message\":\"rate limited\",\"code\":429}}");

		assertThatThrownBy(() -> chat.complete("system", "question"))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasMessage("The chat model is unavailable. Try again in a few minutes.")
				.hasCauseInstanceOf(RateLimitException.class);
		assertThat(REQUESTS).hasSize(2);
	}

	@Test
	void providerErrorIsReportedAsUnavailable() {
		reply = exchange -> respond(exchange, 503, "{\"error\":{\"message\":\"no provider available\",\"code\":503}}");

		assertThatThrownBy(() -> chat.complete("system", "question"))
				.isInstanceOf(ExternalServiceUnavailableException.class);
	}

	@Test
	void slowModelIsReportedAsUnavailable() {
		reply = exchange -> {
			sleep(3_000);
			respond(exchange, 200, COMPLETION);
		};

		assertThatThrownBy(() -> chat.complete("system", "question"))
				.isInstanceOf(ExternalServiceUnavailableException.class)
				.hasCauseInstanceOf(OpenAIIoException.class);
	}

	@Test
	void rejectedKeyIsNotReportedAsUnavailable() {
		reply = exchange -> respond(exchange, 401, "{\"error\":{\"message\":\"invalid key\",\"code\":401}}");

		// A bad key or model id is a configuration error: retrying or "try again later" would not help.
		assertThatThrownBy(() -> chat.complete("system", "question"))
				.isInstanceOf(UnauthorizedException.class);
		assertThat(REQUESTS).hasSize(1);
	}

	@FunctionalInterface
	private interface Reply {

		void handle(HttpExchange exchange) throws IOException;

	}

	private static HttpServer startServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			// Several threads, so a slow reply does not hold back the retry.
			server.setExecutor(Executors.newCachedThreadPool());
			server.createContext("/", exchange -> {
				String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
				REQUESTS.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + "\n" + body);
				try {
					reply.handle(exchange);
				}
				catch (IOException e) {
					// The client gave up (timeout); nothing to answer.
				}
				finally {
					exchange.close();
				}
			});
			server.start();
			return server;
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot start the OpenRouter stub", e);
		}
	}

	private static void respond(HttpExchange exchange, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
