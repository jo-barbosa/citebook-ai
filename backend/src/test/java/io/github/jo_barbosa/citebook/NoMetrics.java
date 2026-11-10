package io.github.jo_barbosa.citebook;

import io.github.jo_barbosa.citebook.application.ports.out.metrics.MetricsPort;

/** For tests that build a {@code QuestionService} but do not look at metrics. */
public class NoMetrics implements MetricsPort {

	@Override
	public void citationsRejected(int count) {
	}

	@Override
	public void questionWithoutMaterial() {
	}

}
