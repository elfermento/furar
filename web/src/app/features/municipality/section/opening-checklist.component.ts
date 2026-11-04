// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  booleanAttribute,
  computed,
  inject,
  input,
  runInInjectionContext,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoService } from '@jsverse/transloco';
import { loadManagementStyles } from '../../../core/layout/management-styles';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { missingOpeningLegal } from '../../../core/municipality/opening';
import type { ChecklistItem } from '../../../shared/ui/checklist/checklist.component';
import { ChecklistComponent } from '../../../shared/ui/checklist/checklist.component';
import { openingChecklistItems } from '../opening-items';
import { OpeningChecklistStore } from './opening-checklist.store';
import { MunicipalitySection } from './section';

/**
 * «Para abrir as reservas» (maqueta 48), enriba de «Xeral»: os datos que usan os textos legais
 * (os cinco de `missingLegal`, gardados) e polo menos unha instalación activa que se vexa no
 * público (sen nada que lle falte para publicarse). Só avisa, non bloquea nada; co todo feito,
 * desaparece. A pestana só a ven o xestor e a plataforma (`configGuard`), e cárgase á parte
 * (`@defer`) para non engordar a pestana. Se o listado de instalacións falla, non se mostra.
 *
 * `compact` (maqueta 56): a mesma lista pregada nunha fila, na portada do concello, para quen o
 * configura (quen a pon decide quen a ve). Alí non hai sección: os datos legais son os gardados, e
 * a folla da xestión (onde van os estilos da lista) pídese antes de pintala.
 */
@Component({
  selector: 'furar-opening-checklist',
  imports: [ChecklistComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items(); as list) {
      <furar-checklist [heading]="heading()" [items]="list" [compact]="compact()" data-opening-checklist />
    }
  `,
})
export class OpeningChecklistComponent implements OnInit {
  private readonly store = inject(OpeningChecklistStore);
  private readonly current = inject(CurrentMunicipality);
  /** Na sección «Concello», os datos legais do formulario aberto; fóra (a portada), non hai. */
  private readonly section = inject(MunicipalitySection, { optional: true });
  private readonly injector = inject(Injector);

  /** Pregada nunha fila co seguinte paso (a portada). */
  readonly compact = input(false, { transform: booleanAttribute });
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());

  /** Se hai unha instalación activa e publicada; `null` mentres carga ou se fallou. */
  private readonly published = signal<boolean | null>(null);

  protected readonly heading = computed(() => {
    this.translation();
    return this.t('section.opening.title');
  });
  protected readonly items = computed<ChecklistItem[] | null>(() => {
    this.translation();
    const published = this.published();
    const slug = this.current.slug();
    if (published === null || !slug) return null;
    const legal = this.section
      ? this.section.missing().length === 0
      : missingOpeningLegal(this.current.value()?.legal).length === 0;
    return openingChecklistItems(slug, { legal, facility: published }, (key) => this.t(key));
  });

  ngOnInit(): void {
    void this.load();
  }

  private t(key: string): string {
    return this.transloco.translate(key);
  }

  private async load(): Promise<void> {
    const slug = this.current.slug();
    if (!slug) return;
    try {
      const styles = this.compact() ? runInInjectionContext(this.injector, loadManagementStyles) : true;
      const [published] = await Promise.all([this.store.hasPublishedFacility(slug), styles]);
      this.published.set(published);
    } catch {
      // Só avisa: sen o listado, mellor non dicir nada ca dicir algo mal.
    }
  }
}
