import { Marked } from 'marked';

import { citationMarkdown } from './citation-markdown';

/** The rules on their own: Markdown in, HTML out, with no Angular and no sanitizer. */
function render(markdown: string): string {
  return new Marked(citationMarkdown).parse(markdown, { async: false });
}

describe('citationMarkdown', () => {
  it('turns a citation into a link to its source', () => {
    expect(render('It tosses a coin [1].')).toContain(
      'It tosses a coin [<a class="citation" href="#source-1">1</a>].',
    );
  });

  it('links each number of a grouped citation', () => {
    expect(render('Both say so [1, 3].')).toContain(
      '[<a class="citation" href="#source-1">1</a>, <a class="citation" href="#source-3">3</a>]',
    );
  });

  it('keeps the Markdown around a citation', () => {
    const html = render('**Height** is random [2].\n\n- first [1]\n- second');

    expect(html).toContain('<strong>Height</strong>');
    expect(html).toContain('<li>first [<a class="citation" href="#source-1">1</a>]</li>');
  });

  it('leaves brackets in code alone', () => {
    const html = render('Use `a[0]` here.\n\n```\nx[1] = 2\n```');

    expect(html).toContain('<code>a[0]</code>');
    expect(html).toContain('x[1] = 2');
    expect(html).not.toContain('class="citation"');
  });

  it('does not treat numbers with more than 3 digits as citations', () => {
    const html = render('In [2024] and [12345].');

    expect(html).toContain('In [2024] and [12345].');
    expect(html).not.toContain('class="citation"');
  });

  it('shows raw HTML as text instead of rendering it', () => {
    const html = render(
      'Before <b>bold</b> <img src="https://attacker.example/x.png"> after\n\n<div>block</div>',
    );

    expect(html).not.toContain('<b>');
    expect(html).not.toContain('<img');
    expect(html).not.toContain('<div>');
    expect(html).toContain('Before &lt;b&gt;bold&lt;/b&gt;');
    expect(html).toContain('&lt;img src=');
    expect(html).toContain('&lt;div&gt;block&lt;/div&gt;');
  });

  it('keeps text that only looks like a tag, such as Java generics', () => {
    expect(render('A List<String> grows, and a<b>c.')).toContain(
      'A List&lt;String&gt; grows, and a&lt;b&gt;c.',
    );
  });

  it('replaces an image by its alt text, as text', () => {
    const html = render('See ![the <b>diagram</b>](https://attacker.example/x.png?q=secret).');

    expect(html).not.toContain('<img');
    expect(html).not.toContain('attacker.example');
    expect(html).not.toContain('<b>');
    expect(html).toContain('the &lt;b&gt;diagram&lt;/b&gt;');
  });
});
