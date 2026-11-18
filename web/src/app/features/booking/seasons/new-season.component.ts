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
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { focusErrorOrAlert, revealAndFocus } from '../../../shared/ui/focus-error/focus-error';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { SelectOption } from '../../../shared/ui/select/select-option';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { ManageBookingsStore } from '../manage/manage-bookings.store';
import { SeasonFormComponent } from './season-form.component';
import { SeasonPlanner } from './season-planner';
import { SchedulePreviewComponent } from '../../../shared/schedule/schedule-preview.component';
import { SeasonsStore } from './seasons.store';

/**
 * «Nova reserva › Tempada»: a entidade e os campos da tempada, os días que non se
 * crean na mesma pantalla (`furar-schedule-preview`) e «Crear N sesións». Creada, volve a «Reservas»
 * cun aviso. Os choques que se cancelan desde aquí usan o diálogo de sempre.
 */
@Component({
  selector: 'furar-new-season',
  imports: [ButtonDirective, ProblemAlertComponent, SeasonFormComponent, SchedulePreviewComponent, TranslocoPipe],
  providers: [SeasonsStore, SeasonPlanner, ManageBookingsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.';
    <furar-season-form
      [slug]="slug()"
      [(draft)]="planner.draft"
      [organizations]="organizations()"
      [errors]="planner.visibleErrors()"
    />
    @if (noOrganizations()) {
      <p class="f-season-note" data-season-no-organizations>{{ t + 'noOrganizations' | transloco }}</p>
    }
    <div role="status" class="f-reserve__live" data-season-status>
      @if (planner.previewState() === 'loading') {
        <p class="f-season-note">{{ t + 'preview.loading' | transloco }}</p>
      } @else if (planner.sessions() === 0) {
        <p class="f-season-note" data-season-none tabindex="-1">{{ t + 'preview.none' | transloco }}</p>
      }
    </div>
    @if (planner.preview(); as preview) {
      <furar-schedule-preview [preview]="preview" (cancelBooking)="planner.cancelConflict($event)" />
    }
    <furar-problem-alert [problem]="planner.problem()" />
    <button
      type="button"
      furarButton="primary"
      class="f-reserve__go"
      data-season-create
      [busy]="planner.saving()"
      (click)="create()"
    >
      @if (planner.sessions(); as count) {
        {{ t + (count === 1 ? 'createOne' : 'createOther') | transloco: { count } }}
      } @else {
        {{ t + 'create' | transloco }}
      }
    </button>
  `,
})
export class NewSeasonComponent {
  protected readonly planner = inject(SeasonPlanner);
  private readonly store = inject(SeasonsStore);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly form = viewChild.required(SeasonFormComponent);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  readonly slug = input.required<string>();

  private readonly loaded = signal(false);
  protected readonly organizations = computed<SelectOption<string>[]>(() =>
    this.store.organizations().map((organization) => ({ value: organization.id, label: organization.name })),
  );
  protected readonly noOrganizations = computed(() => this.loaded() && this.organizations().length === 0);

  constructor() {
    effect(() => {
      const slug = this.slug();
      untracked(() => void this.store.loadOrganizations(slug).then(() => this.loaded.set(true)));
    });
    effect(() => this.planner.partRequired.set(this.form().partRequired()));
  }

  protected async create(): Promise<void> {
    // Sempre activo: se con estes datos non sae ningunha sesión, o foco vai ao aviso que o di.
    if (this.planner.sessions() === 0) {
      const none = this.host.nativeElement.querySelector<HTMLElement>('[data-season-none]');
      if (none) revealAndFocus(none);
      return;
    }
    const series = await this.planner.create();
    if (!series) {
      // Ao fallar, o foco ao primeiro campo con erro ou ao aviso (foco ao erro).
      afterNextRender(() => focusErrorOrAlert(this.host.nativeElement), { injector: this.injector });
      return;
    }
    this.toasts.show('booking.seasons.created', { name: series.organizationName, count: series.sessions });
    await this.router.navigate(['/', this.slug(), 'xestion', 'reservas']);
  }
}
