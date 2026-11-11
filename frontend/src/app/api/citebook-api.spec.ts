import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Answer, CitebookApi, Course, errorMessage } from './citebook-api';

describe('CitebookApi', () => {
  let api: CitebookApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CitebookApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('gets the courses', () => {
    const courses: Course[] = [
      { code: 'esinf', documents: 3 },
      { code: 'ods', documents: 1 },
    ];
    let received: Course[] | undefined;

    api.listCourses().subscribe((value) => (received = value));

    const request = http.expectOne('/api/courses');
    expect(request.request.method).toBe('GET');
    request.flush(courses);
    expect(received).toEqual(courses);
  });

  it('posts the question to the course and returns the answer', () => {
    const answer: Answer = {
      answer: 'It tosses a coin [1].',
      grounded: true,
      citations: [{ n: 1, fileName: 'ods-java.pdf', page: 100, excerpt: 'Toss a coin…' }],
    };
    let received: Answer | undefined;

    api.ask('ods', 'How is the height chosen?').subscribe((value) => (received = value));

    const request = http.expectOne('/api/courses/ods/questions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ question: 'How is the height chosen?' });
    request.flush(answer);
    expect(received).toEqual(answer);
  });
});

describe('errorMessage', () => {
  it("uses the server's detail when the error is a problem detail", () => {
    const error = new HttpErrorResponse({
      status: 503,
      error: {
        title: 'Service Unavailable',
        detail: 'The chat model is unavailable. Try again in a few minutes.',
      },
    });

    expect(errorMessage(error)).toBe('The chat model is unavailable. Try again in a few minutes.');
  });

  it('falls back to a generic sentence when there is no detail', () => {
    const noBody = new HttpErrorResponse({ status: 0, error: null });

    expect(errorMessage(noBody)).toBe(
      'The request failed. Check that the backend is running and try again.',
    );
    expect(errorMessage(new Error('boom'))).toBe(
      'The request failed. Check that the backend is running and try again.',
    );
  });
});
