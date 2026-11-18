// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { PlatformAccessService } from '../../../core/auth/platform-access.service';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { StaffMembershipsService } from '../../../core/auth/staff-memberships.service';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { placeParams } from '../../../shared/i18n/place-name';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { ConfirmDetail } from '../../../shared/ui/dialog/confirm.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { StaffMembersStore } from './staff-members.store';

/**
 * «Engadirme como xestor» (ADR-0028, revisión do 2026-11-11): o botón da
 * plataforma na cabeceira de «Persoal», xunto a «Invitar». Confirma co que pasa (verá as reservas
 * e os datos da veciñanza e queda no rexistro); ao aceptar, a súa fila entra na lista, sae o toast
 * e o acceso e o cambio de concello do menú pásanse ao día. Só se ve para a plataforma mentres
 * non é xestora do concello.
 */
@Component({
  selector: 'furar-join-as-manager',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visible()) {
      <button type="button" furarButton data-join (click)="join()">
        <furar-icon name="user" />
        {{ 'staff.members.join.open' | transloco }}
      </button>
    }
  `,
})
export class JoinAsManagerComponent {
  private readonly store = inject(StaffMembersStore);
  private readonly staff = inject(StaffAccessService);
  private readonly platform = inject(PlatformAccessService);
  private readonly memberships = inject(StaffMembershipsService);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly toasts = inject(ToastService);
  private readonly municipality = inject(CurrentMunicipality);
  /** Xa é xestora: a páxina leva o foco a «Invitar». */
  readonly joined = output();
  /** A API dixo que non: a páxina móstrao como os demais erros. */
  readonly failed = output<unknown>();

  protected readonly visible = computed(() => this.platform.platformAdmin() && this.staff.role() !== 'MANAGER');

  protected async join(): Promise<void> {
    const slug = this.municipality.slug() ?? '';
    const place = placeParams(this.municipality.name() ?? '', this.language.lang());
    const t = (key: string) => this.transloco.translate(`staff.members.join.${key}`, place);
    const details: ConfirmDetail[] = [
      { icon: 'visibility', text: t('sees'), tone: 'warn' },
      { icon: 'schedule', text: t('logged') },
    ];
    try {
      const done = await this.dialogs.confirm({
        heading: t('title'),
        details,
        confirmLabel: t('confirm'),
        busyLabel: t('busy'),
        action: () => this.store.join(slug),
      });
      if (!done) return;
      this.toasts.show('staff.members.done.joined', place);
      await Promise.all([this.staff.reload(slug), this.memberships.reload()]);
      this.joined.emit();
    } catch (error) {
      this.failed.emit(error);
    }
  }
}
