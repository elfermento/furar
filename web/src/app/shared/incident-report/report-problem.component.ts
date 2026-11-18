// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, DestroyRef, afterNextRender, inject, input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { SessionService } from '../../core/auth/session.service';
import { ButtonDirective } from '../ui/button/button.directive';
import { DialogService } from '../ui/dialog/dialog.service';
import { IconComponent } from '../ui/icon/icon.component';
import { REPORT_PARAM } from './incident-report';
import { openIncidentReport } from './open-incident-report';

/**
 * O bloque do final da ficha pública: «Viches algo que non funciona?» e,
 * debaixo, «Avisar dun problema», todo centrado. Sen sesión o botón vese igual: leva a entrar e
 * volve á ficha co aviso aberto (`?avisar=1`).
 */
@Component({
  selector: 'furar-report-problem',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-report-problem', 'data-report-problem': '' },
  template: `
    <p class="f-report-problem__question">{{ 'incident.report.question' | transloco }}</p>
    <button type="button" furarButton data-report-open (click)="open()">
      <furar-icon name="warn" />
      {{ 'incident.report.open' | transloco }}
    </button>
  `,
})
export class ReportProblemComponent {
  readonly slug = input.required<string>();
  readonly facilityId = input.required<string>();
  readonly facilityName = input.required<string>();

  private readonly session = inject(SessionService);
  private readonly dialogs = inject(DialogService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      if (this.route.snapshot.queryParamMap.get(REPORT_PARAM) === null || !this.session.authenticated()) return;
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { [REPORT_PARAM]: null },
        replaceUrl: true,
      });
      if (!destroyRef.destroyed) void this.show();
    });
  }

  protected async open(): Promise<void> {
    if (!this.session.authenticated()) {
      const tree = this.router.createUrlTree([], {
        relativeTo: this.route,
        queryParams: { [REPORT_PARAM]: 1 },
        queryParamsHandling: 'merge',
      });
      await this.session.login(this.router.serializeUrl(tree));
      return;
    }
    await this.show();
  }

  private show(): Promise<boolean> {
    return openIncidentReport(this.dialogs, {
      slug: this.slug(),
      mode: 'resident',
      facilityId: this.facilityId(),
      subheading: this.facilityName(),
    });
  }
}
