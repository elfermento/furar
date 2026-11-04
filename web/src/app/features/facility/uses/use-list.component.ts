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
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PriceComponent } from '../../../shared/ui/money/price.component';
import { RowListDirective } from '../../../shared/ui/row/row-list.directive';
import { RowComponent } from '../../../shared/ui/row/row.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { TileDirective } from '../../../shared/ui/tile/tile.directive';
import { AffectedBookingsFlow } from '../affected-bookings/affected-bookings.flow';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { StagedElements } from '../detail/staged-elements';
import { StagedFailuresComponent } from '../detail/staged-failures.component';
import type { UseDialogData, UseDialogResult } from './use-dialog.component';
import { UseDialogComponent } from './use-dialog.component';
import { UseTagsComponent } from './use-row.component';
import { priceMuted, pricePer, priceText } from './use-price';
import type { Use, UseBody } from './uses.store';
import { UsesStore } from './uses.store';

/** Como se ve un uso co corpo sen gardar (as partes novas, sen id: a API dállo ao gardar). */
export function previewUse(body: UseBody, base: Use | null, key: string): Use {
  return {
    ...(base ?? { facilityId: '', createdAt: '', updatedAt: '', version: 0 }),
    id: key,
    name: body.name,
    rentalMode: body.rentalMode,
    capacity: body.capacity,
    slotMinutes: body.slotMinutes,
    availability: body.availability,
    blocksOthers: body.blocksOthers ?? false,
    parts: (body.parts ?? []).map((part) => ({ id: part.id ?? '', name: part.name })),
    periods: body.periods ?? [],
    weekdays: body.weekdays ?? base?.weekdays ?? 127,
    price: {
      kind: body.price?.kind ?? 'NONE',
      ...(body.price?.amountCents === undefined ? {} : { amountCents: body.price.amountCents }),
      per: body.rentalMode === 'SEATS' ? 'PERSON' : 'BOOKING',
    },
    rules: body.rules ?? {},
  };
}

/**
 * Sección «Usos» da ficha (D1-22; especificación, bloque 1). Fóra da edición, os usos en filas
 * coas súas etiquetas (`soft`, coma nas tarxetas) e o prezo á dereita (maqueta 54). No modo edición da ficha, cada uso é unha
 * tarxeta que abre o seu modal e, ao final, «Novo uso»; o que se fai nos modais queda pendente
 * (`StagedElements`) ata que a barra garda: entón vai á API (con reservas afectadas, o modal de
 * D1-19) e recárgase a ficha, porque pode mudar o que lle falta para saír de borrador.
 */
@Component({
  selector: 'furar-facility-uses',
  imports: [
    ButtonDirective,
    IconComponent,
    PriceComponent,
    RowComponent,
    RowListDirective,
    StagedFailuresComponent,
    StatusStateComponent,
    TagComponent,
    TileDirective,
    TranslocoPipe,
    UseTagsComponent,
  ],
  providers: [UsesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.uses.';
    <section class="gap-item grid" aria-labelledby="facility-uses-title">
      <h2 id="facility-uses-title" class="text-head font-bold" tabindex="-1">
        {{ 'facility.detail.sections.uses' | transloco }}
      </h2>
      @switch (store.state()) {
        @case ('loading') {
          <furar-status-state mode="skeleton" [rows]="2" [description]="t + 'loading' | transloco" />
        }
        @case ('error') {
          <furar-status-state
            mode="error"
            [headingLevel]="3"
            [heading]="t + 'error.title' | transloco"
            [description]="t + 'error.text' | transloco"
          >
            <button type="button" furarButton (click)="load()">{{ t + 'error.retry' | transloco }}</button>
          </furar-status-state>
        }
        @default {
          @if (session.editing()) {
            <furar-facility-staged-failures [failures]="failures()" />
            <ul class="f-tile-grid" [attr.aria-label]="t + 'list' | transloco">
              @for (use of shown(); track use.id) {
                <li>
                  <button furarTile type="button" data-use-edit [attr.data-use-id]="use.id" (click)="open(use)">
                    <span class="gap-unit flex w-full items-start">
                      <span class="font-bold">{{ use.name }}</span>
                      <furar-price data-use-price [amount]="price(use)" [per]="per(use)" [muted]="muted(use)" />
                    </span>
                    <furar-facility-use-tags [use]="use" />
                    @if (staged.get(use.id)) {
                      <furar-tag tone="info" look="outline">{{ 'facility.detail.edit.pending' | transloco }}</furar-tag>
                    }
                  </button>
                </li>
              }
              <li>
                <button furarTile data-dashed type="button" data-use-add (click)="open(null)">
                  <furar-icon name="plus" />
                  {{ t + 'add' | transloco }}
                </button>
              </li>
            </ul>
          } @else if (store.empty()) {
            <p class="text-ink-soft" data-uses-empty>{{ t + 'empty.title' | transloco }}</p>
          } @else {
            <ul furarRowList [attr.aria-label]="t + 'list' | transloco">
              @for (use of store.uses(); track use.id) {
                <li>
                  <furar-row [label]="use.name">
                    <furar-facility-use-tags [use]="use" />
                    <furar-price rowValue data-use-price [amount]="price(use)" [per]="per(use)" [muted]="muted(use)" />
                  </furar-row>
                </li>
              }
            </ul>
          }
        }
      }
    </section>
  `,
})
export class UseListComponent {
  protected readonly store = inject(UsesStore);
  protected readonly session = inject(EditSession);
  private readonly detail = inject(FacilityDetailStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly staged = new StagedElements<Use, UseBody>(previewUse);
  protected readonly shown = computed(() => this.staged.view(this.store.uses()));
  protected readonly failures = computed(() =>
    this.staged
      .failed()
      .flatMap((change) =>
        change.problem ? [{ name: change.body?.name ?? change.base?.name ?? '', problem: change.problem }] : [],
      ),
  );
  private readonly facilityId = computed(() => this.detail.facility()?.id ?? null);
  private readonly transloco = inject(TranslocoService);
  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly t = (key: string, params?: Record<string, unknown>): string => this.transloco.translate(key, params);

  constructor() {
    effect(() => {
      if (this.facilityId()) untracked(() => void this.load());
    });
    const leave = this.session.register({
      dirty: this.staged.dirty,
      validate: () => true,
      save: () => this.save(),
      reset: () => this.staged.clear(),
    });
    inject(DestroyRef).onDestroy(leave);
  }

  protected price(use: Use): string {
    this.lang();
    return priceText(use, this.t);
  }

  protected per(use: Use): string | null {
    this.lang();
    return pricePer(use, this.t);
  }

  protected muted(use: Use): boolean {
    return priceMuted(use);
  }

  protected load(): Promise<void> {
    const slug = this.municipality.slug();
    const id = this.facilityId();
    return slug && id ? this.store.load(slug, id) : Promise.resolve();
  }

  /** Abre o modal dun uso (ou dun novo) e deixa pendente o que se faga nel. */
  protected async open(use: Use | null): Promise<void> {
    const change = use ? this.staged.get(use.id) : undefined;
    const ref = this.dialogs.open<UseDialogResult, UseDialogData>(UseDialogComponent, {
      data: { use, problem: change?.problem ?? null, facilityName: this.detail.facility()?.name },
    });
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    const base = use ? (this.store.get(use.id) ?? null) : null;
    if (result?.kind === 'apply') {
      const key = this.staged.stage(result.body, base, use?.id);
      this.focus(`[data-use-id="${key}"]`);
    } else if (result?.kind === 'remove' && use) {
      this.staged.remove(use.id, base);
      this.focus('[data-use-add]');
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
        await this.store.archive(id);
        return true;
      },
    });
    // Un uso novo ou quitado pode cambiar o borrador; un fallo aquí non desfai o gardado.
    this.detail.reload().catch(() => undefined);
    if (!ok) throw new Error('uses');
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
