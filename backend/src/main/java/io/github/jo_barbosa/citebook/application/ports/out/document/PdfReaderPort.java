package io.github.jo_barbosa.citebook.application.ports.out.document;

import java.util.List;

import io.github.jo_barbosa.citebook.domain.model.chunk.Page;

public interface PdfReaderPort {

	List<Page> readPages(String fileName, byte[] content);

}
