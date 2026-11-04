// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SegmentedDirective } from '../../shared/ui/segmented/segmented.directive';
import { LanguageService } from '../i18n/language.service';
import type { Language } from '../i18n/languages';
import { LANGUAGES } from '../i18n/languages';

/**
 * Cada idioma co seu código á vista («GL») e o nome no propio idioma para os lectores, co seu
 * atributo lang (WCAG 3.1.2); o nome accesible contén o texto visible (2.5.3). Carril [furarSegmented].
 */
@Component({
  selector: 'furar-language-switcher',
  imports: [TranslocoPipe, SegmentedDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div role="group" furarSegmented class="f-lang" [attr.aria-label]="'lang.label' | transloco">
      @for (option of languages; track option) {
        <button
          type="button"
          [attr.lang]="option"
          [attr.aria-pressed]="option === language.lang()"
          (click)="choose(option)"
        >
          {{ 'lang.short.' + option | transloco }}
          <span class="sr-only">{{ 'lang.endonym.' + option | transloco }}</span>
        </button>
      }
    </div>
  `,
})
export class LanguageSwitcherComponent {
  protected readonly language = inject(LanguageService);
  protected readonly languages = LANGUAGES;

  protected choose(lang: Language): void {
    void this.language.use(lang, true);
  }
}
