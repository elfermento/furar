// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../shared/ui/callout/callout.component';
import { DeleteButtonComponent } from '../../shared/ui/delete-button/delete-button.component';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { FieldComponent } from '../../shared/ui/field.component';
import { FieldGroupComponent } from '../../shared/ui/field-group/field-group.component';
import { FieldLineComponent } from '../../shared/ui/field-line/field-line.component';
import { ChipMenuComponent } from '../../shared/ui/menu/chip-menu.component';
import type { ChipMenuOption } from '../../shared/ui/menu/chip-menu.component';
import { StatusStateComponent } from '../../shared/ui/status-state/status-state.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import { IncidentPhotosComponent } from './incident-photos.component';
import type { Incident, IncidentStatus } from './incidents';
import { STATUSES, STATUS_TONE, dayOf, reporterOf, timeOf } from './incidents';
import { IncidentsStore } from './incidents.store';

export interface IncidentDetailData {
  readonly slug: string;
  readonly id: string;
}

let nextId = 0;

/** Abre o detalle dunha incidencia (folla no móbil). */
export async function openIncidentDetail(dialogs: DialogService, data: IncidentDetailData): Promise<void> {
  const ref = dialogs.open<unknown, IncidentDetailData>(IncidentDetailDialogComponent, { data, sheet: true });
  await new Promise<void>((resolve) => ref.closed.subscribe({ complete: resolve }));
}

/**
 * O detalle dunha incidencia: persoa, data, hora, reserva, tipo e o estado
 * nun `furar-chip-menu`; o aviso coas fotos ampliables; ao escoller «Resolta», a mensaxe para a
 * persoa (só se avisou a veciñanza) e a nota interna. «Gardar» con If-Match.
 */
@Component({
  selector: 'furar-incident-detail-dialog',
  imports: [
    ButtonDirective,
    CalloutComponent,
    ChipMenuComponent,
    DeleteButtonComponent,
    DialogComponent,
    FieldComponent,
    FieldGroupComponent,
    FieldLineComponent,
    IncidentPhotosComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './incident-detail-dialog.component.html',
})
export class IncidentDetailDialogComponent {
  protected readonly data = inject<IncidentDetailData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<unknown>>(DialogRef);
  private readonly store = inject(IncidentsStore);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly dialogs = inject(DialogService);
  /** Eliminar (a incidencia ou unha foto): só a xestión. */
  protected readonly canDelete = inject(StaffAccessService).canManage;

  protected readonly ids = `furar-incident-detail-${nextId++}`;
  protected readonly incident = signal<Incident | null>(null);
  protected readonly failed = signal(false);
  protected readonly status = signal<IncidentStatus>('NEW');
  protected readonly note = signal('');
  protected readonly message = signal('');
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly dayOf = dayOf;
  protected readonly timeOf = timeOf;

  protected readonly statusOptions = computed<ChipMenuOption<IncidentStatus>[]>(() =>
    STATUSES.map((value) => ({
      value,
      label: this.transloco.translate('incident.status.' + value),
      tone: STATUS_TONE[value],
    })),
  );
  /** A mensaxe para a persoa: só ao resolver un aviso da veciñanza que aínda ten correo. */
  protected readonly messaging = computed(() => {
    const incident = this.incident();
    return (
      this.status() === 'RESOLVED' &&
      incident?.origin === 'RESIDENT' &&
      !incident.reporter.anonymized &&
      !!incident.reporter.email
    );
  });
  protected readonly person = computed(() => {
    const incident = this.incident();
    if (!incident) return '';
    if (incident.reporter.anonymized) return this.transloco.translate('incident.detail.anonymized');
    return reporterOf(incident) ?? this.transloco.translate('incident.detail.staff');
  });
  protected readonly messageLabel = computed(() => {
    const name = this.incident()?.reporter.name?.split(' ')[0];
    return name
      ? this.transloco.translate('incident.detail.message', { name })
      : this.transloco.translate('incident.detail.messageAnyone');
  });
  protected readonly bookingText = computed(() => {
    const booking = this.incident()?.booking;
    if (!booking) return null;
    return `${dayOf(booking.start).slice(0, 5)} · ${timeOf(booking.start)}–${timeOf(booking.end)}${booking.useName ? ' · ' + booking.useName : ''}`;
  });

  constructor() {
    void this.load();
  }

  protected typed(event: Event): string {
    return (event.target as HTMLTextAreaElement).value;
  }

  protected async load(): Promise<void> {
    this.failed.set(false);
    try {
      this.show(await this.store.get(this.data.slug, this.data.id));
    } catch {
      this.failed.set(true);
    }
  }

  protected async save(): Promise<void> {
    const incident = this.incident();
    if (!incident || this.busy()) return;
    this.busy.set(true);
    this.problem.set(null);
    try {
      const saved = await this.store.review(this.data.slug, incident, {
        status: this.status(),
        internalNote: this.note().trim() || undefined,
        resolutionMessage: this.messaging() ? this.message().trim() || undefined : undefined,
      });
      this.show(saved);
      this.toasts.show('incident.detail.saved');
      this.ref.close();
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(): Promise<void> {
    const incident = this.incident();
    if (!incident) return;
    const t = (key: string) => this.transloco.translate('incident.detail.delete.' + key);
    try {
      const done = await this.dialogs.confirm({
        heading: t('heading'),
        message: t('message'),
        confirmLabel: t('confirm'),
        tone: 'danger',
        action: () => this.store.remove(this.data.slug, incident),
      });
      if (!done) return;
      this.toasts.show('incident.detail.delete.done');
      this.ref.close();
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  protected async removePhoto(photo: string): Promise<void> {
    const incident = this.incident();
    if (!incident) return;
    const t = (key: string) => this.transloco.translate('incident.detail.deletePhoto.' + key);
    let saved: Incident | null = null;
    try {
      const done = await this.dialogs.confirm({
        heading: t('heading'),
        message: t('message'),
        confirmLabel: t('confirm'),
        tone: 'danger',
        action: async () => (saved = await this.store.removePhoto(this.data.slug, incident, photo)),
      });
      if (done && saved) this.show(saved);
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  private show(incident: Incident): void {
    this.incident.set(incident);
    this.status.set(incident.status);
    this.note.set(incident.internalNote ?? '');
    this.message.set(incident.resolutionMessage ?? '');
  }
}
