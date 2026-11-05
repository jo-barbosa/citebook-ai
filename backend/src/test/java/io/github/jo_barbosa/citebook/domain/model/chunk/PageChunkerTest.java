package io.github.jo_barbosa.citebook.domain.model.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PageChunkerTest {

	private final PageChunker chunker = new PageChunker(100, 20);

	@Test
	void shortPageIsOneChunk() {
		String text = "A page that fits in one chunk.";

		List<Chunk> chunks = chunker.chunk(List.of(new Page(7, text)));

		assertThat(chunks).containsExactly(new Chunk(7, 0, text));
	}

	@Test
	void pagesUnderTwentyCharactersAreDropped() {
		List<Chunk> chunks = chunker.chunk(List.of(
				new Page(1, "a".repeat(19)),
				new Page(2, "  " + "b".repeat(19) + "  "),
				new Page(3, "c".repeat(20))));

		assertThat(chunks).containsExactly(new Chunk(3, 0, "c".repeat(20)));
	}

	@Test
	void noPagesGiveNoChunks() {
		assertThat(chunker.chunk(List.of())).isEmpty();
	}

	@Test
	void longPageWithoutBoundariesIsCutAtChunkSizeWithOverlap() {
		String text = letters(250);

		List<Chunk> chunks = chunker.chunk(List.of(new Page(1, text)));

		assertThat(chunks).containsExactly(
				new Chunk(1, 0, text.substring(0, 100)),
				new Chunk(1, 1, text.substring(80, 180)),
				new Chunk(1, 2, text.substring(160, 250)));
	}

	@Test
	void chunkIndexRunsAcrossPagesAndPageNumbersAreKept() {
		List<Chunk> chunks = chunker.chunk(List.of(
				new Page(3, "A short page of text."),
				new Page(5, letters(250))));

		assertThat(chunks).extracting(Chunk::page).containsExactly(3, 5, 5, 5);
		assertThat(chunks).extracting(Chunk::index).containsExactly(0, 1, 2, 3);
	}

	@Test
	void blankChunksAreSkipped() {
		String text = "start of page" + " ".repeat(300) + "end of page";

		List<Chunk> chunks = chunker.chunk(List.of(new Page(1, text)));

		assertThat(chunks).containsExactly(
				new Chunk(1, 0, "start of page"),
				new Chunk(1, 1, "end of page"));
	}

	@Test
	void rejectsInvalidConfiguration() {
		assertThatThrownBy(() -> new PageChunker(0, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PageChunker(100, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new PageChunker(100, 50)).isInstanceOf(IllegalArgumentException.class);
		assertThatCode(() -> new PageChunker(100, 49)).doesNotThrowAnyException();
	}

	@Test
	void prefersAParagraphBreakOverALaterSentenceEnd() {
		String first = "a".repeat(60);
		String text = first + "\n\n" + "bbbb ccc. " + "d".repeat(100);

		List<Chunk> chunks = chunker.chunk(List.of(new Page(1, text)));

		assertThat(chunks.getFirst()).isEqualTo(new Chunk(1, 0, first));
	}

	@Test
	void cutsAtTheLastSentenceEndInTheSecondHalfOfTheWindow() {
		String first = "Alpha beta gamma delta epsilon zeta eta theta iota kappa.";
		String text = first + " Lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega.";

		List<Chunk> chunks = chunker.chunk(List.of(new Page(1, text)));

		assertThat(chunks.getFirst()).isEqualTo(new Chunk(1, 0, first));
	}

	@Test
	void ignoresBoundariesInTheFirstHalfOfTheWindow() {
		String text = "Short one. " + "x".repeat(200);

		List<Chunk> chunks = chunker.chunk(List.of(new Page(1, text)));

		assertThat(chunks.getFirst()).isEqualTo(new Chunk(1, 0, text.substring(0, 100)));
	}

	private static String letters(int length) {
		return IntStream.range(0, length)
				.mapToObj(i -> String.valueOf((char) ('a' + i % 26)))
				.collect(Collectors.joining());
	}

}
