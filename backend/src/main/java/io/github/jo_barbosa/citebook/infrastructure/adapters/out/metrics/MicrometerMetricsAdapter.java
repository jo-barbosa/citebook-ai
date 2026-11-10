package io.github.jo_barbosa.citebook.infrastructure.adapters.out.metrics;

import org.springframework.stereotype.Component;

import io.github.jo_barbosa.citebook.application.ports.out.metrics.MetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts the application's events as Micrometer counters, exposed at {@code /actuator/prometheus} as
 * {@code citebook_citations_rejected_total} and {@code citebook_questions_no_material_total}.
 *
 * <p>The counters are registered here, at startup, so they are exposed with value 0 before the first event.
 */
@Component
public class MicrometerMetricsAdapter implements MetricsPort {

	private final Counter rejectedCitations;
	private final Counter questionsWithoutMaterial;

	public MicrometerMetricsAdapter(MeterRegistry registry) {
		this.rejectedCitations = Counter.builder("citebook.citations.rejected")
				.description("Citations removed from answers because they matched no excerpt sent to the model")
				.register(registry);
		this.questionsWithoutMaterial = Counter.builder("citebook.questions.no.material")
				.description("Questions for which the course had no relevant material, answered without the model")
				.register(registry);
	}

	@Override
	public void citationsRejected(int count) {
		rejectedCitations.increment(count);
	}

	@Override
	public void questionWithoutMaterial() {
		questionsWithoutMaterial.increment();
	}

}
