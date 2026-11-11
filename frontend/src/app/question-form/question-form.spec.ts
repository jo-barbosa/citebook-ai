import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Course } from '../api/citebook-api';
import { AskedQuestion, QuestionForm } from './question-form';

const COURSES: Course[] = [
  { code: 'esinf', documents: 3 },
  { code: 'ods', documents: 1 },
];

describe('QuestionForm', () => {
  let fixture: ComponentFixture<QuestionForm>;
  let page: HTMLElement;
  let asked: AskedQuestion[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [QuestionForm] }).compileComponents();
    fixture = TestBed.createComponent(QuestionForm);
    fixture.componentRef.setInput('courses', COURSES);
    asked = [];
    fixture.componentInstance.asked.subscribe((question) => asked.push(question));
    await fixture.whenStable();
    page = fixture.nativeElement as HTMLElement;
  });

  async function type(text: string): Promise<void> {
    const textarea = page.querySelector('textarea')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  function submit(): void {
    page.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
  }

  it('lists the courses with their document counts', () => {
    const options = Array.from(page.querySelectorAll('option')).map((option) =>
      option.textContent?.trim(),
    );

    expect(options).toEqual(['esinf (3 documents)', 'ods (1 document)']);
  });

  it('cannot ask until a question is typed', async () => {
    const button = page.querySelector('button')!;
    expect(button.disabled).toBe(true);

    await type('   ');
    expect(button.disabled).toBe(true);

    await type('What is a skiplist?');
    expect(button.disabled).toBe(false);
  });

  it('asks the first course by default, with the question trimmed', async () => {
    await type('  What is a skiplist?  ');

    submit();

    expect(asked).toEqual([{ courseCode: 'esinf', question: 'What is a skiplist?' }]);
  });

  it('asks the course picked in the list', async () => {
    const select = page.querySelector('select')!;
    select.value = 'ods';
    select.dispatchEvent(new Event('change'));
    await type('What is a skiplist?');

    submit();

    expect(asked).toEqual([{ courseCode: 'ods', question: 'What is a skiplist?' }]);
  });

  it('does not ask while it is disabled', async () => {
    await type('What is a skiplist?');
    fixture.componentRef.setInput('disabled', true);
    await fixture.whenStable();

    submit();

    expect(page.querySelector('button')!.disabled).toBe(true);
    expect(asked).toEqual([]);
  });

  it('limits the question to 1000 characters', () => {
    expect(page.querySelector('textarea')!.getAttribute('maxlength')).toBe('1000');
  });
});
