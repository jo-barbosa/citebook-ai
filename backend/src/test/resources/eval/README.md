# Evaluation corpus

`questions.json` holds the retrieval evaluation set: 25 questions in Portuguese, written in our own words (no sentences copied from the book), each with the document and the pages where the answer is.

- `expectedPages` are **physical PDF pages** (1 = the cover), the same numbering the ingestion stores. Book page numbers are 12 lower: book page 1 is PDF page 13.
- A question is a hit at rank k when any of the top k retrieved excerpts comes from `expectedDocument` and one of `expectedPages`.

## Attribution

`ods-java.pdf` is *Open Data Structures (in Java)*, Edition 0.1G, by Pat Morin, downloaded unmodified from https://opendatastructures.org/ods-java.pdf. Source: https://github.com/patmorin/ods.

It is licensed under the [Creative Commons Attribution 2.5 Canada licence](https://creativecommons.org/licenses/by/2.5/ca/) (CC BY 2.5 CA). This work contains text from opendatastructures.org.
