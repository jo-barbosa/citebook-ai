package io.github.jo_barbosa.citebook.application.ports.out.metrics;

/** The events the application counts. How they are stored and exposed is the adapter's business. */
public interface MetricsPort {

	/** @param count cited numbers in one answer that matched no excerpt sent to the model; may be 0 */
	void citationsRejected(int count);

	/** A question for which the search found no excerpt, so the model was not called. */
	void questionWithoutMaterial();

}
