// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { CalloutComponent } from '../../shared/ui/callout/callout.component';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import type { RadioCardOption } from '../../shared/ui/radio-cards/radio-cards.component';
import { RadioCardsComponent } from '../../shared/ui/radio-cards/radio-cards.component';
import type { DatasetKey, DownloadFormat } from './open-data';
import { daysBetween, formatSize } from './open-data';
import { OpenDataStore } from './open-data.store';

export interface OpenDataDialogData {
  /** O concello, ou `null` na páxina de todos. */
  readonly slug: string | null;
  readonly dataset: DatasetKey;
  readonly formats: readonly DownloadFormat[];
  /** Subtítulo xa traducido («Ocupación · do 01/10 ao 31/10 · Narón»). */
  readonly subtitle: string;
  readonly lang: string;
  /** Na ocupación: as datas e o tamaño medio dun día en cada formato. */
  readonly range: { from: IsoDate; to: IsoDate } | null;
  readonly bytesPerDay: { csv: number; json: number } | null;
  /** Ao pechar despois dun 404 (o concello acaba de apagar): a páxina recárgase. */
  readonly onGone?: () => void;
}

/** Como pechou o diálogo: descargado, cancelado ou o concello xa non publica (recargar). */
export type OpenDataDialogResult = 'done' | 'gone' | undefined;

let nextId = 0;

/**
 * «Descargar»: o formato en tarxetas (CSV marcado), cunha liña de para que serve cada
 * un e, na ocupación, o tamaño aproximado. Mentres descarga, o botón espera; un erro queda no
 * diálogo (404, 429, 5xx ou sen rede) e o diálogo segue aberto. Pecha con Esc e picando fóra.
 */
@Component({
  selector: 'furar-open-data-dialog',
  imports: [ButtonDirective, CalloutComponent, DialogComponent, IconComponent, RadioCardsComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'openData.dialog.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco" [subheading]="data.subtitle" [busy]="busy()">
      <div class="f-open-data-dialog">
        @if (error(); as key) {
          <furar-callout tone="danger" live="alert" data-download-error>{{ t + key | transloco }}</furar-callout>
        }
        <p class="sr-only" [id]="labelId">{{ t + 'format' | transloco }}</p>
        <furar-radio-cards [options]="options()" [labelledBy]="labelId" [(value)]="format" />
        @if (size(); as text) {
          <p class="f-open-data-dialog__size" data-download-size>{{ t + 'size' | transloco: { size: text } }}</p>
        }
      </div>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-download-cancel (click)="close()">
          {{ t + 'cancel' | transloco }}
        </button>
        <button type="button" furarButton="primary" data-download-confirm [busy]="busy()" (click)="download()">
          <furar-icon name="download" />
          {{ 'openData.download' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class OpenDataDialogComponent {
  protected readonly data = inject<OpenDataDialogData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<OpenDataDialogResult>>(DialogRef);
  private readonly store = inject(OpenDataStore);
  private readonly transloco = inject(TranslocoService);

  protected readonly labelId = `furar-open-data-format-${nextId++}`;
  protected readonly format = signal<DownloadFormat>('csv');
  protected readonly busy = signal(false);
  protected readonly error = signal<'gone' | 'busy' | 'failed' | null>(null);

  protected readonly options = computed<RadioCardOption<DownloadFormat>[]>(() =>
    this.data.formats.map((format) => ({
      value: format,
      label: format === 'geojson' ? 'GeoJSON' : format.toUpperCase(),
      items: [],
      description: this.transloco.translate(`openData.format.${format}`),
    })),
  );

  /** «≈ 4 MB»: días × bytes por día do formato escollido (o GeoJSON, coma o JSON). */
  protected readonly size = computed(() => {
    const { range, bytesPerDay, lang } = this.data;
    if (!range || !bytesPerDay) return null;
    const perDay = this.format() === 'csv' ? bytesPerDay.csv : bytesPerDay.json;
    if (!perDay) return null;
    return formatSize(perDay * daysBetween(range.from, range.to), lang);
  });

  constructor() {
    // Ao pechar por calquera vía (botón, Esc ou picando fóra) despois dun 404, recárgase.
    this.ref.closed.subscribe(() => {
      if (this.error() === 'gone') this.data.onGone?.();
    });
  }

  protected close(): void {
    this.ref.close(this.error() === 'gone' ? 'gone' : undefined);
  }

  protected async download(): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.store.download(this.data.slug, {
        dataset: this.data.dataset,
        format: this.format(),
        lang: this.data.lang,
        range: this.data.range,
      });
      this.ref.close('done');
    } catch (error) {
      const status = asProblem(error).status;
      this.error.set(status === 404 ? 'gone' : status === 429 ? 'busy' : 'failed');
      this.busy.set(false);
    }
  }
}
