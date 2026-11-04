// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Location } from '@angular/common';
import type { OnInit } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { ActionBarComponent } from '../../../shared/ui/action-bar/action-bar.component';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { focusFirstError } from '../../../shared/ui/edit-section/section-errors';
import { IconComponent } from '../../../shared/ui/icon/icon.component';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import type { StepperStep } from '../../../shared/ui/stepper/stepper.component';
import { StepperComponent } from '../../../shared/ui/stepper/stepper.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import { PaymentNotesStore } from '../description/payment-notes.store';
import { FacilityDetailStore } from '../detail/facility-detail.store';
import { ScheduleStore } from '../schedule/schedule.store';
import { UsesStore } from '../uses/uses.store';
import type { LeaveWizardData, LeaveWizardResult } from './leave-wizard-dialog.component';
import { LeaveWizardDialogComponent } from './leave-wizard-dialog.component';
import { ContactStepComponent } from './steps/contact-step.component';
import { DataStepComponent } from './steps/data-step.component';
import { LocationStepComponent } from './steps/location-step.component';
import { PhotosStepComponent } from './steps/photos-step.component';
import { ReviewStepComponent } from './steps/review-step.component';
import { RulesStepComponent } from './steps/rules-step.component';
import { ScheduleStepComponent } from './steps/schedule-step.component';
import { UsesStepComponent } from './steps/uses-step.component';
import { OPTIONAL_STEPS, REVIEW, STEP, STEP_COUNT, STEP_KEYS, stepLabelKey, stepState } from './wizard';
import { WizardForms } from './wizard-forms';
import { WizardSaver } from './wizard-saver.store';
import { WizardUseEditor } from './wizard-use-editor';
import { WizardStore } from './wizard.store';

/**
 * Asistente «Nova instalación» (maqueta 53): unha páxina, non un diálogo. Enriba, «←
 * Instalacións», o nome (ou «Nova instalación») e cando se gardou por última vez; o indicador
 * de pasos; o paso; e ao pé, «Anterior» e «Gardar como borrador» á esquerda e «Saltar» (os
 * opcionais) e «Seguinte», ou no último «Publicar», á dereita. Desde o listado, `nova`; para
 * seguir cun borrador, `nova/:id` (o URL cámbiase só ao crear, sen navegar). Saír a medias
 * pregunta: seguir, descartar ou gardar o borrador.
 */
@Component({
  selector: 'furar-facility-wizard-page',
  imports: [
    ActionBarComponent,
    ButtonDirective,
    ContactStepComponent,
    DataStepComponent,
    IconComponent,
    LocationStepComponent,
    PageHeaderComponent,
    PhotosStepComponent,
    ProblemAlertComponent,
    ReviewStepComponent,
    RouterLink,
    RulesStepComponent,
    ScheduleStepComponent,
    StatusStateComponent,
    StepperComponent,
    TranslocoPipe,
    UsesStepComponent,
  ],
  providers: [
    WizardStore,
    WizardForms,
    WizardSaver,
    WizardUseEditor,
    FacilityDetailStore,
    UsesStore,
    ScheduleStore,
    PaymentNotesStore,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './facility-wizard.page.html',
})
export class FacilityWizardPage implements OnInit {
  protected readonly store = inject(WizardStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly transloco = inject(TranslocoService);
  private readonly dialogs = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  private readonly lang = toSignal(this.transloco.langChanges$);

  /** O id do URL ao entrar (`nova/:id`); sen el, o asistente parte de cero. */
  private readonly openedId = this.route.snapshot.paramMap.get('id');
  /** Volver ao listado desde `nova` ou desde `nova/:id`. */
  protected readonly listLink = this.openedId ? '../..' : '..';
  /** Xa gardou, publicou ou descartou: a saída non pregunta. */
  private leaving = false;

  protected readonly keys = STEP_KEYS;
  protected readonly review = REVIEW;
  protected readonly stepKey = computed(() => STEP_KEYS[this.store.step()]);
  protected readonly optional = computed(() => {
    const key = this.stepKey();
    return OPTIONAL_STEPS.has(key) && !(key === 'contact' && this.store.contactRequired());
  });
  protected readonly stepName = computed(() => {
    this.lang();
    return this.transloco.translate(stepLabelKey(this.stepKey(), this.store.scope()));
  });
  protected readonly heading = computed(() => {
    this.lang();
    this.store.ok();
    const name = this.store.name.value.trim();
    return this.store.step() > 0 && name ? name : this.transloco.translate('facility.wizard.title');
  });
  protected readonly steps = computed<StepperStep[]>(() => {
    this.lang();
    const ok = this.store.ok();
    const current = this.store.step();
    const max = this.store.max();
    return STEP_KEYS.map((key, i) => ({
      label: this.transloco.translate(stepLabelKey(key, this.store.scope())),
      optional: OPTIONAL_STEPS.has(key) && !(key === 'contact' && this.store.contactRequired()),
      state: stepState(i, current, max, ok[key]),
      reachable: i <= max,
    }));
  });

  constructor() {
    // Ao crear a instalación (paso 1), o URL pasa a `nova/:id` sen navegar: recargar segue nela.
    effect(() => {
      const id = this.store.facility()?.id;
      if (!id || this.openedId) return;
      untracked(() => {
        const url = this.router.serializeUrl(this.router.createUrlTree([id], { relativeTo: this.route }));
        this.location.replaceState(url);
      });
    });
  }

  ngOnInit(): void {
    if (this.openedId) void this.store.open(this.openedId);
    else void this.store.start().then(() => this.focus('#wizard-name'));
  }

  protected reload(): void {
    if (this.openedId) void this.store.open(this.openedId);
  }

  /** Desde o indicador ou «Editar» de «Revisar»: o uso aberto pecha antes se está ben. */
  protected async go(step: number): Promise<void> {
    if (!(await this.store.useEditor.apply())) return this.focusError();
    this.store.goTo(step);
    this.focusStep();
  }

  protected previous(): void {
    this.store.goTo(this.store.step() - 1);
    this.focusStep();
  }

  protected skip(): void {
    this.store.useEditor.close();
    this.store.goTo(this.store.step() + 1);
    this.focusStep();
  }

  protected async next(): Promise<void> {
    if (await this.store.next()) this.focusStep();
    else this.focusError();
  }

  protected async saveDraft(): Promise<void> {
    if (!(await this.store.saveDraft())) {
      this.focusError();
      return;
    }
    this.leaving = true;
    this.toast.show('facility.wizard.draftSaved');
    await this.router.navigate([this.listLink], { relativeTo: this.route });
  }

  protected async publish(): Promise<void> {
    if (this.store.missing().length) {
      this.focus('#wizard-missing');
      return;
    }
    const published = await this.store.publish();
    if (!published) {
      this.focusError();
      return;
    }
    this.leaving = true;
    this.toast.show('facility.wizard.published');
    await this.router.navigate([this.listLink, published.id], { relativeTo: this.route });
  }

  /**
   * Garda de saída: sen nada escrito, sae; se non, pregunta. «Descartar» borra o borrador se xa
   * estaba gardado; «Gardar borrador» gárdao e sae.
   */
  async canLeave(): Promise<boolean> {
    if (this.leaving || !this.store.started()) return true;
    const ref = this.dialogs.open<LeaveWizardResult, LeaveWizardData>(LeaveWizardDialogComponent, {
      data: { canSave: this.store.name.valid },
    });
    const choice = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (choice === 'discard') {
      if (!(await this.store.discard())) return false;
      this.leaving = true;
      this.toast.show('facility.wizard.discarded');
      return true;
    }
    if (choice === 'save') {
      if (!(await this.store.saveDraft())) {
        this.focusError();
        return false;
      }
      this.leaving = true;
      this.toast.show('facility.wizard.draftSaved');
      return true;
    }
    return false;
  }

  private focusStep(): void {
    this.focus('#wizard-step-title', true);
  }

  /** Foco no primeiro campo con erro do paso; se non hai, na mensaxe de erro ou no aviso. */
  private focusError(): void {
    afterNextRender(
      () => {
        const card = this.host.nativeElement.querySelector<HTMLElement>('[data-wizard-step]');
        if (card && focusFirstError(card)) return;
        const message = this.host.nativeElement.querySelector<HTMLElement>(
          '[data-wizard-step] .f-field-error, furar-problem-alert [role="alert"]',
        );
        if (message) {
          message.tabIndex = -1;
          message.focus();
        }
      },
      { injector: this.injector },
    );
  }

  private focus(selector: string, scroll = false): void {
    afterNextRender(
      () => {
        const target = this.host.nativeElement.querySelector<HTMLElement>(selector);
        target?.focus({ preventScroll: !scroll });
        if (scroll) this.host.nativeElement.scrollIntoView?.({ block: 'start' });
      },
      { injector: this.injector },
    );
  }

  protected readonly stepIndex = STEP;
  protected readonly total = STEP_COUNT;
}
