import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Answer } from '../api/citebook-api';
import { AnswerView } from './answer-view';
import { provideAnswerMarkdown } from './citation-markdown';

const GROUNDED: Answer = {
  answer: 'It **tosses a coin** [1]. The expected height is 1 [2].',
  grounded: true,
  citations: [
    { n: 1, fileName: 'ods-java.pdf', page: 100, excerpt: 'Toss a coin until it comes up tails.' },
    { n: 2, fileName: 'ods-java.pdf', page: 101, excerpt: 'The expected height of a node is 1.' },
  ],
};

describe('AnswerView', () => {
  let fixture: ComponentFixture<AnswerView>;
  let page: HTMLElement;

  async function show(answer: Answer): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AnswerView],
      providers: [provideAnswerMarkdown()],
    }).compileComponents();
    fixture = TestBed.createComponent(AnswerView);
    fixture.componentRef.setInput('answer', answer);
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  }

  it('renders the Markdown of the answer', async () => {
    await show(GROUNDED);

    expect(page.querySelector('.answer-text strong')?.textContent).toBe('tosses a coin');
    expect(page.querySelector('.answer-text')?.textContent).not.toContain('**');
  });

  it('links each citation marker to its source', async () => {
    await show(GROUNDED);

    const markers = Array.from(page.querySelectorAll<HTMLAnchorElement>('.answer-text a.citation'));
    expect(markers.map((marker) => marker.getAttribute('href'))).toEqual([
      '#source-1',
      '#source-2',
    ]);
    expect(page.querySelector('#source-1')?.textContent).toContain('ods-java.pdf, page 100');
    expect(page.querySelector('#source-2')?.textContent).toContain('ods-java.pdf, page 101');
  });

  it('lists every source with its number, file, page and excerpt', async () => {
    await show(GROUNDED);

    const sources = Array.from(page.querySelectorAll('.sources li'));
    expect(sources.length).toBe(2);
    expect(sources[0].textContent).toContain('[1]');
    expect(sources[0].textContent).toContain('ods-java.pdf, page 100');
    expect(sources[0].querySelector('blockquote')?.textContent).toBe(
      'Toss a coin until it comes up tails.',
    );
  });

  it('does not warn about a grounded answer', async () => {
    await show(GROUNDED);

    expect(page.querySelector('.warning')).toBeNull();
  });

  it('warns when the answer is not grounded, and shows no sources', async () => {
    await show({ answer: 'Dijkstra is greedy.', grounded: false, citations: [] });

    expect(page.querySelector('.warning')?.textContent).toContain('cites no source');
    expect(page.querySelector('.sources')).toBeNull();
    expect(page.textContent).not.toContain('Sources');
  });

  it('renders no script, raw HTML, image or script link from the answer', async () => {
    await show({
      answer:
        'Start <script>alert(1)</script> <img src="https://attacker.example/a.png" onerror="alert(2)"> ' +
        '![alt](https://attacker.example/b.png) [click me](javascript:alert(3)) end [1].',
      grounded: true,
      citations: [GROUNDED.citations[0]],
    });

    const text = page.querySelector('.answer-text')!;
    expect(text.querySelector('script')).toBeNull();
    expect(text.querySelector('img')).toBeNull();
    // Raw HTML is shown as text and the image is replaced by its alt text: nothing that loads a URL is left.
    expect(text.querySelector('[src]')).toBeNull();
    expect(text.textContent).toContain('<script>alert(1)</script>');
    const hrefs = Array.from(text.querySelectorAll('a')).map(
      (link) => link.getAttribute('href') ?? '',
    );
    expect(hrefs.some((href) => href.startsWith('javascript:'))).toBe(false);
    expect(text.textContent).toContain('Start');
    expect(text.querySelector('a.citation')?.getAttribute('href')).toBe('#source-1');
  });

  it('shows an excerpt as text, never as HTML', async () => {
    await show({
      answer: 'See [1].',
      grounded: true,
      citations: [{ n: 1, fileName: 'notes.pdf', page: 2, excerpt: '<b>bold</b> **stars**' }],
    });

    const excerpt = page.querySelector('#source-1 blockquote')!;
    expect(excerpt.querySelector('b')).toBeNull();
    expect(excerpt.textContent).toBe('<b>bold</b> **stars**');
  });
});
