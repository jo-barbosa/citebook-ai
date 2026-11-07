package io.github.jo_barbosa.citebook;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public final class FakeEmbeddingModel implements EmbeddingModel {

	public static final int DIMENSIONS = 1024;

	private final boolean unavailable;
	private final String brokenMarker;

	private FakeEmbeddingModel(boolean unavailable, String brokenMarker) {
		this.unavailable = unavailable;
		this.brokenMarker = brokenMarker;
	}

	public static FakeEmbeddingModel working() {
		return new FakeEmbeddingModel(false, null);
	}

	public static FakeEmbeddingModel unavailable() {
		return new FakeEmbeddingModel(true, null);
	}

	public static FakeEmbeddingModel brokenVectorFor(String marker) {
		return new FakeEmbeddingModel(false, marker);
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		if (unavailable) {
			throw new IllegalStateException("embedding service unavailable");
		}
		List<String> texts = request.getInstructions();
		List<Embedding> embeddings = new ArrayList<>();
		for (int i = 0; i < texts.size(); i++) {
			embeddings.add(new Embedding(vector(texts.get(i)), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(Document document) {
		return vector(document.getText());
	}

	@Override
	public int dimensions() {
		return DIMENSIONS;
	}

	private float[] vector(String text) {
		if (brokenMarker != null && text.contains(brokenMarker)) {
			return new float[3];
		}
		float[] vector = new float[DIMENSIONS];
		for (String word : text.toLowerCase().split("[^a-z]+")) {
			if (!word.isEmpty()) {
				vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
			}
		}
		return vector;
	}

}
