import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { App } from './app';

describe('App', () => {
  let fixture: ComponentFixture<App>;
  let page: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting()],
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
    request.flush({ answer: 'It tosses a coin [1].', grounded: true, citations: [] });
    await fixture.whenStable();

    expect(page.querySelector('[role="status"]')).toBeNull();
    expect(page.querySelector('.answer')?.textContent).toContain('It tosses a coin [1].');
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
    expect(page.querySelector('.answer')).toBeNull();
    expect(page.querySelector('button')!.disabled).toBe(false);
  });

  it('shows a message when the courses cannot be loaded', async () => {
    http.expectOne('/api/courses').error(new ProgressEvent('error'));
    await fixture.whenStable();

    expect(page.querySelector('[role="alert"]')?.textContent).toContain('The request failed');
    expect(page.querySelector('form')).toBeNull();
  });
});
