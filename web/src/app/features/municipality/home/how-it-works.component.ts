// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  afterNextRender,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { PublicFacilityResponse } from './home.store';
import { LanguageService } from '../../../core/i18n/language.service';
import { sportName } from '../../../shared/facility-search/facility-search';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { QR_CODE, QR_PATH, QR_SIZE } from './qr-sample';
import { deckPoses, sectionProgress } from './step-deck';

/**
 * «Así de fácil»: franxa da cor principal con tres pasos que entran de lado e se apilan ao baixar
 * (sección fixa). Con `animation-timeline` vai só en CSS (home.css); sen el, un respaldo pequeno
 * en JS pon as mesmas variables `--t` e `--u`. Con movemento reducido, unha debaixo doutra e sen
 * animación. As «capturas» (`aria-hidden`) levan os datos e as cores de cada concello: as súas
 * instalacións coas fotos (paso 1), a tarxeta de reservar dunha delas (paso 2) e un QR de verdade
 * dunha reserva de exemplo (paso 3). Sen instalacións, cadros baleiros no seu sitio.
 */
@Component({
  selector: 'furar-how-it-works',
  imports: [TranslocoPipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './how-it-works.component.html',
})
export class HowItWorksComponent {
  private readonly document = inject(DOCUMENT);
  private readonly section = viewChild.required<ElementRef<HTMLElement>>('section');

  /** Nome do concello (para as capturas); nulo na portada sen concello («Reserva a túa pista»). */
  readonly municipality = input.required<string | null>();
  /** Enderezo da web, sen protocolo («localhost:8080/naron»), para a barra das capturas. */
  readonly address = input.required<string>();

  /** Instalacións publicadas do concello (as da portada). */
  readonly facilities = input<readonly PublicFacilityResponse[]>([]);

  protected readonly lang = inject(LanguageService).lang;
  protected readonly qr = { code: QR_CODE, path: QR_PATH, viewBox: `0 0 ${QR_SIZE} ${QR_SIZE}` };
  /** Tres instalacións para a portada en pequeno; tres ocos se aínda non hai. */
  protected readonly shown = computed(() => this.facilities().slice(0, 3));
  /** A da tarxeta de reservar e do QR: a primeira con deporte, se a hai. */
  protected readonly sample = computed(
    () => this.facilities().find((f) => f.sports.length) ?? this.facilities()[0] ?? null,
  );
  protected readonly sport = computed(() => {
    const first = this.sample()?.sports[0];
    return first ? sportName(first, this.lang()) : '';
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const view = this.document.defaultView;
      const supported = typeof CSS !== 'undefined' && CSS.supports?.('animation-timeline: view()');
      if (!view || supported || view.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
        return;
      }
      let frame = 0;
      const update = () => {
        const section = this.section().nativeElement;
        const box = section.getBoundingClientRect();
        const cards = [...section.querySelectorAll<HTMLElement>('.f-step')];
        deckPoses(sectionProgress(box.top, box.height, view.innerHeight), cards.length).forEach((pose, i) => {
          cards[i]?.style.setProperty('--t', String(pose.t));
          cards[i]?.style.setProperty('--u', String(pose.u));
        });
      };
      const onScroll = () => {
        cancelAnimationFrame(frame);
        frame = requestAnimationFrame(update);
      };
      view.addEventListener('scroll', onScroll, { passive: true });
      update();
      destroyRef.onDestroy(() => {
        view.removeEventListener('scroll', onScroll);
        cancelAnimationFrame(frame);
      });
    });
  }
}
