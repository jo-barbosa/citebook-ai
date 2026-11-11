import { Component, inject, signal } from '@angular/core';

import { AnswerView } from './answer-view/answer-view';
import { Answer, CitebookApi, Course, errorMessage } from './api/citebook-api';
import { AskedQuestion, QuestionForm } from './question-form/question-form';

/** The page: loads the courses, sends the question, and holds what is shown (loading, error, answer). */
@Component({
  imports: [QuestionForm, AnswerView],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  private readonly api = inject(CitebookApi);

  protected readonly title = signal('citebook-ai');
  /** `null` until the list arrives, so "still loading" and "no courses" are told apart. */
  protected readonly courses = signal<Course[] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly answer = signal<Answer | null>(null);

  constructor() {
    this.api.listCourses().subscribe({
      next: (courses) => this.courses.set(courses),
      error: (error: unknown) => this.error.set(errorMessage(error)),
    });
  }

  protected ask(asked: AskedQuestion): void {
    this.loading.set(true);
    this.error.set(null);
    this.answer.set(null);
    this.api.ask(asked.courseCode, asked.question).subscribe({
      next: (answer) => {
        this.answer.set(answer);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(errorMessage(error));
        this.loading.set(false);
      },
    });
  }
}
