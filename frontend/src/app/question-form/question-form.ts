import { Component, computed, input, output, signal } from '@angular/core';

import { Course, MAX_QUESTION_LENGTH } from '../api/citebook-api';

export interface AskedQuestion {
  courseCode: string;
  question: string;
}

/** Pick a course, type a question. Knows nothing about HTTP: it only emits what was asked. */
@Component({
  selector: 'app-question-form',
  templateUrl: './question-form.html',
  styleUrl: './question-form.css',
})
export class QuestionForm {
  readonly courses = input.required<Course[]>();
  /** True while a question is being answered: nothing can be changed or sent. */
  readonly disabled = input(false);
  readonly asked = output<AskedQuestion>();

  protected readonly maxLength = MAX_QUESTION_LENGTH;
  protected readonly pickedCode = signal('');
  protected readonly question = signal('');

  /** The course picked in the list, or the first one while nothing was picked. */
  protected readonly selectedCode = computed(
    () => this.pickedCode() || this.courses()[0]?.code || '',
  );

  protected readonly canAsk = computed(
    () => !this.disabled() && this.selectedCode() !== '' && this.question().trim() !== '',
  );

  protected submit(event: Event): void {
    event.preventDefault(); // no page reload
    if (!this.canAsk()) {
      return;
    }
    this.asked.emit({ courseCode: this.selectedCode(), question: this.question().trim() });
  }
}
