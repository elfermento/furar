// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SessionService } from '../../../core/auth/session.service';
import { DeploymentSettings } from '../../../core/deployment/deployment-settings';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  openMatchCountsText,
  openMatchRule,
  openMatchTitle,
  openMatchUseDetail,
  openMatchWhenParts,
} from '../../../shared/open-matches/open-match-format';
import { OpenMatches } from '../../../shared/open-matches/open-matches.store';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { IconComponent, registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { MapComponent } from '../../../shared/ui/map/map.component';
import { BookingCancel } from '../mine/booking-cancel';
import { canChange } from '../mine/my-bookings';
import { MyBookingsStore } from '../mine/my-bookings.store';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import { BookingMatchActions } from './booking-match-actions';
import type { MapPoint } from '../../../shared/ui/map/map-point';
import type { ShownMatch } from './open-match';
import { OpenMatchOrganizerActionsComponent } from './open-match-organizer-actions.component';
import { OpenMatchPeopleComponent } from './open-match-people.component';
import { clearJoinIntent, saveJoinIntent, tabStorage, takeJoinIntent } from './join-intent';
import type { OpenMatchRequest } from './open-match-requests.component';
import { OpenMatchRequestsComponent } from './open-match-requests.component';
import { asMine, matchAction } from './open-match';

registerSymbols(LAZY_SYMBOLS);

/**
 * Páxina dunha partida (`/:concello/partidas/:id`, ADR-0040): a estrutura da ficha da
 * instalación. O título, a imaxe, as solicitudes (só quen organiza: unha tarxeta por solicitude,
 * «Rexeitar» e «Aceptar» cheos, á dereita), a descrición (uso, nivel, «Con aprobación» e o texto,
 * sempre texto plano) e as persoas que van (só quen está dentro). Ao lado, a tarxeta: a instalación
 * coa súa ligazón e enderezo, cando, as prazas («4 de 10 · faltan 6» e a regra do prazo) e quen
 * organiza; ao pé, unha soa acción: «Unirme» ou «Pedir unirme» (sen sesión, leva a entrar e volve
 * aquí), «Saír» ou «Retirar a solicitude», ou o estado. Quen organiza: «Deixar de buscar» e
 * «Cancelar a reserva» (se aínda se pode), cada un co seu diálogo; pechada, o estado e «Cancelar a
 * reserva». Mentres se mostra, `noindex`.
 */
@Component({
  selector: 'furar-open-match-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    MapComponent,
    OpenMatchOrganizerActionsComponent,
    OpenMatchPeopleComponent,
    OpenMatchRequestsComponent,
    ProblemAlertComponent,
    RouterLink,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [MyBookingsStore, BookingMatchActions, BookingCancel],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './open-match.page.html',
})
export class OpenMatchPage {
  /** Parámetro `:id` da ruta. */
  readonly id = input.required<string>();

  private readonly store = inject(OpenMatches);
  private readonly bookings = inject(MyBookingsStore);
  private readonly matchActions = inject(BookingMatchActions);
  private readonly cancelling = inject(BookingCancel);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly match = signal<ShownMatch | null>(null);
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly notFound = signal(false);
  /** A acción en curso: `join`, `leave` ou o id da solicitude máis `accept`/`reject`. */
  protected readonly busy = signal<string | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** «Como chegar»: o punto da ficha pública da instalación (sen mapa no despregue, nada). */
  protected readonly mapEnabled = inject(DeploymentSettings).mapEnabled;
  protected readonly point = signal<MapPoint | null>(null);

  protected readonly mine = computed(() => asMine(this.match()));
  /** A reserva da partida, se a organiza a persoa: «Cancelar a reserva» só se aínda se pode. */
  private readonly booking = signal<MyBookingResponse | null>(null);
  protected readonly canCancel = computed(() => {
    const booking = this.booking();
    return !!booking && canChange(booking);
  });
  /** O que pinta a páxina, xa calculado (o nome, cando, as prazas, a regra, os chips e a acción). */
  protected readonly view = computed(() => {
    this.langChanges();
    const match = this.match();
    if (!match) return null;
    const lang = this.language.lang();
    return {
      match,
      action: matchAction(match),
      title: openMatchTitle(match),
      when: openMatchWhenParts(match, lang),
      counts: openMatchCountsText(match, this.t),
      rule: openMatchRule(match, lang, this.t),
      // Os chips da descrición: o uso (e a parte), o nivel e «Con aprobación».
      tags: [
        openMatchUseDetail(match),
        match.partName,
        match.level ? this.t(`openMatch.level.${match.level}`) : null,
        match.approval ? this.t('openMatch.approval') : null,
      ].filter((tag): tag is string => !!tag),
    };
  });
  protected readonly requests = computed(() => {
    const mine = this.mine();
    return mine?.me.role === 'ORGANIZER' ? mine.requests : [];
  });

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
  private readonly window = inject(DOCUMENT).defaultView;
  private readonly tab = () => tabStorage(this.window);

  constructor() {
    // Unha quedada non vai aos buscadores (ADR-0040, punto 9).
    const meta = inject(Meta);
    meta.updateTag({ name: 'robots', content: 'noindex' });
    inject(DestroyRef).onDestroy(() => meta.removeTag('name="robots"'));
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

  protected async join(): Promise<void> {
    if (!this.session.authenticated()) {
      // Ao volver xa con sesión, únese soa (join-intent.ts).
      saveJoinIntent(this.tab(), { slug: this.slug(), id: this.id(), at: Date.now() });
      await this.session.login(this.router.url);
      return;
    }
    await this.run('join', async () => {
      const mine = await this.store.join(this.slug(), this.id());
      this.match.set(mine);
      this.toasts.show(mine.me.role === 'PENDING' ? 'openMatch.page.askedToast' : 'openMatch.page.joinedToast');
    });
  }

  /** «Saír» ou «Retirar a solicitude»: sen preguntar (pódese volver). */
  protected async leave(): Promise<void> {
    const pending = this.view()?.action === 'pending';
    await this.run('leave', async () => {
      await this.store.leave(this.slug(), this.id());
      this.match.set(await this.reread());
      this.toasts.show(pending ? 'openMatch.page.withdrawnToast' : 'openMatch.page.leftToast');
    });
  }

  /** «Deixar de buscar» (quen organiza): o diálogo de sempre; pechada, a tarxeta cambia. */
  protected async stop(): Promise<void> {
    const mine = this.mine();
    if (!mine?.bookingId) return;
    this.problem.set(null);
    try {
      const subject = { title: mine.title || mine.facilityName, start: mine.start, end: mine.end };
      const closed = await this.matchActions.stop(this.slug(), mine.bookingId, mine.players.length, subject);
      if (closed) this.match.set(closed);
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  /** «Cancelar a reserva» (quen organiza): a reserva e a partida; a tarxeta pasa a «cancelouse». */
  protected async cancelBooking(): Promise<void> {
    const booking = this.booking();
    if (!booking) return;
    this.problem.set(null);
    try {
      if (await this.cancelling.confirm(this.slug(), booking)) this.match.set(await this.reread());
    } catch (error) {
      this.problem.set(asProblem(error));
      void this.loadBooking(this.slug(), this.match());
    }
  }

  protected async decide(request: OpenMatchRequest, accept: boolean): Promise<void> {
    await this.run(request.id + (accept ? 'accept' : 'reject'), async () => {
      const slug = this.slug();
      const updated = accept
        ? await this.store.accept(slug, this.id(), request.id)
        : await this.store.reject(slug, this.id(), request.id);
      this.match.set(updated);
      this.toasts.show(accept ? 'openMatch.page.acceptedToast' : 'openMatch.page.rejectedToast', {
        name: request.name,
      });
    });
  }

  private async run(what: string, work: () => Promise<void>): Promise<void> {
    if (this.busy()) return;
    this.busy.set(what);
    this.problem.set(null);
    try {
      await work();
    } catch (error) {
      this.problem.set(asProblem(error));
      // Completa ou pechada entrementres: a tarxeta amosa o estado de agora.
      this.match.set(await this.reread().catch(() => this.match()));
    } finally {
      this.busy.set(null);
    }
  }

  private async loadPoint(slug: string, facilityId: string): Promise<void> {
    // Sen o punto, a páxina segue sen «Como chegar» (a tarxeta xa leva o enderezo).
    this.point.set(await this.store.facilityPoint(slug, facilityId).catch(() => null));
  }

  /** Quen organiza: a súa reserva (para «Cancelar a reserva») e os textos da devolución. */
  private async loadBooking(slug: string, match: ShownMatch | null): Promise<void> {
    const mine = asMine(match);
    if (mine?.me.role !== 'ORGANIZER' || !mine.bookingId) return;
    if (!this.booking()) this.bookings.loadMunicipalityTexts(slug);
    // Sen ela, sen «Cancelar a reserva» (segue en «As miñas reservas»).
    this.booking.set(await this.bookings.get(slug, mine.bookingId).catch(() => null));
  }

  private reread(): Promise<ShownMatch> {
    return this.session.authenticated()
      ? this.store.mine(this.slug(), this.id())
      : this.store.get(this.slug(), this.id());
  }

  private async load(slug: string, id: string): Promise<void> {
    this.state.set(this.match() ? 'ready' : 'loading');
    try {
      const match = this.session.authenticated() ? await this.store.mine(slug, id) : await this.store.get(slug, id);
      this.match.set(match);
      this.state.set('ready');
      if (this.mapEnabled() && !this.point()) void this.loadPoint(slug, match.facilityId);
      void this.loadBooking(slug, match);
      // «Unirme» sen sesión, xusto antes de entrar: agora, o mesmo ca o botón (unha vez). Se a
      // páxina se ve sen sesión (cancelou o acceso), esquécese.
      if (!this.session.authenticated()) clearJoinIntent(this.tab());
      else if (takeJoinIntent(this.tab(), slug, id, Date.now())) void this.join();
    } catch (error) {
      this.notFound.set((error as ApiProblem | undefined)?.status === 404);
      this.state.set('error');
    }
  }
}
