package io.github.jo_barbosa.citebook.infrastructure.adapters.out.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.jo_barbosa.citebook.application.ports.out.metrics.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerMetricsAdapterTest {

	private final MeterRegistry registry = new SimpleMeterRegistry();

	private final MetricsPort metrics = new MicrometerMetricsAdapter(registry);

	@Test
	void registersBothCountersAtZeroSoTheyAreExposedBeforeTheFirstEvent() {
		assertThat(count("citebook.citations.rejected")).isZero();
		assertThat(count("citebook.questions.no.material")).isZero();
	}

	@Test
	void addsUpTheRejectedCitationsOfEachAnswer() {
		metrics.citationsRejected(2);
		metrics.citationsRejected(0);
		metrics.citationsRejected(1);

		assertThat(count("citebook.citations.rejected")).isEqualTo(3.0);
	}

	@Test
	void countsEachQuestionWithoutMaterial() {
		metrics.questionWithoutMaterial();
		metrics.questionWithoutMaterial();

		assertThat(count("citebook.questions.no.material")).isEqualTo(2.0);
	}

	/** {@code get(...).counter()} throws if the counter is not registered. */
	private double count(String name) {
		return registry.get(name).counter().count();
	}

}
