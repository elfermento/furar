// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  effect,
  inject,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { PlaceOfPipe } from '../../../shared/i18n/place-of.pipe';
import { InvitationFlow } from './staff-invitation.flow';
import { StaffInvitationStore } from './staff-invitation.store';

/**
 * Aceptar unha invitación ao persoal (`/:concello/convite#<token>`; maqueta 38). O token chega no
 * fragmento: gárdase en `sessionStorage` e o URL queda limpo (substitúese a entrada do historial).
 * Sen sesión, a web vai directamente ao rexistro de Keycloak co correo invitado; ao volver con
 * sesión acepta soa, sen capa nin casiña (ADR-0027, revisión do 2026-11-03). Cada estado ten o
 * seu título, enfocado ao chegar (WCAG 2.4.3).
 */
@Component({
  selector: 'furar-staff-invitation-page',
  imports: [PlaceOfPipe, ButtonDirective, PageHeaderComponent, RouterLink, StatusStateComponent, TranslocoPipe],
  providers: [StaffInvitationStore, InvitationFlow],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-invitation.page.html',
})
export class StaffInvitationPage implements OnInit {
  protected readonly flow = inject(InvitationFlow);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  constructor() {
    // Título enfocado en cada estado (WCAG 2.4.3): tamén ao volver de Keycloak, salvo que o foco xa
    // estea nun botón da páxina.
    effect(() => {
      if (this.flow.step() === 'loading') return;
      afterNextRender(
        () => {
          const host = this.host.nativeElement;
          const active = host.ownerDocument.activeElement;
          if (active && active !== host.ownerDocument.body && host.contains(active)) return;
          const title = host.querySelector<HTMLElement>('h1');
          if (title) {
            title.tabIndex = -1;
            title.focus();
          }
        },
        { injector: this.injector },
      );
    });
  }

  ngOnInit(): void {
    const fragment = this.route.snapshot.fragment;
    if (fragment !== null) {
      this.flow.receive(fragment);
      // Fóra do URL e do historial: substitúe a entrada actual polo mesmo camiño sen fragmento.
      void this.router.navigate([], { relativeTo: this.route, replaceUrl: true });
    }
    void this.flow.start();
  }
}
