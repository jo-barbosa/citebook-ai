package io.github.jo_barbosa.citebook.application.services.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionOperations;

import io.github.jo_barbosa.citebook.application.ports.in.document.DocumentUseCases;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentCommand;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestDocumentView;
import io.github.jo_barbosa.citebook.application.ports.in.document.IngestStatus;
import io.github.jo_barbosa.citebook.application.ports.out.chunk.ChunkStorePort;
import io.github.jo_barbosa.citebook.application.ports.out.course.CourseRepositoryPort;
import io.github.jo_barbosa.citebook.application.ports.out.document.DocumentRepositoryPort;
import io.github.jo_barbosa.citebook.application.ports.out.document.PdfReaderPort;
import io.github.jo_barbosa.citebook.domain.exceptions.PdfWithoutTextException;
import io.github.jo_barbosa.citebook.domain.model.chunk.Chunk;
import io.github.jo_barbosa.citebook.domain.model.chunk.ChunkMatch;
import io.github.jo_barbosa.citebook.domain.model.chunk.Page;
import io.github.jo_barbosa.citebook.domain.model.chunk.PageChunker;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.course.CourseSummary;
import io.github.jo_barbosa.citebook.domain.model.document.Document;

@ExtendWith(OutputCaptureExtension.class)
class DocumentServiceTest {

	private static final List<Page> TWO_PAGES = List.of(
			new Page(1, "First page of the course notes."),
			new Page(2, "Second page of the course notes."));

	private final InMemoryCourses courses = new InMemoryCourses();
	private final InMemoryDocuments documents = new InMemoryDocuments();
	private final RecordingChunkStore chunkStore = new RecordingChunkStore();
	private final StubPdfReader pdfReader = new StubPdfReader();

	private final DocumentUseCases service = new DocumentService(courses, documents, chunkStore, pdfReader,
			new PageChunker(100, 20), TransactionOperations.withoutTransaction());

	@Test
	void indexesANewDocumentAndCreatesTheCourse() {
		pdfReader.pages = TWO_PAGES;

		IngestDocumentView view = ingest("v1");

		assertThat(view.status()).isEqualTo(IngestStatus.INDEXED);
		assertThat(view.pages()).isEqualTo(2);
		assertThat(view.chunks()).isEqualTo(2);
		assertThat(courses.byCode).containsKey("esinf");
		assertThat(documents.stored).singleElement().satisfies(document -> {
			assertThat(document.id()).isEqualTo(view.documentId());
			assertThat(document.fileName()).isEqualTo("notes.pdf");
			assertThat(document.pageCount()).isEqualTo(2);
			assertThat(document.chunkCount()).isEqualTo(2);
			assertThat(document.sha256()).hasSize(64);
		});
		assertThat(chunkStore.saved).singleElement().satisfies(saved -> {
			assertThat(saved.courseCode()).isEqualTo("esinf");
			assertThat(saved.documentId()).isEqualTo(view.documentId());
			assertThat(saved.fileName()).isEqualTo("notes.pdf");
			assertThat(saved.chunks()).hasSize(2);
		});
	}

	@Test
	void reusesAnExistingCourse() {
		Course existing = courses.create("esinf");
		pdfReader.pages = TWO_PAGES;

		ingest("v1");

		assertThat(courses.byCode).hasSize(1);
		assertThat(documents.stored.getFirst().courseId()).isEqualTo(existing.id());
	}

	@Test
	void removesInvisibleCharactersFromTheFileName() {
		pdfReader.pages = TWO_PAGES;
		String dirty = "notes" + Character.toString(0xE0041) + "\u200B.pdf";

		service.ingest(new IngestDocumentCommand("esinf", dirty, "v1".getBytes(StandardCharsets.UTF_8)));

		assertThat(documents.stored).singleElement()
				.satisfies(document -> assertThat(document.fileName()).isEqualTo("notes.pdf"));
		assertThat(chunkStore.saved).singleElement()
				.satisfies(saved -> assertThat(saved.fileName()).isEqualTo("notes.pdf"));
	}

	@Test
	void removesInvisibleCharactersBeforeChunking() {
		pdfReader.pages = List.of(new Page(1, "A heap\u200B is a complete binary tree.\u202E"));

		ingest("v1");

		assertThat(chunkStore.saved).singleElement()
				.satisfies(saved -> assertThat(saved.chunks()).extracting(Chunk::text)
						.containsExactly("A heap is a complete binary tree."));
	}

	@Test
	void aPdfWithOnlyInvisibleTextHasNoText() {
		pdfReader.pages = List.of(new Page(1, "\u200B".repeat(25)));

		assertThatThrownBy(() -> ingest("v1")).isInstanceOf(PdfWithoutTextException.class);
		assertThat(chunkStore.saved).isEmpty();
	}

	@Test
	void logsHowManyInvisibleCharactersWereRemoved(CapturedOutput output) {
		pdfReader.pages = List.of(new Page(1, "A heap\u200B is\u200B a complete binary tree."));

		IngestDocumentView view = ingest("v1");

		assertThat(output).contains("Removed 2 invisible characters from document " + view.documentId());
	}

	@Test
	void sameContentIsAlreadyIndexed() {
		pdfReader.pages = TWO_PAGES;
		IngestDocumentView first = ingest("v1");

		IngestDocumentView second = ingest("v1");

		assertThat(second.status()).isEqualTo(IngestStatus.ALREADY_INDEXED);
		assertThat(second.documentId()).isEqualTo(first.documentId());
		assertThat(documents.stored).hasSize(1);
		assertThat(chunkStore.saved).hasSize(1);
	}

	@Test
	void aNewVersionReplacesTheOldOne() {
		pdfReader.pages = TWO_PAGES;
		IngestDocumentView first = ingest("v1");
		pdfReader.pages = List.of(new Page(1, "A completely rewritten first page."));

		IngestDocumentView second = ingest("v2");

		assertThat(second.status()).isEqualTo(IngestStatus.REPLACED);
		assertThat(second.documentId()).isNotEqualTo(first.documentId());
		assertThat(second.chunks()).isEqualTo(1);
		assertThat(chunkStore.deleted).containsExactly(first.documentId());
		assertThat(documents.stored).singleElement().extracting(Document::id).isEqualTo(second.documentId());
		assertThat(chunkStore.saved).hasSize(2);
	}

	@Test
	void aPdfWithoutTextStoresNothing() {
		pdfReader.failure = new PdfWithoutTextException("notes.pdf");

		assertThatThrownBy(() -> ingest("v1")).isInstanceOf(PdfWithoutTextException.class);

		assertNothingStored();
	}

	@Test
	void pagesTooShortToChunkCountAsNoText() {
		pdfReader.pages = List.of(new Page(1, "too short"));

		assertThatThrownBy(() -> ingest("v1"))
				.isInstanceOf(PdfWithoutTextException.class)
				.hasMessage("The PDF 'notes.pdf' has no extractable text. It may be a scanned document.");

		assertNothingStored();
	}

	private IngestDocumentView ingest(String content) {
		return service.ingest(new IngestDocumentCommand("esinf", "notes.pdf", content.getBytes(StandardCharsets.UTF_8)));
	}

	private void assertNothingStored() {
		assertThat(courses.byCode).isEmpty();
		assertThat(documents.stored).isEmpty();
		assertThat(chunkStore.saved).isEmpty();
	}

	private static class InMemoryCourses implements CourseRepositoryPort {

		final Map<String, Course> byCode = new HashMap<>();

		@Override
		public Optional<Course> findByCode(String code) {
			return Optional.ofNullable(byCode.get(code));
		}

		@Override
		public Course create(String code) {
			Course course = new Course(UUID.randomUUID(), code, Instant.now());
			byCode.put(code, course);
			return course;
		}

		@Override
		public List<String> findAllCodes() {
			return byCode.keySet().stream().sorted().toList();
		}

		@Override
		public List<CourseSummary> findAllSummaries() {
			throw new UnsupportedOperationException("ingest never lists courses");
		}

	}

	private static class InMemoryDocuments implements DocumentRepositoryPort {

		final List<Document> stored = new ArrayList<>();

		@Override
		public Optional<Document> findByCourseIdAndFileName(UUID courseId, String fileName) {
			return stored.stream()
					.filter(document -> document.courseId().equals(courseId) && document.fileName().equals(fileName))
					.findFirst();
		}

		@Override
		public Document create(UUID courseId, String fileName, String sha256, int pageCount, int chunkCount) {
			Document document = new Document(UUID.randomUUID(), courseId, fileName, sha256, pageCount, chunkCount,
					Instant.now());
			stored.add(document);
			return document;
		}

		@Override
		public void deleteById(UUID id) {
			stored.removeIf(document -> document.id().equals(id));
		}

	}

	private static class RecordingChunkStore implements ChunkStorePort {

		record Saved(String courseCode, UUID documentId, String fileName, List<Chunk> chunks) {
		}

		final List<Saved> saved = new ArrayList<>();
		final List<UUID> deleted = new ArrayList<>();

		@Override
		public void save(String courseCode, UUID documentId, String fileName, List<Chunk> chunks) {
			saved.add(new Saved(courseCode, documentId, fileName, chunks));
		}

		@Override
		public void deleteByDocumentId(UUID documentId) {
			deleted.add(documentId);
		}

		@Override
		public List<ChunkMatch> search(String courseCode, String query, int k, double minScore) {
			return List.of();
		}

	}

	private static class StubPdfReader implements PdfReaderPort {

		List<Page> pages = List.of();
		RuntimeException failure;

		@Override
		public List<Page> readPages(String fileName, byte[] content) {
			if (failure != null) {
				throw failure;
			}
			return pages;
		}

	}

}
