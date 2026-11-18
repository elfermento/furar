// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { SessionService } from '../../../core/auth/session.service';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { IconComponent, registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { WaitsStore } from '../waits/waits.store';
import { ReserveHours } from './reserve-hours';
import { ReserveStore } from './reserve.store';

// A icona de «Deixar de esperar» (a de «Cancelar») non vai na carga inicial.
registerSymbols(LAZY_SYMBOLS);

/**
 * Cunha hora ocupada escollida (ADR-0033): «Avísame se se libera» no canto de
 * «Continuar»; despois, o aviso verde e «Deixar de esperar» (perigo, sen confirmar: pódese volver
 * esperar). Sen sesión, o botón leva a entrar e volve ao mesmo paso coa hora posta; a persoa
 * pulsa outra vez. Se a hora xa quedou libre (`booking.wait-slot-free`), recárganse as horas e
 * volve «Continuar» co texto do erro como aviso; o resto de erros, no propio panel.
 */
@Component({
  selector: 'furar-wait-panel',
  imports: [ButtonDirective, CalloutComponent, IconComponent, ProblemAlertComponent, RouterLink, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @let t = 'booking.reserve.wait.';
    @if (waiting(); as wait) {
      <furar-callout tone="success" live="status" [heading]="t + 'onTitle' | transloco" data-wait-on>
        <p>{{ t + 'onText' | transloco }}</p>
      </furar-callout>
      <button type="button" furarButton="danger" data-wait-remove [busy]="busy()" (click)="leave(wait.id)">
        <furar-icon name="event-busy" />
        {{ t + 'remove' | transloco }}
      </button>
    } @else {
      <button
        type="button"
        furarButton="primary"
        data-wait-add
        [busy]="busy()"
        [attr.aria-describedby]="store.authenticated() ? null : 'reserve-wait-sign-in'"
        (click)="join()"
      >
        {{ t + 'add' | transloco }}
      </button>
      @if (!store.authenticated()) {
        <p class="f-reserve__fine" id="reserve-wait-sign-in" data-wait-sign-in>{{ t + 'signIn' | transloco }}</p>
      }
    }
    @if (problem(); as p) {
      <furar-problem-alert [problem]="p" data-wait-error />
      @if (p.code === 'booking.wait-limit') {
        <a furarButton [routerLink]="['/', slug(), 'reservas']" data-wait-mine>{{ t + 'mine' | transloco }}</a>
      }
    }
  `,
})
export class WaitPanelComponent {
  protected readonly store = inject(ReserveStore);
  private readonly hours = inject(ReserveHours);
  private readonly waits = inject(WaitsStore);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly municipality = inject(CurrentMunicipality);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  /** A espera da persoa por esta hora, se xa a ten (tamén ao chegar desde o correo). */
  protected readonly waiting = computed(() => {
    const use = this.store.use();
    const hour = this.hours.chosenBusy();
    if (!use || !hour) return null;
    const start = new Date(hour.start).getTime();
    return this.waits.items().find((wait) => wait.useId === use.id && new Date(wait.start).getTime() === start) ?? null;
  });

  constructor() {
    // As esperas da persoa, unha vez; e o erro dunha hora non queda para a seguinte.
    effect(() => {
      const slug = this.slug();
      if (slug && this.store.authenticated()) untracked(() => void this.waits.load(slug));
    });
    effect(() => {
      this.hours.chosenBusy();
      untracked(() => this.problem.set(null));
    });
  }

  protected async join(): Promise<void> {
    if (!this.session.authenticated()) {
      await this.session.login(this.router.url);
      return;
    }
    const facility = this.store.facility();
    const use = this.store.use();
    const hour = this.hours.chosenBusy();
    if (!facility || !use || !hour || this.busy()) return;
    this.busy.set(true);
    this.problem.set(null);
    try {
      await this.waits.join(this.slug(), facility.id, use.id, hour.start);
    } catch (error) {
      const problem = asProblem(error);
      if (problem.code === 'booking.wait-slot-free') {
        // A hora xa está libre: vólvense pedir as horas e queda escollida para reservala.
        await this.hours.freedNow(problem.translated);
      } else {
        this.problem.set(problem);
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected async leave(id: string): Promise<void> {
    if (this.busy()) return;
    this.busy.set(true);
    this.problem.set(null);
    try {
      await this.waits.leave(this.slug(), id);
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
