import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { App } from './app';
import { provideAnswerMarkdown } from './answer-view/citation-markdown';

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideAnswerMarkdown()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    page = fixture.nativeElement as HTMLElement;
  });

  afterEach(() => {
    http.verify();
  });

  /** Answers the request for the courses that the page makes when it starts. */
  async function loadCourses(courses: { code: string; documents: number }[]): Promise<void> {
    http.expectOne('/api/courses').flush(courses);
    await fixture.whenStable();
  }

  async function ask(question: string): Promise<void> {
    const textarea = page.querySelector('textarea')!;
    textarea.value = question;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
  }

  it('shows the product name and what the page is for', async () => {
    await loadCourses([]);

    expect(page.querySelector('h1')?.textContent).toBe('citebook-ai');
    expect(page.querySelector('main p')?.textContent).toContain('cite their source');
  });

  it('lists the courses in the form', async () => {
    await loadCourses([
      { code: 'esinf', documents: 3 },
      { code: 'ods', documents: 1 },
    ]);

    expect(page.querySelectorAll('option').length).toBe(2);
  });

  it('says so when there are no courses, instead of showing an empty form', async () => {
    await loadCourses([]);

    expect(page.querySelector('form')).toBeNull();
    expect(page.textContent).toContain('There are no courses yet');
  });

  it('shows a status while the question is being answered, then the answer', async () => {
    await loadCourses([{ code: 'ods', documents: 1 }]);

    await ask('How is the height chosen?');

    expect(page.querySelector('[role="status"]')?.textContent).toContain('Looking for an answer');
    expect(page.querySelector('button')!.disabled).toBe(true);

    const request = http.expectOne('/api/courses/ods/questions');
    expect(request.request.body).toEqual({ question: 'How is the height chosen?' });
    request.flush({
      answer: 'It tosses a coin [1].',
      grounded: true,
      citations: [{ n: 1, fileName: 'ods-java.pdf', page: 100, excerpt: 'Toss a coin.' }],
    });
    await fixture.whenStable();

    expect(page.querySelector('[role="status"]')).toBeNull();
    const answer = page.querySelector('app-answer-view')!;
    expect(answer.textContent).toContain('It tosses a coin');
    expect(answer.querySelector('a.citation')?.getAttribute('href')).toBe('#source-1');
    expect(answer.querySelector('#source-1')?.textContent).toContain('ods-java.pdf, page 100');
  });

  it("shows the server's message when the question fails", async () => {
    await loadCourses([{ code: 'ods', documents: 1 }]);

    await ask('How is the height chosen?');
    http
      .expectOne('/api/courses/ods/questions')
      .flush(
        { detail: 'The chat model is unavailable. Try again in a few minutes.' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain(
      'The chat model is unavailable',
    );
    expect(page.querySelector('app-answer-view')).toBeNull();
    expect(page.querySelector('button')!.disabled).toBe(false);
  });

  it('clears the previous answer when a new question starts', async () => {
    await loadCourses([{ code: 'ods', documents: 1 }]);
    await ask('How is the height chosen?');
    http
      .expectOne('/api/courses/ods/questions')
      .flush({ answer: 'It tosses a coin.', grounded: false, citations: [] });
    await fixture.whenStable();
    expect(page.querySelector('app-answer-view')).not.toBeNull();

    await ask('And the expected height?');

    expect(page.querySelector('app-answer-view')).toBeNull();
    expect(page.querySelector('[role="status"]')).not.toBeNull();
    http
      .expectOne('/api/courses/ods/questions')
      .flush({ answer: 'It is 1.', grounded: false, citations: [] });
    await fixture.whenStable();
    expect(page.querySelector('app-answer-view')?.textContent).toContain('It is 1.');
  });

  it('shows a message when the courses cannot be loaded', async () => {
    http.expectOne('/api/courses').error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('The request failed');
    expect(page.querySelector('form')).toBeNull();
  });
});
