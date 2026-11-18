// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SessionService } from '../../../core/auth/session.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { DependentsStore } from '../../../shared/dependents/dependents.store';
import { SELF } from '../../../shared/dependents/participants';
import {
  activityDates,
  ageText,
  eventWhen,
  inLang,
  isScheduled,
  longDay,
  priceText,
  scheduleWhen,
} from '../../../shared/events/event-format';
import type { MyEventRegistrationResponse, PublicEventResponse } from '../../../shared/events/public-events.store';
import { PublicEvents } from '../../../shared/events/public-events.store';
import { RegistrationBandComponent } from '../../../shared/events/registration-band.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { DisclosureComponent } from '../../../shared/ui/disclosure/disclosure.component';
import { focusPageHeadingIfLost } from '../../../shared/ui/focus-page-heading';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { SeatsChipComponent } from '../../../shared/ui/seats-chip/seats-chip.component';
import { StatusBandComponent } from '../../../shared/ui/status-band/status-band.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { EventJoinComponent, anyoneLeft } from './event-join.component';

/** O que se pode facer agora para apuntar a alguén. */
export type EventAction = 'join' | 'ask' | 'wait' | 'closed' | 'none';

/**
 * A acción de apuntarse segundo o evento: con lista de espera (sen «Con aprobación»), «Avísame se
 * queda sitio»; pechado, a frase; se non, «Apuntarme» ou «Pedir praza» (con aprobación, tamén
 * completo: a solicitude queda pendente). `none`: xa non queda ninguén da conta por apuntar.
 */
export function eventAction(event: PublicEventResponse, anyoneLeft = true): EventAction {
  if (!anyoneLeft) return 'none';
  if (!event.approval && (event.waitlist || event.free === 0)) return 'wait';
  if (!event.open) return 'closed';
  return event.approval ? 'ask' : 'join';
}

/**
 * Ficha dun evento ou dunha actividade do concello (ADR-0038, ADR-0041): o patrón da ficha dunha
 * instalación (título, imaxe e descrición; tarxeta lateral con Cando, Onde, Idade, Prazas, Prezo e
 * «Apuntarse ata»). Enriba da tarxeta, unha banda por inscrición da conta (a propia e as das
 * persoas a cargo: `furar-registration-band`). Ao pé da tarxeta, apuntarse (`furar-event-join`:
 * para quen, a data de nacemento e «Apuntarme» ou «Pedir praza»). Sen prazas e sen aprobación, iso
 * mesmo vai nunha banda «Non quedan prazas» con «Avísame se queda sitio». A fila «Prazas» nunca
 * contradí a banda: con prazas para a lista de espera, «1 libre, para a lista de espera».
 */
@Component({
  selector: 'furar-event-page',
  imports: [
    ButtonDirective,
    DisclosureComponent,
    EventJoinComponent,
    IconComponent,
    RegistrationBandComponent,
    RouterLink,
    SeatsChipComponent,
    StatusBandComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './event.page.html',
})
export class EventPage {
  /** Parámetro `:id` da ruta. */
  readonly id = input.required<string>();

  private readonly store = inject(PublicEvents);
  private readonly dependents = inject(DependentsStore);
  private readonly session = inject(SessionService);
  private readonly toasts = inject(ToastService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly event = signal<PublicEventResponse | null>(null);
  /** As inscricións da conta neste evento (a propia e as das persoas a cargo). */
  protected readonly registrations = signal<MyEventRegistrationResponse[]>([]);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly notFound = signal(false);
  /** A banda que está gardando (a súa clave). */
  protected readonly busyBand = signal<string | null>(null);

  protected readonly lang = this.language.lang;
  protected readonly authenticated = this.session.authenticated;

  private readonly t = (key: string, params?: Record<string, unknown>) => {
    this.langChanges();
    return this.transloco.translate(key, params);
  };

  protected readonly name = computed(() => {
    const event = this.event();
    return event ? inLang(event.name, this.lang()) : '';
  });
  protected readonly description = computed(() => {
    const text = this.event()?.description;
    return text ? inLang(text, this.lang()) : '';
  });
  /** «Cando»: o día e a hora ou, nunha actividade, o horario e as datas. */
  protected readonly when = computed<string[]>(() => {
    const event = this.event();
    if (!event) return [];
    const lang = this.lang();
    if (isScheduled(event)) return [scheduleWhen(event.schedule, lang, this.t), activityDates(event, lang, this.t)];
    return [eventWhen(event.startsAt, lang)];
  });
  protected readonly age = computed(() => {
    const event = this.event();
    return event ? ageText(event, this.t) : '';
  });
  protected readonly deadline = computed(() => {
    const at = this.event()?.registrationDeadline;
    if (!at) return '';
    const { date, time } = inMadrid(at);
    return `${longDay(date, this.lang())} · ${time}`;
  });
  protected readonly price = computed(() => {
    const event = this.event();
    return event ? priceText(event, this.t) : '';
  });

  protected readonly action = computed<EventAction | null>(() => {
    const event = this.event();
    if (!event) return null;
    return eventAction(event, !this.authenticated() || anyoneLeft(this.registrations(), this.dependents.items()));
  });

  constructor() {
    effect(() => {
      const id = this.id();
      const slug = this.slug();
      this.session.authenticated();
      if (slug) untracked(() => void this.load(slug, id));
    });
  }

  protected retry(): void {
    void this.load(this.slug(), this.id());
  }

  protected bandKey(item: MyEventRegistrationResponse): string {
    return item.dependent?.id ?? SELF;
  }

  protected async leave(item: MyEventRegistrationResponse): Promise<void> {
    const waiting = item.status === 'WAITING' || item.status === 'NOTIFIED';
    await this.runBand(item, 'event.public.leaveError', async () => {
      await this.store.leave(this.slug(), this.id(), item.dependent?.id);
      this.toasts.show(waiting ? 'event.band.leftListToast' : 'event.public.leftToast');
    });
    focusPageHeadingIfLost(this.injector);
  }

  protected async confirm(item: MyEventRegistrationResponse): Promise<void> {
    await this.runBand(item, 'event.band.confirmError', async () => {
      await this.store.confirm(this.slug(), this.id(), item.dependent?.id);
      this.toasts.show('event.public.joinedToast');
    });
  }

  protected alt(event: PublicEventResponse): string {
    return inLang(event.cover.alt, this.lang());
  }

  private async runBand(item: MyEventRegistrationResponse, errorKey: string, work: () => Promise<void>): Promise<void> {
    if (this.busyBand()) return;
    this.busyBand.set(this.bandKey(item));
    try {
      await work();
    } catch (error) {
      this.toasts.error(errorKey, error);
    } finally {
      // Sexa como sexa, a ficha amosa o estado de agora (unha praza que outra persoa confirmou).
      await this.refresh().catch(() => undefined);
      this.busyBand.set(null);
    }
  }

  /** Volver pedir o evento e as inscricións (despois de apuntarse). */
  protected reload(): void {
    void this.refresh().catch(() => undefined);
  }

  private async refresh(): Promise<void> {
    const slug = this.slug();
    const id = this.id();
    const [event, registrations] = await Promise.all([this.store.get(slug, id), this.store.mineAll(slug, id)]);
    this.event.set(event);
    this.registrations.set(registrations);
  }

  private async load(slug: string, id: string): Promise<void> {
    this.state.set(this.event() ? 'ready' : 'loading');
    try {
      if (this.session.authenticated()) {
        void this.dependents.load();
        const [event, registrations] = await Promise.all([this.store.get(slug, id), this.store.mineAll(slug, id)]);
        this.event.set(event);
        this.registrations.set(registrations);
      } else {
        this.event.set(await this.store.get(slug, id));
        this.registrations.set([]);
      }
      this.state.set('ready');
    } catch (error) {
      this.notFound.set((error as ApiProblem | undefined)?.status === 404);
      this.state.set('error');
    }
  }
}
