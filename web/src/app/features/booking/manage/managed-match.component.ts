// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { FieldLineComponent } from '../../../shared/ui/field-line/field-line.component';
import type { ChipMenuOption } from '../../../shared/ui/menu/chip-menu.component';
import { ChipMenuComponent } from '../../../shared/ui/menu/chip-menu.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { ManagedBookingDetailResponse } from './manage-bookings';
import { ManageBookingsStore } from './manage-bookings.store';

type Shown = 'shown' | 'hidden';

/**
 * «Partida: Na portada / Oculta» no detalle dunha reserva do concello (ADR-0040, punto 8):
 * fila solta co chip-menú, só para a xestión (o seu permiso de ocultar partidas) e mentres a
 * reserva busca xente. Ocultala avisa a quen organiza; o erro vai ao aviso do detalle (`failed`).
 */
@Component({
  selector: 'furar-managed-match',
  imports: [ChipMenuComponent, FieldLineComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @if (visible()) {
      <furar-field-line data-detail-match [label]="'openMatch.manage.line' | transloco">
        <furar-chip-menu
          [label]="'openMatch.manage.line' | transloco"
          [options]="options()"
          [value]="detail().openMatch?.hidden ? 'hidden' : 'shown'"
          [busy]="busy()"
          (selected)="set($event)"
        />
      </furar-field-line>
    }
  `,
})
export class ManagedMatchComponent {
  readonly detail = input.required<ManagedBookingDetailResponse>();
  readonly failed = output<unknown>();

  private readonly store = inject(ManageBookingsStore);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly busy = signal(false);
  protected readonly visible = computed(() => this.staff.canManage() && this.detail().openMatch?.status === 'OPEN');
  protected readonly options = computed<ChipMenuOption<Shown>[]>(() => {
    this.langChanges();
    return [
      { value: 'shown', label: this.transloco.translate('openMatch.manage.shown'), tone: 'info' },
      { value: 'hidden', label: this.transloco.translate('openMatch.manage.hidden'), tone: 'muted' },
    ];
  });

  protected async set(value: Shown): Promise<void> {
    this.busy.set(true);
    try {
      await this.store.setOpenMatchHidden(this.municipality.slug() ?? '', this.detail(), value === 'hidden');
      this.toasts.show(value === 'hidden' ? 'openMatch.manage.hiddenToast' : 'openMatch.manage.shownToast');
    } catch (error) {
      this.failed.emit(error);
    } finally {
      this.busy.set(false);
    }
  }
}
