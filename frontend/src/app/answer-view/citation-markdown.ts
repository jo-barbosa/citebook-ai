import { Provider } from '@angular/core';
import { MarkedExtension, Tokens } from 'marked';
import { MARKED_EXTENSIONS, provideMarkdown } from 'ngx-markdown';

/** `[1]` or `[1, 3]`: numbers of 1 to 3 digits, as the backend's citation check accepts them. */
const CITATION = /^\[(\d{1,3}(?:\s*,\s*\d{1,3})*)\]/;

interface CitationToken extends Tokens.Generic {
  type: 'citation';
  raw: string;
  numbers: string[];
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/**
 * How an answer's Markdown is turned into HTML, on top of the standard rules:
 *
 * - a citation marker becomes a link to its source in the list below the answer (`#source-n`). It is an inline
 *   rule of the parser, so brackets inside code spans and code blocks are never touched;
 * - raw HTML is shown as text, not rendered: nothing the model writes becomes an element, and text that only
 *   looks like a tag (`List<String>`) is not lost;
 * - an image is replaced by its alt text: the browser would fetch an image as soon as it is rendered, which lets
 *   a poisoned document leak text through the image's URL.
 *
 * The result still goes through Angular's sanitizer in the `<markdown>` component.
 */
export const citationMarkdown: MarkedExtension = {
  extensions: [
    {
      name: 'citation',
      level: 'inline',
      // Where a citation may start, so the parser stops plain text there and tries the tokenizer.
      start: (source: string) => source.indexOf('['),
      tokenizer(source: string): CitationToken | undefined {
        const match = CITATION.exec(source);
        if (!match) {
          return undefined;
        }
        return {
          type: 'citation',
          raw: match[0],
          numbers: match[1].split(',').map((number) => number.trim()),
        };
      },
      renderer(token: Tokens.Generic): string {
        const links = (token as CitationToken).numbers.map(
          (number) => `<a class="citation" href="#source-${number}">${number}</a>`,
        );
        return `[${links.join(', ')}]`;
      },
    },
  ],
  renderer: {
    html: ({ text }: Tokens.HTML | Tokens.Tag) => escapeHtml(text),
    image: ({ text }: Tokens.Image) => escapeHtml(text),
  },
};

/** `ngx-markdown` with our rules. Sanitization stays at its default (Angular's `DomSanitizer`). */
export function provideAnswerMarkdown(): Provider[] {
  return provideMarkdown({
    markedExtensions: [{ provide: MARKED_EXTENSIONS, useValue: citationMarkdown, multi: true }],
  });
}
