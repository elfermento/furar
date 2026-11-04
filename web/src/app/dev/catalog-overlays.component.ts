// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ApiProblem } from '../core/errors/api-problem';
import { AffectedBookingsFlow } from '../features/facility/affected-bookings/affected-bookings.flow';
import { ButtonDirective } from '../shared/ui/button/button.directive';
import { CardComponent } from '../shared/ui/card/card.component';
import { DialogComponent } from '../shared/ui/dialog/dialog.component';
import { DialogService } from '../shared/ui/dialog/dialog.service';
import { ChipMenuComponent } from '../shared/ui/menu/chip-menu.component';
import { SwitchComponent } from '../shared/ui/switch/switch.component';

type SampleStatus = 'ACTIVE' | 'TEMPORARILY_CLOSED' | 'WITHDRAWN';

/** Reservas de exemplo para o modal de reservas afectadas (D1-19; P19: só no catálogo ata o dominio 2). */
const SAMPLE_AFFECTED = [
  {
    bookingId: 'a1',
    holderName: 'Club Deportivo Narón',
    date: '2026-11-03',
    startMinute: 480,
    endMinute: 570,
    useName: 'Fútbol 11',
    hasEmail: true,
    phone: '981 38 44 10',
  },
  {
    bookingId: 'a2',
    holderName: 'Lucía Varela',
    date: '2026-11-03',
    startMinute: 600,
    endMinute: 660,
    useName: 'Fútbol 7, Campo A',
    hasEmail: false,
    phone: '666 12 34 56',
  },
  {
    bookingId: 'a3',
    holderName: 'Escola Xoán XXIII',
    date: '2026-11-03',
    startMinute: 600,
    endMinute: 660,
    useName: 'Fútbol 7, Campo B',
    hasEmail: true,
    phone: '981 35 07 21',
  },
];

/** Capa de exemplo para o catálogo: título, texto, contido e pé con dúas accións. */
@Component({
  selector: 'furar-catalog-sample-dialog',
  imports: [ButtonDirective, DialogComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog [heading]="'catalog.ov.dialogTitle' | transloco" [description]="'catalog.ov.dialogText' | transloco">
      <p>{{ 'catalog.ov.dialogBody' | transloco }}</p>
      <ng-container dialogActions>
        <button type="button" furarButton (click)="ref.close()">{{ 'ui.confirm.cancel' | transloco }}</button>
        <button type="button" furarButton="primary" (click)="ref.close()">
          {{ 'catalog.ov.dialogSave' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class CatalogSampleDialogComponent {
  protected readonly ref = inject(DialogRef);
}

/**
 * Sección do catálogo para as capas de D1-05a: diálogo, confirmación, chip de estado con menú
 * (Activa e Pechada aplícanse directo; «De baixa» confirma, como pide P17) e interruptores.
 */
@Component({
  selector: 'furar-catalog-overlays',
  imports: [ButtonDirective, CardComponent, ChipMenuComponent, ReactiveFormsModule, SwitchComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.overlays' | transloco" id="capas">
      <div class="gap-form grid">
        <div class="gap-item flex flex-wrap items-center">
          <button type="button" furarButton data-open-dialog (click)="openDialog()">
            {{ 'catalog.ov.openDialog' | transloco }}
          </button>
          <button type="button" furarButton="danger" data-open-confirm (click)="openConfirm()">
            {{ 'catalog.ov.openConfirm' | transloco }}
          </button>
          <button type="button" furarButton data-open-affected (click)="openAffected()">
            {{ 'catalog.ov.openAffected' | transloco }}
          </button>
          @if (answer() !== null) {
            <p role="status">{{ (answer() ? 'catalog.ov.answerYes' : 'catalog.ov.answerNo') | transloco }}</p>
          }
        </div>
        <div>
          <furar-chip-menu
            [label]="'catalog.ov.statusLabel' | transloco"
            [value]="status()"
            [options]="[
              { value: 'ACTIVE', label: 'catalog.ov.active' | transloco, tone: 'success' },
              { value: 'TEMPORARILY_CLOSED', label: 'catalog.ov.closed' | transloco, tone: 'warn' },
              { value: 'WITHDRAWN', label: 'catalog.ov.withdrawn' | transloco, tone: 'muted' },
            ]"
            (selected)="changeStatus($event)"
          />
        </div>
        <div class="gap-unit flex flex-col items-start" [formGroup]="switches">
          <furar-switch formControlName="online" [label]="'catalog.ov.switchOnline' | transloco" />
          <furar-switch formControlName="slots" [label]="'catalog.ov.switchSlots' | transloco" />
          <furar-switch formControlName="covered" [label]="'catalog.ov.switchDisabled' | transloco" />
        </div>
      </div>
    </furar-card>
  `,
})
export class CatalogOverlaysComponent {
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly transloco = inject(TranslocoService);
  protected readonly answer = signal<boolean | null>(null);
  protected readonly status = signal<SampleStatus>('ACTIVE');
  protected readonly switches = new FormGroup({
    online: new FormControl(true, { nonNullable: true }),
    slots: new FormControl(false, { nonNullable: true }),
    covered: new FormControl({ value: true, disabled: true }, { nonNullable: true }),
  });

  protected openDialog(): void {
    this.dialogs.open(CatalogSampleDialogComponent);
  }

  protected async openConfirm(): Promise<void> {
    const t = (key: string) => this.transloco.translate(key);
    this.answer.set(
      await this.dialogs.confirm({
        heading: t('catalog.ov.confirmTitle'),
        message: t('catalog.ov.confirmText'),
        confirmLabel: t('catalog.ov.confirmAction'),
        busyLabel: t('catalog.ov.confirmBusy'),
        tone: 'danger',
        action: () => new Promise((resolve) => setTimeout(resolve, 600)),
      }),
    );
  }

  /** Primeiro envío: 409 con affected[]; o reenvío con decisions[] vai ben despois dun intre. */
  protected async openAffected(): Promise<void> {
    const outcome = await this.affected.run(
      async (decisions) => {
        if (!decisions) {
          throw new ApiProblem(409, 'facility.affected-bookings', {}, [], undefined, '', { affected: SAMPLE_AFFECTED });
        }
        await new Promise((resolve) => setTimeout(resolve, 600));
        return decisions;
      },
      { summary: this.transloco.translate('catalog.ov.affectedSummary') },
    );
    this.answer.set(outcome.done);
  }

  protected async changeStatus(next: string): Promise<void> {
    const t = (key: string) => this.transloco.translate(key);
    if (next === 'WITHDRAWN') {
      const ok = await this.dialogs.confirm({
        heading: t('catalog.ov.withdrawTitle'),
        message: t('catalog.ov.withdrawText'),
        confirmLabel: t('catalog.ov.withdrawAction'),
        tone: 'danger',
      });
      if (!ok) {
        return;
      }
    }
    this.status.set(next as SampleStatus);
  }
}
