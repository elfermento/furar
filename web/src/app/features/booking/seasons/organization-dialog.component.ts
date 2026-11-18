// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import type { ChipItem } from '../../../shared/ui/chip-list/chip-list.component';
import { ChipListComponent } from '../../../shared/ui/chip-list/chip-list.component';
import { DeleteButtonComponent } from '../../../shared/ui/delete-button/delete-button.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldGroupComponent } from '../../../shared/ui/field-group/field-group.component';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { EMAIL_PATTERN, PHONE_PATTERN } from '../manage/book-for.store';
import type { OrganizationRequest, OrganizationResponse } from './seasons.store';
import { SeasonsStore } from './seasons.store';

/** O máximo de responsables dunha entidade (ADR-0039, punto 7). */
export const MAX_MANAGERS = 10;

export interface OrganizationDialogData {
  readonly slug: string;
  /** A entidade que se edita; `null`, nova. */
  readonly organization: OrganizationResponse | null;
}

/** Ao pechar despois de gardar ou eliminar: o aviso que mostrar. */
export interface OrganizationDialogResult {
  readonly key: string;
  readonly params?: Record<string, unknown>;
}

/**
 * Nova entidade e editar unha entidade: nunha cápsula o nome, a persoa de contacto e,
 * nunha fila, o correo (obrigatorio: alí chegan os avisos) e o teléfono; os responsables, correos
 * que se escriben e se engaden con Intro, en chips debaixo (como moito dez; o «?» di que fan). Ao
 * editar, «Eliminar» á parte e debaixo: sen historial bórrase; con historial arquívase e queda só o
 * nome; con sesións ou reservas futuras non se pode (o erro da API).
 */
@Component({
  selector: 'furar-organization-dialog',
  imports: [
    ButtonDirective,
    ChipListComponent,
    DeleteButtonComponent,
    DialogComponent,
    FieldComponent,
    FieldGroupComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './organization-dialog.component.html',
})
export class OrganizationDialogComponent {
  protected readonly data = inject<OrganizationDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<OrganizationDialogResult>>(DialogRef);
  private readonly store = inject(SeasonsStore);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly managerInput = viewChild<ElementRef<HTMLInputElement>>('managerInput');

  protected readonly id = 'furar-organization';
  protected readonly current = this.data.organization;
  protected readonly form = new FormGroup({
    name: new FormControl(this.current?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(120)],
    }),
    contactName: new FormControl(this.current?.contactName ?? '', {
      nonNullable: true,
      validators: [Validators.maxLength(120)],
    }),
    email: new FormControl(this.current?.email ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(EMAIL_PATTERN), Validators.maxLength(254)],
    }),
    phone: new FormControl(this.current?.phone ?? '', {
      nonNullable: true,
      validators: [Validators.minLength(3), Validators.maxLength(30), Validators.pattern(PHONE_PATTERN)],
    }),
  });
  /** O correo que se está a escribir nos responsables. */
  protected readonly manager = new FormControl('', { nonNullable: true });
  protected readonly managers = signal<readonly string[]>(this.current?.managers ?? []);
  protected readonly chips = signal<ChipItem[]>([]);
  protected readonly chipStatus = signal('');
  protected readonly managerError = signal<{ key: string; params: Record<string, unknown> } | null>(null);
  protected readonly submitted = signal(false);
  protected readonly saving = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  /** Despois de quitar o último chip, o foco volve ao campo. */
  protected readonly managerFocus = { focus: () => this.managerInput()?.nativeElement.focus() };

  constructor() {
    this.syncChips();
  }

  protected error(name: keyof typeof this.form.controls) {
    return fieldError(this.form.controls[name], this.submitted());
  }

  protected described(name: keyof typeof this.form.controls, suffix = `-${name}`): string | null {
    return describedBy(this.id + suffix, false, this.error(name));
  }

  /** Intro (ou saír do campo con algo escrito): engade o correo se vale e non está. */
  protected addManager(onBlur = false): void {
    const email = this.manager.value.trim();
    if (!email) {
      this.managerError.set(null);
      return;
    }
    if (!EMAIL_PATTERN.test(email) || email.length > 254) {
      if (!onBlur) this.managerError.set({ key: 'errors.validation.pattern', params: {} });
      return;
    }
    const list = this.managers();
    if (list.some((item) => item.toLowerCase() === email.toLowerCase())) {
      this.manager.setValue('');
      this.managerError.set(null);
      return;
    }
    if (list.length >= MAX_MANAGERS) {
      this.managerError.set({ key: 'errors.booking.organization-managers-limit', params: { max: MAX_MANAGERS } });
      return;
    }
    this.managers.set([...list, email]);
    this.manager.setValue('');
    this.managerError.set(null);
    this.chipStatus.set(this.transloco.translate('booking.seasons.organization.managerAdded', { email }));
    this.syncChips();
  }

  protected removeManager(index: number): void {
    const email = this.managers()[index];
    this.managers.update((list) => list.filter((_, i) => i !== index));
    this.managerError.set(null);
    this.chipStatus.set(this.transloco.translate('booking.seasons.organization.managerRemoved', { email }));
    this.syncChips();
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.saving()) return;
    this.submitted.set(true);
    this.problem.set(null);
    // O que quedou escrito nos responsables tamén conta.
    this.addManager();
    this.form.markAllAsTouched();
    // O foco ao primeiro erro pono `furar-dialog` (vixía o envío do formulario).
    if (this.form.invalid || this.managerError()) return;
    this.saving.set(true);
    try {
      const saved = await this.store.saveOrganization(this.data.slug, this.current, this.body());
      this.ref.close({ key: 'booking.seasons.organization.saved', params: { name: saved.name } });
    } catch (error) {
      const problem = asProblem(error);
      if (problem.code === 'booking.organization-name-taken') {
        this.form.controls.name.setErrors({ 'booking.organization-name-taken': {} });
      } else {
        this.problem.set(problem);
      }
    } finally {
      this.saving.set(false);
    }
  }

  /**
   * Eliminar: pregunta (sen historial bórrase; con historial queda arquivada co nome) e elimina
   * coa capa ocupada; o erro (sesións futuras) queda no diálogo.
   */
  protected async remove(organization: OrganizationResponse): Promise<void> {
    const t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);
    this.problem.set(null);
    try {
      const done = await this.dialogs.confirm({
        heading: t('booking.seasons.organization.deleteConfirm.title', { name: organization.name }),
        message: t('booking.seasons.organization.deleteConfirm.text'),
        confirmLabel: t('booking.seasons.organization.delete'),
        tone: 'danger',
        action: () => this.store.deleteOrganization(this.data.slug, organization),
      });
      if (done) this.ref.close({ key: 'booking.seasons.organization.deleted', params: { name: organization.name } });
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  private body(): OrganizationRequest {
    const { name, contactName, email, phone } = this.form.getRawValue();
    return {
      name: name.trim(),
      ...(contactName.trim() ? { contactName: contactName.trim() } : {}),
      email: email.trim(),
      ...(phone.trim() ? { phone: phone.trim() } : {}),
      managers: [...this.managers()],
    };
  }

  private syncChips(): void {
    this.chips.set(this.managers().map((email) => ({ key: email.toLowerCase(), label: email })));
  }
}
