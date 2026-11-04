// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ThemeColors } from '../../../core/theme/contrast';
import { IconComponent } from '../../../shared/ui/icon/icon.component';

/**
 * Vista previa das cores do tema (maqueta 49): unha barra coa marca, unha tarxeta de reserva cun
 * enlace e o botón, e un campo co foco, pintados coas cores do formulario en vivo. É unha imaxe
 * (`role="img"` co seu nome): os textos son de mostra.
 */
@Component({
  selector: 'furar-theme-preview',
  imports: [IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let c = colors();
    <div
      class="f-theme-preview"
      role="img"
      [attr.aria-label]="'theme.preview.label' | transloco"
      [style.background]="c.background"
      [style.color]="c.text"
    >
      <div class="f-theme-preview__bar">
        <span class="f-theme-preview__shield" [style.background]="c.primary" [style.color]="c.onPrimary">
          <furar-icon name="shield" />
        </span>
        <span class="f-theme-preview__names">
          <b>{{ name() }}</b>
          <small>{{ 'site.tagline' | transloco }}</small>
        </span>
      </div>
      <div class="f-theme-preview__body">
        <div class="f-theme-preview__card" [style.border-color]="c.primary">
          <div class="f-theme-preview__row">
            <b>{{ 'theme.preview.facility' | transloco }}</b>
          </div>
          <p>{{ 'theme.preview.when' | transloco }}</p>
          <div class="f-theme-preview__row">
            <span class="f-theme-preview__link" [style.color]="c.accent">{{ 'theme.preview.link' | transloco }}</span>
            <span class="f-theme-preview__button" [style.background]="c.primary" [style.color]="c.onPrimary">
              {{ 'theme.preview.button' | transloco }}
            </span>
          </div>
        </div>
        <div class="f-theme-preview__field">
          <b>{{ 'theme.preview.field' | transloco }}</b>
          <span class="f-theme-preview__input" [style.border-color]="c.primary" [style.outline-color]="c.primary">
            {{ 'theme.preview.value' | transloco }}
          </span>
        </div>
      </div>
    </div>
  `,
})
export class ThemePreviewComponent {
  readonly colors = input.required<ThemeColors>();
  /** Nome do concello na barra. */
  readonly name = input('');
}
