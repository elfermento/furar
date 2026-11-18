// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { RemindersStore } from './reminders.store';

type State =
  { readonly kind: 'sending' } | { readonly kind: 'done' } | { readonly kind: 'error'; readonly text: string };

/** Sen proba no URL: o mesmo texto ca unha ligazón que non serve. */
const INVALID = 'errors.booking.reminder-link-invalid';

/**
 * «Non quero recordatorios» (`/:concello/recordatorios/baixa?t=…`, ADR-0035): a ligazón do correo
 * trae a proba asinada, sen sesión nin datos persoais; a páxina mándaa unha vez ao abrir e di o
 * resultado. Sen proba, ou se a API a rexeita (manipulada, caducada), o motivo da API.
 */
@Component({
  selector: 'furar-reminder-opt-out-page',
  imports: [CalloutComponent, StatusStateComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.reminders.optOut.';
    <div class="f-mine">
      <h1>{{ t + 'title' | transloco }}</h1>
      @switch (state().kind) {
        @case ('done') {
          <furar-callout tone="success" live="status" data-opt-out-done>
            {{ t + 'done' | transloco: { municipality: name() } }}
          </furar-callout>
        }
        @case ('error') {
          <furar-callout tone="danger" live="alert" data-opt-out-error>{{
            errorText() || (invalidKey | transloco)
          }}</furar-callout>
        }
        @default {
          <furar-status-state mode="loading" [description]="t + 'sending' | transloco" />
        }
      }
    </div>
  `,
})
export class ReminderOptOutPage implements OnInit {
  /** A proba asinada, do parámetro `t` do URL. */
  readonly t = input<string | undefined>(undefined);

  private readonly reminders = inject(RemindersStore);
  private readonly municipality = inject(CurrentMunicipality);
  protected readonly state = signal<State>({ kind: 'sending' });
  protected readonly invalidKey = INVALID;
  protected readonly name = computed(() => this.municipality.name() ?? '');
  protected readonly errorText = computed(() => {
    const state = this.state();
    return state.kind === 'error' ? state.text : '';
  });

  ngOnInit(): void {
    const token = this.t()?.trim();
    const slug = this.municipality.slug();
    if (!token || !slug) {
      this.state.set({ kind: 'error', text: '' });
      return;
    }
    this.reminders.optOut(slug, token).then(
      () => this.state.set({ kind: 'done' }),
      (error: unknown) => this.state.set({ kind: 'error', text: asProblem(error).translated }),
    );
  }
}
