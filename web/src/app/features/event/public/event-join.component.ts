// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { SessionService } from '../../../core/auth/session.service';
import { ApiProblem } from '../../../core/errors/api-problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import type { DependentResponse } from '../../../shared/dependents/dependents.store';
import { DependentsStore } from '../../../shared/dependents/dependents.store';
import { addDependent } from '../../../shared/dependents/open-dependents';
import { SELF } from '../../../shared/dependents/participants';
import { ageText, birthYearFits, hasAgeLimit } from '../../../shared/events/event-format';
import type { MyEventRegistrationResponse, PublicEventResponse } from '../../../shared/events/public-events.store';
import { PublicEvents } from '../../../shared/events/public-events.store';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { FieldComponent } from '../../../shared/ui/field.component';
import { revealAndFocus } from '../../../shared/ui/focus-error/focus-error';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import type { PersonOption } from '../../../shared/ui/person-picker/person-picker.component';
import { PersonPickerComponent } from '../../../shared/ui/person-picker/person-picker.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';

/** Códigos que van debaixo da data de nacemento. */
const BIRTH_CODES = new Set(['event.birth-date-invalid', 'event.birth-year-required', 'event.age-not-allowed']);

/** Queda alguén da conta por apuntar (eu ou unha persoa a cargo sen inscrición). */
export function anyoneLeft(
  registrations: readonly MyEventRegistrationResponse[],
  dependents: readonly Pick<DependentResponse, 'id'>[],
): boolean {
  const taken = new Set(registrations.map((r) => r.dependent?.id ?? SELF));
  return !taken.has(SELF) || dependents.some((d) => !taken.has(d.id));
}

/**
 * Apuntarse nun evento ou nunha actividade (ADR-0041): «Para quen» (`furar-person-picker`: eu e
 * as persoas a cargo sen inscrición; a que non entra pola idade, atenuada co motivo) só se hai
 * persoas a cargo ou límite de idade, a data de nacemento completa se vou eu e hai límite (co erro
 * debaixo do campo e o foco nel), «Engadir persoa» centrado e o botón: «Apuntarme», «Pedir praza»
 * ou, con lista de espera, «Avísame se queda sitio». Sen sesión, o botón leva a entrar e volve á
 * ficha sen facer nada só. Despois de apuntar (ou se falla), `changed`: a ficha volve cargar.
 */
@Component({
  selector: 'furar-event-join',
  imports: [
    ButtonDirective,
    DateFieldComponent,
    FieldComponent,
    IconComponent,
    PersonPickerComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-event-join' },
  template: `
    @let t = 'event.public.';
    @if (showPicker()) {
      <p class="f-public-card__title" id="event-who">{{ t + 'who.title' | transloco }}</p>
      <furar-person-picker
        labelledBy="event-who"
        data-event-who
        [options]="people()"
        [selected]="chosen()"
        [disabled]="busy()"
        (selectedChange)="pick($event)"
      />
      @if (needsBirthDate()) {
        <furar-field
          fieldId="event-birth-date"
          data-event-birth
          required
          [label]="t + 'birthDate' | transloco"
          [error]="birthError()"
        >
          <furar-date-field
            required
            yearSelect
            startView="years"
            min="1900-01-01"
            buttonId="event-birth-date"
            [label]="t + 'birthDate' | transloco"
            [max]="today"
            [invalid]="!!birthError()"
            [describedBy]="birthError() ? 'event-birth-date-error' : null"
            [formControl]="birthDate"
          />
        </furar-field>
      }
      <button type="button" furarButton size="sm" class="f-event-join__add" data-event-add-person (click)="addPerson()">
        <furar-icon name="plus" />
        {{ 'dependents.add' | transloco }}
      </button>
    }
    @if (mode() === 'wait') {
      <button type="button" furarButton="primary" data-event-wait [busy]="busy()" (click)="join()">
        {{ 'event.band.notifyMe' | transloco }}
      </button>
    } @else {
      <button type="button" furarButton="primary" block data-event-join [busy]="busy()" (click)="join()">
        {{ (mode() === 'ask' ? t + 'ask' : t + 'join') | transloco }}
      </button>
    }
    <furar-problem-alert [problem]="problem()" />
  `,
})
export class EventJoinComponent {
  private readonly store = inject(PublicEvents);
  private readonly dependents = inject(DependentsStore);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toasts = inject(ToastService);
  private readonly dialogs = inject(DialogService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly langChanges = toSignal(this.transloco.langChanges$);

  readonly event = input.required<PublicEventResponse>();
  /** As inscricións que xa ten a conta neste evento. */
  readonly registrations = input<readonly MyEventRegistrationResponse[]>([]);
  /** `join`, `ask` («Con aprobación») ou `wait` (lista de espera). */
  readonly mode = input<'join' | 'ask' | 'wait'>('join');
  /** Apuntouse (ou fallou): a ficha volve cargar o evento e as inscricións. */
  readonly changed = output();

  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** Para quen: `SELF` ou o id dunha persoa a cargo. */
  private readonly who = signal<string | null>(null);
  protected readonly birthDate = new FormControl<IsoDate | null>(null);
  protected readonly birthError = signal<ControlError | null>(null);
  protected readonly today = madridToday();

  private readonly t = (key: string, params?: Record<string, unknown>) => {
    this.langChanges();
    return this.transloco.translate(key, params);
  };

  /** As persoas da conta que aínda se poden apuntar: eu e as persoas a cargo sen inscrición. */
  protected readonly people = computed<PersonOption[]>(() => {
    const event = this.event();
    if (!this.session.authenticated()) return [];
    const taken = new Set(this.registrations().map((r) => r.dependent?.id ?? SELF));
    const options: PersonOption[] = [];
    if (!taken.has(SELF)) {
      options.push({ id: SELF, label: this.t('event.public.who.me'), name: this.session.name() ?? '' });
    }
    const age = ageText(event, this.t).toLocaleLowerCase(this.language.lang());
    for (const dependent of this.dependents.items()) {
      if (taken.has(dependent.id)) continue;
      options.push({
        id: dependent.id,
        label: dependent.name,
        detail: this.t('event.public.who.dependent', { year: dependent.birthYear }),
        blocked: birthYearFits(event, dependent.birthYear) ? undefined : this.t('event.public.who.notAllowed', { age }),
      });
    }
    return options;
  });
  /** «Para quen» só se hai a quen escoller: persoas a cargo ou límite de idade. */
  protected readonly showPicker = computed(
    () => this.session.authenticated() && (this.dependents.items().length > 0 || hasAgeLimit(this.event())),
  );
  /** A persoa escollida, se aínda se pode escoller (por defecto, a primeira que se pode). */
  protected readonly chosen = computed(() => {
    const options = this.people().filter((option) => !option.blocked);
    const who = this.who();
    if (who && options.some((option) => option.id === who)) return who;
    // Nunha actividade con límite de idade, de primeiras a persoa a cargo que entra.
    const dependent = hasAgeLimit(this.event()) ? options.find((option) => option.id !== SELF) : undefined;
    return (dependent ?? options[0])?.id ?? null;
  });
  /** Vou eu e hai límite de idade: pide a data de nacemento. */
  protected readonly needsBirthDate = computed(() => hasAgeLimit(this.event()) && this.chosen() === SELF);

  constructor() {
    this.birthDate.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.birthError.set(null));
  }

  protected pick(id: string | null): void {
    this.who.set(id);
    this.birthError.set(null);
  }

  protected async addPerson(): Promise<void> {
    const id = await addDependent(this.dialogs);
    if (id) this.who.set(id);
  }

  /** Apuntarse, pedir praza ou entrar na lista de espera para a persoa escollida. */
  protected async join(): Promise<void> {
    if (!this.session.authenticated()) {
      await this.session.login(this.router.url);
      return;
    }
    const who = this.chosen();
    if (!who || this.busy()) return;
    const birthDate = this.needsBirthDate() ? this.birthDate.value : null;
    if (this.needsBirthDate() && !birthDate) {
      this.birthError.set({ key: 'event.public.birthDateRequired', params: {} });
      this.focusBirthDate();
      return;
    }
    this.busy.set(true);
    this.problem.set(null);
    try {
      const mine = await this.store.join(this.municipality.slug() ?? '', this.event().id, {
        ...(who === SELF ? {} : { dependentId: who }),
        ...(birthDate ? { birthDate } : {}),
      });
      this.toasts.show(joinedToast(mine.status));
      this.who.set(null);
      this.birthDate.reset(null);
    } catch (error) {
      const problem = error instanceof ApiProblem ? error : null;
      if (problem && BIRTH_CODES.has(problem.code as string) && this.needsBirthDate()) {
        this.birthError.set({ key: problem.messageKey, params: { ...problem.params } });
        this.focusBirthDate();
      } else {
        this.problem.set(error as ApiProblem);
      }
    } finally {
      this.busy.set(false);
      // Sexa como sexa, a ficha amosa o estado de agora (completo ou pechado entrementres).
      this.changed.emit();
    }
  }

  private focusBirthDate(): void {
    afterNextRender(
      () => {
        const button = this.host.querySelector<HTMLElement>('[data-event-birth] button');
        if (button) revealAndFocus(button);
      },
      { injector: this.injector },
    );
  }
}

function joinedToast(status: MyEventRegistrationResponse['status']): string {
  if (status === 'PENDING') return 'event.public.askedToast';
  if (status === 'WAITING' || status === 'NOTIFIED') return 'event.band.waitToast';
  return 'event.public.joinedToast';
}
