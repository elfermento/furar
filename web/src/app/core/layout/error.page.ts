// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';

const KNOWN = ['403', '404', '500'] as const;

/** Páxina de erro (403, 404, 500). Úsase con skipLocationChange: o URL non cambia. */
@Component({
  selector: 'furar-error-page',
  imports: [RouterLink, TranslocoPipe, ButtonDirective, StatusStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-status-state
      mode="error"
      [headingLevel]="1"
      [heading]="'errorPage.' + kind() + '.title' | transloco"
      [description]="'errorPage.' + kind() + '.text' | transloco"
    >
      <a routerLink="/" furarButton="primary">{{ 'errorPage.home' | transloco }}</a>
    </furar-status-state>
  `,
})
export class ErrorPage {
  /** Parámetro da ruta /erro/:status (withComponentInputBinding). */
  readonly status = input<string>('404');
  protected readonly kind = computed(() => {
    const status = this.status();
    return (KNOWN as readonly string[]).includes(status) ? status : '404';
  });
}
