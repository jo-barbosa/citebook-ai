import { Component, input } from '@angular/core';
import { MarkdownComponent } from 'ngx-markdown';

import { Answer } from '../api/citebook-api';

/**
 * One answer: the warning when nothing backs it, the text (Markdown, with each [n] linking to its source) and the
 * sources. The text is model output, so it is only rendered through the sanitizing `<markdown>` component; the
 * sources are plain interpolation.
 */
@Component({
  selector: 'app-answer-view',
  imports: [MarkdownComponent],
  templateUrl: './answer-view.html',
  styleUrl: './answer-view.css',
})
export class AnswerView {
  readonly answer = input.required<Answer>();
}
