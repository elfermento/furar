// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { ButtonDirective } from '../ui/button/button.directive';
import type { IsoDate } from '../ui/calendar/local-date';
import { ClearButtonComponent } from '../ui/clear-button/clear-button.component';
import { IconComponent } from '../ui/icon/icon.component';
import { dayLabel } from './facility-search';
import type { SearchType } from './search-filters-dialog.component';

/** O buscado nunha portada: o que resume a pílula. */
export interface SearchSummary {
  /** O texto buscado ou o concello escollido. */
  readonly where?: string | null;
  readonly date: IsoDate;
  readonly today: IsoDate;
  /** Os nomes dos deportes marcados (e «Cuberta»). */
  readonly sports: readonly string[];
  readonly kind: SearchType;
  /** Hai «Zona do mapa». */
  readonly zone?: boolean;
}

/**
 * Móbil, cun filtro posto (sen a cabeceira): no alto da portada, unha pílula «primary» co resumo do
 * buscado («Pádel · Mañá», cortado con «…» se non cabe) que volve abrir a folla de busca
 * (`opened`), e un «✕» que quita todos os filtros (`cleared`): volven a cabeceira e os carruseis.
 */
@Component({
  selector: 'furar-search-summary',
  imports: [ButtonDirective, ClearButtonComponent, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-search-summary' },
  template: `
    <button
      type="button"
      furarButton="primary"
      #pill
      class="f-search-summary__pill"
      data-search-summary
      [attr.aria-label]="'home.search.summary' | transloco: { summary: text() }"
      (click)="opened.emit($event)"
    >
      <furar-icon name="search" />
      <span class="f-search-summary__text">{{ text() }}</span>
    </button>
    <furar-clear-button data-clear="all" [label]="'home.search.clear.all' | transloco" (pressed)="cleared.emit()" />
  `,
})
export class SearchSummaryComponent {
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly lang = toSignal(this.transloco.langChanges$);
  private readonly injector = inject(Injector);
  private readonly pill = viewChild.required<ElementRef<HTMLButtonElement>>('pill');

  readonly summary = input.required<SearchSummary>();
  /** Picouse a pílula: quen a usa abre a folla de busca cos valores postos. */
  readonly opened = output<Event>();
  /** O «✕»: quen a usa quita todos os filtros. */
  readonly cleared = output();

  /**
   * O foco á pílula en canto se pinta: tras buscar na folla, a cabeceira (co «Buscar» onde volvería
   * o foco) escóndese e queda ela.
   */
  focusSoon(): void {
    afterNextRender(() => this.pill().nativeElement.focus(), { injector: this.injector });
  }

  /** «Pádel · Mañá»: o que se buscou, o día (se non é hoxe), o tipo, os deportes e a zona. */
  protected readonly text = computed(() => {
    this.lang();
    const t = (key: string) => this.transloco.translate(key);
    const { where, date, today, sports, kind, zone } = this.summary();
    const day =
      date === today
        ? null
        : dayLabel(date, today, this.language.lang(), {
            today: t('home.search.today'),
            tomorrow: t('home.search.tomorrow'),
          });
    const type = kind === 'all' ? null : t(`facility.catalog.filters.kinds.${kind}`);
    return [where, ...sports, day, type, zone ? t('facility.catalog.zone') : null].filter(Boolean).join(' · ');
  });
}
