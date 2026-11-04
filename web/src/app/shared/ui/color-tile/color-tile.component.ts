// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { map, startWith, switchMap } from 'rxjs';
import { isHexColor, luminance } from '../../../core/theme/contrast';

/** Por riba desta luminosidade a mostra leva un bordo fino (o branco sobre o fondo da páxina). */
const PALE = 0.85;

/**
 * Unha cor como mosaico (maqueta 49): a mostra é o propio mosaico, sen card arredor, co nome e o
 * código #rrggbb debaixo. Premer nela abre o selector de cor do navegador (`input type="color"`
 * enriba da mostra, transparente), que leva o nome da cor como rótulo. Bordo fino só se a cor é
 * moi clara e se confundiría co fondo.
 */
@Component({
  selector: 'furar-color-tile',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-color-tile' },
  template: `
    <label class="f-color-tile__label">
      <span class="f-color-tile__swatch" [style.background]="value()" [attr.data-pale]="pale() ? '' : null">
        <input type="color" class="f-color-tile__input" [formControl]="control()" [attr.data-color]="name()" />
      </span>
      <span class="f-color-tile__name">{{ label() }}</span>
      <span class="f-color-tile__hex">{{ value() }}</span>
    </label>
  `,
})
export class ColorTileComponent {
  readonly control = input.required<FormControl<string>>();
  readonly label = input.required<string>();
  /** Clave da cor (`primary`), para as probas e a vista previa. */
  readonly name = input('');

  protected readonly value = toSignal(
    toObservable(this.control).pipe(
      switchMap((control) => control.valueChanges.pipe(startWith(control.value))),
      map((value) => value ?? ''),
    ),
    { initialValue: '' },
  );
  protected readonly pale = computed(() => isHexColor(this.value()) && luminance(this.value()) > PALE);
}
