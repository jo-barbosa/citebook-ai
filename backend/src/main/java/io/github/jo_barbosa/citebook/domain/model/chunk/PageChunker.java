package io.github.jo_barbosa.citebook.domain.model.chunk;

import java.util.ArrayList;
import java.util.List;

public class PageChunker {

	static final int MIN_PAGE_LENGTH = 20;

	private final int chunkSize;
	private final int overlap;

	public PageChunker(int chunkSize, int overlap) {
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize must be positive");
		}
		if (overlap < 0 || overlap >= chunkSize / 2) {
			throw new IllegalArgumentException("overlap must be at least 0 and less than half of chunkSize");
		}
		this.chunkSize = chunkSize;
		this.overlap = overlap;
	}

	public List<Chunk> chunk(List<Page> pages) {
		List<Chunk> chunks = new ArrayList<>();
		for (Page page : pages) {
			for (String text : split(page.text().strip())) {
				chunks.add(new Chunk(page.number(), chunks.size(), text));
			}
		}
		return chunks;
	}

	private List<String> split(String text) {
		List<String> parts = new ArrayList<>();
		if (text.length() < MIN_PAGE_LENGTH) {
			return parts;
		}
		int start = 0;
		while (true) {
			int end = start + chunkSize >= text.length() ? text.length() : cutPoint(text, start);
			String part = text.substring(start, end).strip();
			if (!part.isEmpty()) {
				parts.add(part);
			}
			if (end == text.length()) {
				return parts;
			}
			start = end - overlap;
		}
	}

	private int cutPoint(String text, int start) {
		int hardEnd = start + chunkSize;
		int minEnd = start + chunkSize / 2;
		int paragraph = text.lastIndexOf("\n\n", hardEnd - 2);
		if (paragraph >= minEnd) {
			return paragraph;
		}
		for (int i = hardEnd - 1; i >= minEnd; i--) {
			if (isSentenceEnd(text, i)) {
				return i + 1;
			}
		}
		return hardEnd;
	}

	private static boolean isSentenceEnd(String text, int i) {
		char c = text.charAt(i);
		return (c == '.' || c == '!' || c == '?') && Character.isWhitespace(text.charAt(i + 1));
	}

}
