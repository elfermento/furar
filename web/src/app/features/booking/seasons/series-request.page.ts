// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { formatDateLong } from '../../../shared/ui/calendar/local-date';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { focusErrorOrAlert, revealAndFocus } from '../../../shared/ui/focus-error/focus-error';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { SelectOption } from '../../../shared/ui/select/select-option';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { TagComponent } from '../../../shared/ui/tag/tag.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ManageBookingsStore } from '../manage/manage-bookings.store';
import type { RejectRequestData } from './reject-request-dialog.component';
import { RejectRequestDialogComponent } from './reject-request-dialog.component';
import { SeasonFormComponent } from './season-form.component';
import { SeasonPlanner } from './season-planner';
import { SchedulePreviewComponent } from '../../../shared/schedule/schedule-preview.component';
import { draftOf } from './seasons';
import type { SeriesRequestCardResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/**
 * Revisar unha solicitude de tempada (`/:concello/xestion/entidades/solicitudes/:id`):
 * «←» e o título; a entidade, a súa nota entre comiñas e quen a pediu e cando; os campos da
 * tempada xa enchidos (a xestora pode axustalos; a entidade non cambia), os días que non se crean
 * e, á dereita, «Rexeitar» e «Aprobar e crear N sesións». Rexeitar pide a mensaxe que lle chega á
 * entidade por correo. Unha solicitude xa decidida só se mostra.
 */
@Component({
  selector: 'furar-series-request-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    ProblemAlertComponent,
    RouterLink,
    SeasonFormComponent,
    SchedulePreviewComponent,
    StatusStateComponent,
    TagComponent,
    TranslocoPipe,
  ],
  providers: [SeasonsStore, SeasonPlanner, ManageBookingsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.';
    <div class="f-reserve">
      <div class="f-reserve__title">
        <a
          furarButton="secondary"
          round
          data-request-back
          [routerLink]="['/', slug(), 'xestion', 'entidades']"
          [attr.aria-label]="t + 'review.back' | transloco"
        >
          <furar-icon name="arrow-left" />
        </a>
        <h1>{{ t + 'review.title' | transloco }}</h1>
      </div>
      @switch (state()) {
        @case ('error') {
          <furar-status-state mode="error" [heading]="t + 'review.loadError' | transloco">
            <a furarButton [routerLink]="['/', slug(), 'xestion', 'entidades']">{{ t + 'review.back' | transloco }}</a>
          </furar-status-state>
        }
        @case ('ready') {
          @if (request(); as r) {
            <section
              class="f-card f-reserve__card"
              [attr.data-series-request-review]="r.id"
              [attr.aria-label]="r.organizationName"
            >
              <furar-callout tone="info" data-request-from [heading]="r.organizationName">
                @if (r.note) {
                  <p class="f-season-quote">{{ t + 'review.note' | transloco: { note: r.note } }}</p>
                }
                <p class="f-season-meta" data-request-meta>
                  @for (part of meta(); track $index) {
                    <span class="f-season-part">{{ part }}</span>
                    @if (!$last) {
                      {{ ' · ' }}
                    }
                  }
                </p>
              </furar-callout>
              @if (r.status === 'PENDING') {
                <furar-season-form
                  [slug]="slug()"
                  [(draft)]="planner.draft"
                  [organizations]="organization()"
                  [errors]="planner.visibleErrors()"
                />
                <div role="status" class="f-reserve__live">
                  @if (planner.previewState() === 'loading') {
                    <p class="f-season-note">{{ t + 'preview.loading' | transloco }}</p>
                  } @else if (planner.sessions() === 0) {
                    <p class="f-season-note" data-request-none tabindex="-1">{{ t + 'preview.none' | transloco }}</p>
                  }
                </div>
                @if (planner.preview(); as preview) {
                  <furar-schedule-preview [preview]="preview" (cancelBooking)="planner.cancelConflict($event)" />
                }
                <furar-problem-alert [problem]="planner.problem()" />
                <div class="f-season-actions">
                  <button type="button" furarButton="danger" data-request-reject (click)="reject(r)">
                    <furar-icon name="close" />
                    {{ t + 'review.reject' | transloco }}
                  </button>
                  <button
                    type="button"
                    furarButton="primary"
                    data-request-approve
                    [busy]="planner.saving()"
                    (click)="approve(r)"
                  >
                    @if (planner.sessions(); as count) {
                      {{ t + (count === 1 ? 'review.approveOne' : 'review.approveOther') | transloco: { count } }}
                    } @else {
                      {{ t + 'review.approve' | transloco }}
                    }
                  </button>
                </div>
              } @else {
                <p class="f-season-decided">
                  <furar-tag
                    look="solid"
                    [tone]="r.status === 'APPROVED' ? 'success' : 'danger'"
                    [attr.data-chip]="r.status"
                    >{{ t + 'request.' + r.status | transloco }}</furar-tag
                  >
                </p>
                @if (r.rejectionMessage) {
                  <p class="f-season-quote">{{ r.rejectionMessage }}</p>
                }
              }
            </section>
          }
        }
        @default {
          <furar-status-state mode="skeleton" shape="card" [rows]="4" [description]="'common.loading' | transloco" />
        }
      }
    </div>
  `,
})
export class SeriesRequestPage {
  /** Parámetro `:id` da ruta. */
  readonly id = input.required<string>();

  protected readonly planner = inject(SeasonPlanner);
  private readonly store = inject(SeasonsStore);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  private readonly form = viewChild(SeasonFormComponent);

  protected readonly slug = computed(() => this.municipality.slug() ?? '');
  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly request = signal<SeriesRequestCardResponse | null>(null);
  /** A entidade da solicitude: vese no campo, pero non se cambia. */
  protected readonly organization = computed<SelectOption<string>[]>(() => {
    const r = this.request();
    return r ? [{ value: r.organizationId, label: r.organizationName }] : [];
  });
  /** «xiana@… · 10 de novembro»: quen a pediu e cando. */
  protected readonly meta = computed(() => {
    this.langChanges();
    const r = this.request();
    if (!r) return [];
    const day = formatDateLong(inMadrid(r.createdAt).date, this.language.lang());
    return [r.requestedByEmail, day].filter((part): part is string => !!part);
  });

  constructor() {
    effect(() => {
      const slug = this.slug();
      const id = this.id();
      if (slug) untracked(() => void this.load(slug, id));
    });
    effect(() => {
      const form = this.form();
      if (form) this.planner.partRequired.set(form.partRequired());
    });
  }

  private async load(slug: string, id: string): Promise<void> {
    this.state.set('loading');
    try {
      const request = await this.store.getRequest(slug, id);
      this.request.set(request);
      this.planner.draft.set(draftOf(request));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  protected async approve(request: SeriesRequestCardResponse): Promise<void> {
    // Sempre activo: se con estes datos non sae ningunha sesión, o foco vai ao aviso que o di.
    if (this.planner.sessions() === 0) {
      const none = this.host.nativeElement.querySelector<HTMLElement>('[data-request-none]');
      if (none) revealAndFocus(none);
      return;
    }
    const series = await this.planner.create(request.id);
    if (!series) {
      afterNextRender(() => focusErrorOrAlert(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.toasts.show('booking.seasons.review.approved', { name: series.organizationName, count: series.sessions });
    await this.router.navigate(['/', this.slug(), 'xestion', 'entidades']);
  }

  protected async reject(request: SeriesRequestCardResponse): Promise<void> {
    const data: RejectRequestData = { request, slug: this.slug() };
    const ref = this.dialogs.open<SeriesRequestCardResponse, RejectRequestData, RejectRequestDialogComponent>(
      RejectRequestDialogComponent,
      { data, injector: this.injector },
    );
    const rejected = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (!rejected) return;
    this.toasts.show('booking.seasons.review.rejected', { name: rejected.organizationName });
    await this.router.navigate(['/', this.slug(), 'xestion', 'entidades']);
  }
}
