// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import type { RouterStateSnapshot } from '@angular/router';
import { TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { merge } from 'rxjs';
import { CurrentMunicipality } from '../municipality/current-municipality';

/**
 * O `title` das rutas é unha clave de tradución; o título cambia co idioma (WCAG 2.4.2).
 * Ata que os textos do idioma activo están cargados non se toca: queda o de `index.html`
 * (ou o anterior) e nunca se ve unha clave na pestana.
 */
@Injectable({ providedIn: 'root' })
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private readonly municipality = inject(CurrentMunicipality);
  private key: string | undefined;

  constructor() {
    super();
    effect(() => {
      this.municipality.name();
      this.render();
    });
    // O idioma activo cambia cando os seus textos xa están; no arranque, ademais, os do idioma
    // por defecto chegan sen cambio de idioma.
    merge(this.transloco.langChanges$, this.transloco.events$)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.render());
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.key = this.buildTitle(snapshot);
    this.render();
  }

  private render(): void {
    if (Object.keys(this.transloco.getTranslation(this.transloco.getActiveLang())).length === 0) {
      return;
    }
    const parts = [
      this.key ? this.transloco.translate(this.key) : undefined,
      this.municipality.name() ?? undefined,
      this.transloco.translate('app.name'),
    ];
    this.title.setTitle(parts.filter((part, index, all) => part && all.indexOf(part) === index).join(' · '));
  }
}
