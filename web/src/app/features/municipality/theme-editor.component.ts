// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormControl, FormGroup } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { map, startWith, switchMap } from 'rxjs';
import type { ContrastCheck, ThemeColors } from '../../core/theme/contrast';
import { checkTheme, fixContrast, fixDirection, fixedColor } from '../../core/theme/contrast';
import { FieldsetDirective } from '../../shared/ui/card/fieldset.directive';
import { ColorTileComponent } from '../../shared/ui/color-tile/color-tile.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';

export type ThemeForm = FormGroup<{ [K in keyof ThemeColors]: FormControl<string> }>;

export const THEME_COLORS: readonly (keyof ThemeColors)[] = ['primary', 'onPrimary', 'accent', 'background', 'text'];

/**
 * Cores do tema (maqueta 49): cada cor é un mosaico (`furar-color-tile`) e, debaixo, se se le
 * ben, en frases sen números («O texto dos botóns lese ben»). A que non chega ao mínimo di que
 * cor escurecer ou aclarar e leva «Axustar», que a cambia o xusto (`fixContrast`). As razóns de
 * contraste van pechadas en «Detalles técnicos». O que se proxecta (`[themeEditorAfter]`) vai
 * despois dos mosaicos: o botón da vista previa.
 */
@Component({
  selector: 'furar-theme-editor',
  // Bloque: un elemento en liña con bloques dentro parte en caixas anónimas e o toque cae fóra.
  host: { class: 'block' },
  imports: [ColorTileComponent, FieldsetDirective, IconComponent, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset furarFieldset class="f-theme" [formGroup]="form()">
      <legend>{{ 'theme.title' | transloco }}</legend>
      <div class="f-color-tiles">
        @for (name of names; track name) {
          <furar-color-tile [control]="form().controls[name]" [label]="'theme.' + name | transloco" [name]="name" />
        }
      </div>
      <ng-content select="[themeEditorAfter]" />
      <ul class="f-theme__checks" [attr.aria-label]="'theme.checks' | transloco" aria-live="polite">
        @for (check of checks(); track check.pair) {
          <li [attr.data-ok]="check.ok" [attr.data-check]="check.pair">
            <furar-icon [name]="check.ok ? 'check' : 'danger'" />
            <span>
              @if (check.ok) {
                {{ 'theme.plain.' + check.pair + '.ok' | transloco }}
              } @else {
                {{
                  'theme.plain.' + check.pair + '.low'
                    | transloco: { action: ('theme.plain.' + direction(check) | transloco) }
                }}
                <button
                  type="button"
                  class="f-theme__fix"
                  [attr.data-fix]="check.pair"
                  [attr.aria-label]="
                    'theme.fix' | transloco: { what: ('theme.plain.' + check.pair + '.what' | transloco) }
                  "
                  (click)="fix(check.pair)"
                >
                  {{ 'theme.fixShort' | transloco }}
                </button>
              }
            </span>
          </li>
        }
      </ul>
      <details class="f-theme__tech">
        <summary>{{ 'theme.technical' | transloco }}</summary>
        <ul [attr.aria-label]="'theme.contrast' | transloco">
          @for (check of checks(); track check.pair) {
            <li [attr.data-ok]="check.ok">
              {{
                'theme.check.' + (check.ok ? 'ok' : 'low')
                  | transloco
                    : {
                        pair: ('theme.pair.' + check.pair | transloco),
                        ratio: check.ratio.toFixed(2),
                        minimum: check.minimum,
                      }
              }}
            </li>
          }
        </ul>
      </details>
    </fieldset>
  `,
})
export class ThemeEditorComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly injector = inject(Injector);
  readonly form = input.required<ThemeForm>();
  protected readonly names = THEME_COLORS;

  /** As cores do formulario, en vivo. */
  readonly colors = toSignal(
    toObservable(this.form).pipe(
      switchMap((form) =>
        form.valueChanges.pipe(
          startWith(null),
          map(() => form.getRawValue()),
        ),
      ),
    ),
    { initialValue: { primary: '', onPrimary: '', accent: '', background: '', text: '' } },
  );
  protected readonly checks = computed(() => checkTheme(this.colors()));

  protected direction(check: ContrastCheck): string {
    return fixDirection(this.colors(), check.pair);
  }

  /**
   * «Axustar»: cambia só a cor do par, o xusto para chegar ao mínimo, e marca o cambio. O botón
   * desaparece co erro: o foco vai á cor que cambiou.
   */
  protected fix(pair: ContrastCheck['pair']): void {
    const key = fixedColor(pair);
    const next = fixContrast(this.form().getRawValue(), pair);
    const control = this.form().controls[key];
    control.setValue(next[key]);
    control.markAsDirty();
    afterNextRender(() => this.host.querySelector<HTMLElement>(`[data-color="${key}"]`)?.focus(), {
      injector: this.injector,
    });
  }
}
