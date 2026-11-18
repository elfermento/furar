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
import { NARROW_QUERY, deckFits, pinnedDeck, sectionProgress, stepDelay, stepToward, targetStep } from './step-deck';

/**
 * «Así de fácil»: franxa da cor principal con tres pasos que entran de lado e se apilan ao baixar
 * (sección fixa), tamén no móbil, onde a baralla ten o alto da tarxeta máis alta e vai entre a
 * barra superior e a inferior. O scroll só escolle o paso (un terzo da sección cada un,
 * `targetStep`); o cambio de paso é unha transición de duración fixa (`--f-step-move`, home.css)
 * e cada paso queda polo menos un tempo á vista (`stepDelay`), así que un scroll brusco non fai
 * pasar as tarxetas de golpe (`DeckStepper`, que pon `data-active` na sección). Unha debaixo
 * doutra e sen animación: con movemento reducido, nunha pantalla ancha e baixa (`STATIC_QUERY`)
 * e no móbil se a baralla non cabe fixa (`deckFits`: `data-deck="static"`, que se volve mirar ao
 * cambiar o alto da pantalla ou das tarxetas). As «capturas» (`aria-hidden`) levan os datos e as cores de cada concello: as súas
 * instalacións coas fotos (paso 1), a tarxeta de reservar dunha delas (paso 2) e un QR de verdade
 * dunha reserva de exemplo (paso 3). Sen instalacións, cadros baleiros no seu sitio.
 */
/**
 * O paso á vista da baralla (`data-active` na sección): segue o scroll dun paso en dun e con
 * tempo mínimo por paso. A primeira vez, e se a baralla non vai fixa, vai directo, sen agardar.
 */
export class DeckStepper {
  private current = -1;
  private changedAt = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(
    private readonly section: HTMLElement,
    private readonly view: Window,
    private readonly now: () => number = () => Date.now(),
  ) {}

  /** Volve mirar onde vai o scroll (ao baixar, ao cambiar o alto). */
  update(): void {
    const count = this.section.querySelectorAll('.f-step').length;
    const box = this.section.getBoundingClientRect();
    const target = targetStep(sectionProgress(box.top, box.height, this.view.innerHeight), count);
    const animated = pinnedDeck(this.view) && this.section.dataset['deck'] !== 'static';
    if (this.current < 0 || !animated) {
      this.show(target);
      return;
    }
    this.schedule(target);
  }

  stop(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
  }

  private schedule(target: number): void {
    this.stop();
    const delay = stepDelay(this.current, target, this.now() - this.changedAt);
    if (delay === Infinity) return;
    this.timer = setTimeout(() => {
      this.timer = null;
      this.show(stepToward(this.current, target));
      // O scroll pode seguir: o seguinte paso, co seu tempo.
      this.update();
    }, delay);
  }

  private show(step: number): void {
    this.stop();
    this.current = step;
    this.changedAt = this.now();
    this.section.dataset['active'] = String(step);
  }
}

/**
 * No móbil, se a baralla non cabe fixa no alto visible (entre a barra superior e a inferior),
 * marca a sección con `data-deck="static"` (home.css: unha debaixo doutra); se cabe, quítao.
 */
export function fitDeck(section: HTMLElement, view: Window): void {
  const title = section.querySelector<HTMLElement>('.f-steps__title');
  const cards = [...section.querySelectorAll<HTMLElement>('.f-step')];
  let fits = true;
  if (title && (view.matchMedia?.(NARROW_QUERY).matches ?? false)) {
    const root = view.getComputedStyle(view.document.documentElement);
    const topbar = parseFloat(root.getPropertyValue('--f-topbar-h')) || 0;
    const bottom = view.document.querySelector<HTMLElement>('.f-bottomnav')?.offsetHeight ?? 0;
    const titleBox = title.offsetHeight + (parseFloat(view.getComputedStyle(title).marginBottom) || 0);
    fits = deckFits(
      view.innerHeight - topbar - bottom,
      titleBox,
      cards.map((card) => card.offsetHeight),
    );
  }
  if (fits) delete section.dataset['deck'];
  else section.dataset['deck'] = 'static';
}

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
      if (!view) {
        return;
      }
      const section = this.section().nativeElement;
      const stepper = new DeckStepper(section, view);
      let frame = 0;
      const onScroll = () => {
        cancelAnimationFrame(frame);
        frame = requestAnimationFrame(() => stepper.update());
      };
      // Se a baralla cabe fixa: ao comezo, ao cambiar o alto da pantalla e o das tarxetas
      // (fotos, idioma, tamaño do texto).
      let fitFrame = 0;
      const refit = () => {
        cancelAnimationFrame(fitFrame);
        fitFrame = requestAnimationFrame(() => {
          fitDeck(section, view);
          stepper.update();
        });
      };
      fitDeck(section, view);
      stepper.update();
      view.addEventListener('scroll', onScroll, { passive: true });
      view.addEventListener('resize', refit, { passive: true });
      const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(refit);
      section.querySelectorAll('.f-step').forEach((card) => observer?.observe(card));
      destroyRef.onDestroy(() => {
        view.removeEventListener('scroll', onScroll);
        view.removeEventListener('resize', refit);
        observer?.disconnect();
        cancelAnimationFrame(frame);
        cancelAnimationFrame(fitFrame);
        stepper.stop();
      });
    });
  }
}
