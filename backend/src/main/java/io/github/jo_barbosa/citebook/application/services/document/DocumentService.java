package io.github.jo_barbosa.citebook.application.services.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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
import io.github.jo_barbosa.citebook.domain.model.chunk.Page;
import io.github.jo_barbosa.citebook.domain.model.chunk.PageChunker;
import io.github.jo_barbosa.citebook.domain.model.course.Course;
import io.github.jo_barbosa.citebook.domain.model.document.Document;
import io.github.jo_barbosa.citebook.domain.model.text.InvisibleCharacterFilter;
import io.github.jo_barbosa.citebook.domain.model.text.StrippedText;

@Service
public class DocumentService implements DocumentUseCases {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private final CourseRepositoryPort courses;
	private final DocumentRepositoryPort documents;
	private final ChunkStorePort chunkStore;
	private final PdfReaderPort pdfReader;
	private final PageChunker chunker;
	private final TransactionOperations transaction;
	// Stateless domain logic with no configuration: created here, not injected.
	private final InvisibleCharacterFilter invisible = new InvisibleCharacterFilter();

	public DocumentService(CourseRepositoryPort courses, DocumentRepositoryPort documents, ChunkStorePort chunkStore,
			PdfReaderPort pdfReader, PageChunker chunker, TransactionOperations transaction) {
		this.courses = courses;
		this.documents = documents;
		this.chunkStore = chunkStore;
		this.pdfReader = pdfReader;
		this.chunker = chunker;
		this.transaction = transaction;
	}

	@Override
	public IngestDocumentView ingest(IngestDocumentCommand command) {
		String sha256 = sha256(command.content());
		Optional<Document> existing = courses.findByCode(command.courseCode())
				.flatMap(course -> documents.findByCourseIdAndFileName(course.id(), command.fileName()));
		if (existing.isPresent() && existing.get().sha256().equals(sha256)) {
			Document document = existing.get();
			return new IngestDocumentView(document.id(), document.pageCount(), document.chunkCount(),
					IngestStatus.ALREADY_INDEXED);
		}

		List<Page> pages = new ArrayList<>();
		int removed = 0;
		for (Page page : pdfReader.readPages(command.fileName(), command.content())) {
			StrippedText stripped = invisible.strip(page.text());
			pages.add(new Page(page.number(), stripped.text()));
			removed += stripped.removed();
		}
		List<Chunk> chunks = chunker.chunk(pages);
		if (chunks.isEmpty()) {
			throw new PdfWithoutTextException(command.fileName());
		}

		IngestDocumentView view = transaction.execute(status -> store(command, sha256, pages.size(), chunks, existing));
		if (removed > 0) {
			// The id, not the file name: the name comes from the client and could forge log lines.
			log.warn("Removed {} invisible characters from document {}", removed, view.documentId());
		}
		return view;
	}

	private IngestDocumentView store(IngestDocumentCommand command, String sha256, int pageCount, List<Chunk> chunks,
			Optional<Document> existing) {
		Course course = courses.findByCode(command.courseCode())
				.orElseGet(() -> courses.create(command.courseCode()));
		existing.ifPresent(old -> {
			chunkStore.deleteByDocumentId(old.id());
			documents.deleteById(old.id());
		});
		Document document = documents.create(course.id(), command.fileName(), sha256, pageCount, chunks.size());
		chunkStore.save(command.courseCode(), document.id(), command.fileName(), chunks);
		return new IngestDocumentView(document.id(), pageCount, chunks.size(),
				existing.isPresent() ? IngestStatus.REPLACED : IngestStatus.INDEXED);
	}

	private static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
