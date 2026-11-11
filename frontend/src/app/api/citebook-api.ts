import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Course {
  code: string;
  documents: number;
}

export interface Citation {
  n: number;
  fileName: string;
  page: number;
  excerpt: string;
}

export interface Answer {
  answer: string;
  grounded: boolean;
  citations: Citation[];
}

/** The backend's limit for a question; longer ones are refused with 400. */
export const MAX_QUESTION_LENGTH = 1000;

const GENERIC_ERROR = 'The request failed. Check that the backend is running and try again.';

/** The only place that knows the backend's URLs. They are relative: the dev proxy forwards /api. */
@Injectable({ providedIn: 'root' })
export class CitebookApi {
  private readonly http = inject(HttpClient);

  listCourses(): Observable<Course[]> {
    return this.http.get<Course[]>('/api/courses');
  }

  ask(courseCode: string, question: string): Observable<Answer> {
    return this.http.post<Answer>(`/api/courses/${encodeURIComponent(courseCode)}/questions`, {
      question,
    });
  }
}

/**
 * The message to show for a failed request. The backend answers errors as a problem detail (RFC 9457) whose
 * `detail` is written for people; anything else (backend down, proxy error) gets one generic sentence.
 */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse && typeof error.error?.detail === 'string') {
    return error.error.detail;
  }
  return GENERIC_ERROR;
}
