// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, effect, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import type { RouterStateSnapshot } from '@angular/router';
import { TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../i18n/language.service';
import { CurrentMunicipality } from '../municipality/current-municipality';

/** O `title` das rutas é unha clave de tradución; o título cambia co idioma (WCAG 2.4.2). */
@Injectable({ providedIn: 'root' })
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private readonly municipality = inject(CurrentMunicipality);
  private key: string | undefined;

  constructor() {
    super();
    const language = inject(LanguageService);
    effect(() => {
      language.lang();
      this.municipality.name();
      this.render();
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.key = this.buildTitle(snapshot);
    this.render();
  }

  private render(): void {
    const parts = [
      this.key ? this.transloco.translate(this.key) : undefined,
      this.municipality.name() ?? undefined,
      this.transloco.translate('app.name'),
    ];
    this.title.setTitle(parts.filter((part, index, all) => part && all.indexOf(part) === index).join(' · '));
  }
}
