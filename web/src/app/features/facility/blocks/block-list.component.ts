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
import { TranslocoPipe } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { EditSession } from '../../../shared/ui/edit-session/edit-session';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { RowListDirective } from '../../../shared/ui/row/row-list.directive';
import { RowComponent } from '../../../shared/ui/row/row.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { TileDirective } from '../../../shared/ui/tile/tile.directive';
import { AffectedBookingsFlow } from '../affected-bookings/affected-bookings.flow';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { StagedElements } from '../detail/staged-elements';
import { StagedFailuresComponent } from '../detail/staged-failures.component';
import type { BlockDialogData, BlockDialogResult } from './block-dialog.component';
import { BlockDialogComponent } from './block-dialog.component';
import { blockDay } from './block-format';
import { BlockSummaryComponent } from './block-row.component';
import type { Block, BlockBody } from './blocks.store';
import { BlocksStore } from './blocks.store';

/** Como se ve un bloqueo co corpo sen gardar. */
export function previewBlock(body: BlockBody, base: Block | null, key: string): Block {
  return {
    id: key,
    version: base?.version ?? 0,
    date: body.date,
    startMinute: body.startMinute,
    endMinute: body.endMinute,
    reason: body.reason ?? '',
  };
}

/**
 * Sección «Bloqueos» da ficha (D1-24; especificación, bloque 2): horas dun día nas que non se
 * pode reservar (mantemento, un evento). Fóra da edición, filas por data; no modo edición da
 * ficha, tarxetas que abren o seu modal e «Bloqueo». O que se fai nos modais queda pendente ata
 * que a barra garda (con reservas afectadas, o modal de D1-19). Cargando, erro e baleiro.
 */
@Component({
  selector: 'furar-facility-blocks',
  imports: [
    BlockSummaryComponent,
    ButtonDirective,
    IconComponent,
    RowComponent,
    RowListDirective,
    StagedFailuresComponent,
    StatusStateComponent,
    TagComponent,
    TileDirective,
    TranslocoPipe,
  ],
  providers: [BlocksStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.blocks.';
    <section class="gap-item grid" aria-labelledby="facility-blocks-title">
      <h2 id="facility-blocks-title" class="text-head font-bold" tabindex="-1">
        {{ 'facility.detail.sections.blocks' | transloco }}
      </h2>
      @switch (store.state()) {
        @case ('loading') {
          <furar-status-state mode="skeleton" [rows]="1" [description]="t + 'loading' | transloco" />
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
            <ul class="f-tile-grid" [attr.aria-label]="'facility.detail.sections.blocks' | transloco">
              @for (block of shown(); track block.id) {
                <li>
                  <button furarTile type="button" data-block-edit [attr.data-block-id]="block.id" (click)="open(block)">
                    <span class="font-bold">{{ day(block) }}</span>
                    <furar-facility-block-summary [block]="block" />
                    @if (staged.get(block.id)) {
                      <furar-tag tone="info" look="outline">{{ 'facility.detail.edit.pending' | transloco }}</furar-tag>
                    }
                  </button>
                </li>
              }
              <li>
                <button furarTile data-dashed type="button" data-block-add (click)="open(null)">
                  <furar-icon name="plus" />
                  {{ t + 'add' | transloco }}
                </button>
              </li>
            </ul>
          } @else if (store.empty()) {
            <p class="text-ink-soft" data-blocks-empty>{{ t + 'empty' | transloco }}</p>
          } @else {
            <ul furarRowList [attr.aria-label]="'facility.detail.sections.blocks' | transloco">
              @for (block of store.blocks(); track block.id) {
                <li>
                  <furar-row [label]="day(block)">
                    <furar-facility-block-summary [block]="block" />
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
export class BlockListComponent {
  protected readonly store = inject(BlocksStore);
  protected readonly session = inject(EditSession);
  private readonly detail = inject(FacilityDetailStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly dialogs = inject(DialogService);
  private readonly affected = inject(AffectedBookingsFlow);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly staged = new StagedElements<Block, BlockBody>(previewBlock);
  protected readonly shown = computed(() =>
    this.staged.view(this.store.blocks()).sort((a, b) => a.date.localeCompare(b.date) || a.startMinute - b.startMinute),
  );
  protected readonly failures = computed(() =>
    this.staged.failed().flatMap((change) =>
      change.problem
        ? [
            {
              name: blockDay(change.body?.date ?? change.base?.date ?? '', this.language.lang()),
              problem: change.problem,
            },
          ]
        : [],
    ),
  );
  private readonly facilityId = computed(() => this.detail.facility()?.id ?? null);

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

  protected load(): Promise<void> {
    const slug = this.municipality.slug();
    const id = this.facilityId();
    return slug && id ? this.store.load(slug, id) : Promise.resolve();
  }

  protected day(block: Block): string {
    return blockDay(block.date, this.language.lang());
  }

  protected async open(block: Block | null): Promise<void> {
    const change = block ? this.staged.get(block.id) : undefined;
    const others = this.shown().filter((b) => b.id !== block?.id);
    const ref = this.dialogs.open<BlockDialogResult, BlockDialogData>(BlockDialogComponent, {
      data: { block, others, problem: change?.problem ?? null },
    });
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    const base = block ? (this.store.get(block.id) ?? null) : null;
    if (result?.kind === 'apply') {
      const key = this.staged.stage(result.body, base, block?.id);
      this.focus(`[data-block-id="${key}"]`);
    } else if (result?.kind === 'remove' && block) {
      this.staged.remove(block.id, base);
      this.focus('[data-block-add]');
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
        await this.store.remove(id);
        return true;
      },
    });
    if (!ok) throw new Error('blocks');
  }

  private focus(selector: string): void {
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>(selector)?.focus(), {
      injector: this.injector,
    });
  }
}
