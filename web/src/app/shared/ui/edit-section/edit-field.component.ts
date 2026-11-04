// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import type { AbstractControl } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { EditSession } from '../edit-session/edit-session';
import { IconComponent } from '../icon/icon.component';
import { firstFocusable } from './section-errors';

/**
 * Un valor da sección editable no sitio (D1-05b; docs/deseno.md §8, contrato de foco do plan):
 * en repouso é un `<button>` co rótulo e o valor («Teléfono 981 000 000, Editar»); ao activalo
 * amósase o control que pon a feature (o contido) e o foco vai ao seu primeiro campo. Esc pecha
 * e devolve o foco ao botón sen desfacer (desfai «Cancelar» da sección). Co control inválido e
 * tocado queda aberto, para que o erro non quede tapado. Pecha ao gardar ou cancelar a sección.
 *
 * Un control con capa propia (selector de hora, combobox) que use Esc para pechala debe chamar a
 * `preventDefault()`: entón Esc non pecha o campo.
 *
 * Co modo edición global (`EditSession`), non hai botón nin lapis: fóra da edición é texto
 * (rótulo e valor) e dentro, o control directamente.
 */
@Component({
  selector: 'furar-edit-field',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-edit-field',
    '[attr.data-editing]': 'shown() ? "" : null',
    '(keydown.escape)': 'onEscape($event)',
  },
  template: `
    @if (session && !shown()) {
      <div class="f-edit-field__static">
        <span class="f-edit-field__label">{{ label() }}</span>
        <span class="f-edit-field__value" [attr.data-empty]="value() ? null : ''">
          {{ value() || ('ui.editSection.empty' | transloco) }}
        </span>
      </div>
    }
    <button #trigger type="button" class="f-edit-field__trigger" [hidden]="shown() || session" (click)="open()">
      <span class="f-edit-field__label">{{ label() }}</span>
      <span class="f-edit-field__value" [attr.data-empty]="value() ? null : ''">
        {{ value() || ('ui.editSection.empty' | transloco) }}
      </span>
      <span class="sr-only">, {{ 'ui.editSection.edit' | transloco }}</span>
      <furar-icon class="f-edit-field__pencil" name="pencil" />
    </button>
    <div #editor class="f-edit-field__editor" [hidden]="!shown()">
      <ng-content />
    </div>
  `,
})
export class EditFieldComponent {
  /** Rótulo do valor (o mesmo ca o do campo): «Teléfono». */
  readonly label = input.required<string>();
  /** Valor en texto, xa formatado; baleiro → «Sen indicar». */
  readonly value = input<string | null>('');
  /** Control (ou grupo) do campo: se está inválido e tocado, o campo non se pecha. */
  readonly control = input<AbstractControl | null>(null);

  protected readonly session = inject(EditSession, { optional: true });
  private readonly injector = inject(Injector);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly editor = viewChild.required<ElementRef<HTMLElement>>('editor');
  private readonly editing = signal(false);
  private readonly showsError = signal(false);
  /** Aberto: porque se activou, porque ten un erro á vista ou porque a pantalla está en edición. */
  readonly shown = computed(() => (this.session ? this.session.editing() : this.editing() || this.showsError()));

  constructor() {
    effect((onCleanup) => {
      const control = this.control();
      if (!control) {
        this.showsError.set(false);
        return;
      }
      const sync = (): void => this.showsError.set(control.invalid && control.touched);
      sync();
      const subscription = control.events.subscribe(sync);
      onCleanup(() => subscription.unsubscribe());
    });
  }

  /** Abre o campo e leva o foco ao primeiro control. */
  open(): void {
    this.editing.set(true);
    afterNextRender(() => firstFocusable(this.editor().nativeElement)?.focus(), { injector: this.injector });
  }

  /** Volve ao valor en repouso sen mover o foco (a sección decide onde vai). */
  close(): void {
    this.editing.set(false);
  }

  protected onEscape(event: Event): void {
    if (this.session || !this.editing() || event.defaultPrevented || this.showsError()) {
      return;
    }
    event.preventDefault();
    this.editing.set(false);
    afterNextRender(() => this.trigger().nativeElement.focus(), { injector: this.injector });
  }
}
