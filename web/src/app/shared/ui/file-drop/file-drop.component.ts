// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input, output, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

let nextId = 0;

/**
 * Zona grande para subir ficheiros (maqueta 53, paso «Fotos»): icona, o que se pode soltar, o
 * botón (un `input type="file"` nativo con `label`: teclado e lector como sempre) e a axuda.
 * Arrastrar é un atallo, nunca a única forma (WCAG 2.5.7). Avisa cos ficheiros escollidos ou
 * soltados; quen o usa comproba tipo e tamaño.
 */
@Component({
  selector: 'furar-file-drop',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-file-drop',
    '[attr.data-over]': 'over() ? "" : null',
    '[attr.data-disabled]': 'disabled() ? "" : null',
  },
  template: `
    <div class="f-file-drop__zone" (dragover)="dragOver($event)" (dragleave)="over.set(false)" (drop)="drop($event)">
      <furar-icon class="f-file-drop__icon" name="image" [size]="24" />
      <p class="f-file-drop__title">{{ title() }}</p>
      <input
        type="file"
        class="sr-only"
        multiple
        [id]="id"
        [accept]="accept()"
        [disabled]="disabled()"
        [attr.aria-describedby]="help() ? id + '-help' : null"
        (change)="pick($event)"
      />
      <label class="f-file-drop__button" [for]="id">
        <furar-icon name="plus" />
        {{ button() }}
      </label>
      @if (help()) {
        <small class="f-file-drop__help" [id]="id + '-help'">{{ help() }}</small>
      }
    </div>
  `,
})
export class FileDropComponent {
  /** «Arrastra aquí as fotos da instalación». */
  readonly title = input.required<string>();
  /** Texto do botón («Subir fotos»). */
  readonly button = input.required<string>();
  readonly help = input('');
  readonly accept = input('');
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Ficheiros escollidos ou soltados. */
  readonly files = output<File[]>();

  protected readonly id = `furar-file-drop-${nextId++}`;
  protected readonly over = signal(false);

  protected pick(event: Event): void {
    const element = event.target as HTMLInputElement;
    const files = Array.from(element.files ?? []);
    element.value = '';
    if (files.length) this.files.emit(files);
  }

  protected dragOver(event: DragEvent): void {
    if (this.disabled()) return;
    event.preventDefault();
    this.over.set(true);
  }

  protected drop(event: DragEvent): void {
    event.preventDefault();
    this.over.set(false);
    if (this.disabled()) return;
    const files = Array.from(event.dataTransfer?.files ?? []);
    if (files.length) this.files.emit(files);
  }
}
