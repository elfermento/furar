// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, Injector, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { managementHome } from '../../core/auth/roles';
import { SessionService } from '../../core/auth/session.service';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { StaffMembershipsService } from '../../core/auth/staff-memberships.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { openAccountDeletionWith } from '../../shared/account-deletion/open-account-deletion';
import { placeParams } from '../../shared/i18n/place-name';
import { ActionItemComponent, ActionListDirective } from '../../shared/ui/action-list/action-list.component';
import { formatInstant } from '../../shared/ui/calendar/madrid-time';
import { CardComponent } from '../../shared/ui/card/card.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../shared/ui/page-header/page-header.component';
import { TagComponent } from '../../shared/ui/tag/tag.component';
import { initials } from '../../shared/ui/user-menu/initials';

/** Un concello da persoa como persoal: o nome («Concello de Narón»), o rol e a súa xestión. */
interface Place {
  readonly slug: string;
  readonly name: string;
  readonly role: string;
  readonly link: string;
  readonly current: boolean;
}

/**
 * «A miña conta» (`/conta` e `/:concello/conta`): unha card con quen é (iniciais, nome e correo),
 * o acceso anterior a esta sesión no concello actual (ENS op.acc.6 R5, só o persoal: a API só o
 * garda para el) e, se é persoal, os seus concellos co rol; cada un leva á súa xestión e o actual
 * leva «Actual». Debaixo, «Seguridade da conta» (o contrasinal e o segundo factor, na consola de
 * Keycloak) e «Eliminar a miña conta» (ADR-0043), en vermello e a última.
 */
@Component({
  selector: 'furar-account-page',
  imports: [
    ActionItemComponent,
    ActionListDirective,
    CardComponent,
    IconComponent,
    PageHeaderComponent,
    RouterLink,
    TagComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './account.page.html',
})
export class AccountPage {
  protected readonly session = inject(SessionService);
  private readonly staff = inject(StaffAccessService);
  private readonly memberships = inject(StaffMembershipsService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly lang = toSignal(this.transloco.selectTranslation());

  protected readonly letters = computed(() => initials(this.session.name() ?? ''));
  /** «18/11/2026, 09:05» (Europe/Madrid), se a API devolve un acceso anterior. */
  protected readonly lastSignIn = computed(() => formatInstant(this.staff.previousSignInAt()));
  protected readonly places = computed((): readonly Place[] => {
    this.lang();
    const lang = this.transloco.getActiveLang();
    const slug = this.municipality.slug();
    return this.memberships.memberships().map((m) => ({
      slug: m.slug,
      name: this.transloco.translate('session.municipality', placeParams(m.name, lang)),
      role: this.transloco.translate('session.role.' + m.role),
      link: managementHome(m.slug, m.role),
      current: m.slug === slug,
    }));
  });

  /** Pregunta á API e, se se pode, vai a Keycloak; se queda algo pendente, o diálogo lístao. */
  protected deleteAccount(): Promise<void> {
    return openAccountDeletionWith(this.injector, () => this.session.deleteAccount(this.here()));
  }

  /** O URL do navegador: a volta de Keycloak se a persoa non remata. */
  private here(): string {
    const location = this.document.defaultView?.location;
    return location ? location.pathname + location.search + location.hash : this.router.url;
  }
}
