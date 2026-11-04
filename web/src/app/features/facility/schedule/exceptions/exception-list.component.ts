// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../../../core/i18n/language.service';
import { DialogService } from '../../../../shared/ui/dialog/dialog.service';
import { EditSession } from '../../../../shared/ui/edit-session/edit-session';
import { IconComponent } from '../../../../shared/ui/icon/icon.component';
import { RowListDirective } from '../../../../shared/ui/row/row-list.directive';
import { RowComponent } from '../../../../shared/ui/row/row.component';
import { TagComponent } from '../../../../shared/ui/tag/tag.component';
import { TileDirective } from '../../../../shared/ui/tile/tile.directive';
import { AffectedBookingsFlow } from '../../affected-bookings/affected-bookings.flow';
import { FacilityDetailStore } from '../../detail/facility-detail.store';
import { StagedElements } from '../../detail/staged-elements';
import { StagedFailuresComponent } from '../../detail/staged-failures.component';
import type { ExceptionDialogData, ExceptionDialogResult } from './exception-dialog.component';
import { ExceptionDialogComponent } from './exception-dialog.component';
import { ExceptionSummaryComponent, exceptionDay } from './exception-row.component';
import type { ExceptionBody, ScheduleException } from './exceptions.store';
import { ExceptionsStore } from './exceptions.store';

/** Como se ve unha excepción co corpo sen gardar. */
export function previewException(body: ExceptionBody, base: ScheduleException | null, key: string): ScheduleException {
  return {
    id: key,
    version: base?.version ?? 0,
    date: body.date,
    open: body.open,
    startMinute: body.open ? (body.startMinute ?? null) : null,
    endMinute: body.open ? (body.endMinute ?? null) : null,
    reason: body.reason ?? '',
  };
}

/**
 * Excepcións do horario (D1-23b; especificación, bloque 2): un día con outro horario ou pechado,
 * por riba da tempada e dos festivos. Fóra da edición, filas por data; no modo edición da ficha,
 * tarxetas que abren o seu modal e «Excepción». O que se fai nos modais queda pendente ata que a
 * barra garda (con reservas afectadas, o modal de D1-19). Cargando e erro son os da sección
 * Horario (o mesmo `GET`).
 */
@Component({
  selector: 'furar-facility-exception-list',
  imports: [
    ExceptionSummaryComponent,
    IconComponent,
    RowComponent,
    RowListDirective,
    StagedFailuresComponent,
    TagComponent,
    TileDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.schedule.exceptions.';
    <div class="gap-item grid" role="group" aria-labelledby="facility-exceptions-title">
      <h3 id="facility-exceptions-title" class="font-bold" tabindex="-1">{{ t + 'title' | transloco }}</h3>
      @if (session.editing()) {
        <furar-facility-staged-failures [failures]="failures()" />
        <ul class="f-tile-grid" [attr.aria-label]="t + 'title' | transloco">
          @for (exception of shown(); track exception.id) {
            <li>
              <button
                furarTile
                type="button"
                data-exception-edit
                [attr.data-exception-id]="exception.id"
                (click)="open(exception)"
              >
                <span class="font-bold">{{ day(exception) }}</span>
                <furar-facility-exception-summary [exception]="exception" />
                @if (staged.get(exception.id)) {
                  <furar-tag tone="info" look="outline">{{ 'facility.detail.edit.pending' | transloco }}</furar-tag>
                }
              </button>
            </li>
          }
          <li>
            <button furarTile data-dashed type="button" data-exception-add (click)="open(null)">
              <furar-icon name="plus" />
              {{ t + 'add' | transloco }}
            </button>
          </li>
        </ul>
      } @else if (store.empty()) {
        <p class="text-ink-soft" data-exceptions-empty>{{ t + 'empty' | transloco }}</p>
      } @else {
        <ul furarRowList [attr.aria-label]="t + 'title' | transloco">
          @for (exception of store.exceptions(); track exception.id) {
            <li>
              <furar-row [label]="day(exception)">
                <furar-facility-exception-summary [exception]="exception" />
              </furar-row>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class ExceptionListComponent {
  protected readonly store = inject(ExceptionsStore);
  protected readonly session = inject(EditSession);
  private readonly detail = inject(FacilityDetailStore);
  private readonly language = inject(LanguageService);
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly staged = new StagedElements<ScheduleException, ExceptionBody>(previewException);
  protected readonly shown = computed(() =>
    this.staged.view(this.store.exceptions()).sort((a, b) => a.date.localeCompare(b.date)),
  );
  protected readonly failures = computed(() =>
    this.staged.failed().flatMap((change) =>
      change.problem
        ? [
            {
              name: exceptionDay(change.body?.date ?? change.base?.date ?? '', this.language.lang()),
              problem: change.problem,
            },
          ]
        : [],
    ),
  );

  constructor() {
    const leave = this.session.register({
      dirty: this.staged.dirty,
      validate: () => true,
      save: () => this.save(),
      reset: () => this.staged.clear(),
    });
    inject(DestroyRef).onDestroy(leave);
  }

  protected day(exception: ScheduleException): string {
    return exceptionDay(exception.date, this.language.lang());
  }

  protected async open(exception: ScheduleException | null): Promise<void> {
    const change = exception ? this.staged.get(exception.id) : undefined;
    const taken = new Set(this.shown().flatMap((e) => (e.id === exception?.id ? [] : [e.date])));
    const ref = this.dialogs.open<ExceptionDialogResult, ExceptionDialogData>(ExceptionDialogComponent, {
      data: {
        exception,
        taken,
        today: this.store.today(),
        problem: change?.problem ?? null,
        holiday: (date) => this.store.holiday(date),
      },
    });
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    const base = exception ? (this.store.exceptions().find((e) => e.id === exception.id) ?? null) : null;
    if (result?.kind === 'apply') {
      const key = this.staged.stage(result.body, base, exception?.id);
      this.focus(`[data-exception-id="${key}"]`);
    } else if (result?.kind === 'remove' && exception) {
      this.staged.remove(exception.id, base);
      this.focus('[data-exception-add]');
    }
  }

  private async save(): Promise<void> {
    const ok = await this.staged.flush({
      create: async (body) => {
        const outcome = await this.affected.run((decisions) => this.store.create(body, decisions));
        return outcome.done ? outcome.value : null;
      },
      update: async (id, body) => {
        const outcome = await this.affected.run((decisions) => this.store.update(id, body, decisions));
        return outcome.done ? outcome.value : null;
      },
      remove: async (id) => {
        const outcome = await this.affected.run((decisions) => this.store.remove(id, decisions));
        return outcome.done;
      },
    });
    this.detail.reload().catch(() => undefined);
    if (!ok) throw new Error('exceptions');
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
