// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { ButtonDirective } from '../ui/button/button.directive';
import { CalloutComponent } from '../ui/callout/callout.component';
import { DeleteButtonComponent } from '../ui/delete-button/delete-button.component';
import { DialogComponent } from '../ui/dialog/dialog.component';
import { FieldGroupComponent } from '../ui/field-group/field-group.component';
import { FieldComponent } from '../ui/field.component';
import { HelpTipComponent } from '../ui/help-tip/help-tip.component';
import { IconComponent } from '../ui/icon/icon.component';
import type { SelectOption } from '../ui/select/select.component';
import { SelectComponent } from '../ui/select/select.component';
import { ToastService } from '../ui/toast/toast.service';
import type { IncidentReportData, IncidentType } from './incident-report';
import { DESCRIPTION_MAX, INCIDENT_TYPES, MAX_PHOTOS, PHOTO_TYPES, dataUrl, photoProblem } from './incident-report';
import { IncidentReportStore } from './incident-report.store';

interface Picked {
  readonly key: number;
  readonly file: File;
  readonly preview: string;
}

let nextId = 0;

/**
 * «Avisar dun problema» (ADR-0036), diálogo e folla no móbil: que pasa (despregable), a
 * descrición (o «?» lembra que non se poñan nomes), nun grupo de campos, e ata tres fotos con
 * miniatura e «Quitar», con «Engadir foto» debaixo delas. O persoal usa o mesmo como «Nova
 * incidencia», co campo «Instalación» enriba. Ao enviar pecha e
 * avisa cun toast; un erro da API queda na capa.
 */
@Component({
  selector: 'furar-incident-report-dialog',
  imports: [
    ButtonDirective,
    CalloutComponent,
    DeleteButtonComponent,
    DialogComponent,
    FieldComponent,
    FieldGroupComponent,
    HelpTipComponent,
    IconComponent,
    SelectComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'incident.report.';
    <furar-dialog
      size="md"
      [heading]="(staff ? t + 'newTitle' : t + 'title') | transloco"
      [subheading]="data.subheading ?? ''"
      [busy]="busy()"
    >
      <form novalidate class="f-incident-form" [id]="ids + '-form'" (submit)="$event.preventDefault(); send()">
        <furar-field-group>
          @if (staff) {
            <furar-field
              data-incident-facility
              [fieldId]="ids + '-facility'"
              [label]="t + 'facility' | transloco"
              [error]="facilityError() ? { key: t + 'facilityRequired', params: {} } : null"
            >
              <furar-select
                [selectId]="ids + '-facility'"
                [label]="t + 'facility' | transloco"
                [placeholder]="t + 'facilityPlaceholder' | transloco"
                [options]="data.facilities ?? []"
                [value]="facility() ?? undefined"
                [required]="true"
                [invalid]="!!facilityError()"
                [describedBy]="facilityError() ? ids + '-facility-error' : null"
                (changed)="facility.set($event)"
              />
            </furar-field>
          }
          <furar-field
            [fieldId]="ids + '-type'"
            [label]="t + 'type' | transloco"
            [error]="typeError() ? { key: t + 'typeRequired', params: {} } : null"
          >
            <furar-select
              data-incident-type
              [selectId]="ids + '-type'"
              [label]="t + 'type' | transloco"
              [placeholder]="t + 'typePlaceholder' | transloco"
              [options]="typeOptions()"
              [value]="type() ?? undefined"
              [required]="true"
              [invalid]="!!typeError()"
              [describedBy]="typeError() ? ids + '-type-error' : null"
              (changed)="type.set($event)"
            />
          </furar-field>
          <furar-field
            [fieldId]="ids + '-description'"
            [label]="t + 'description' | transloco"
            [tip]="t + 'descriptionTip' | transloco"
            [error]="descriptionError() ? { key: t + 'descriptionRequired', params: {} } : null"
          >
            <textarea
              class="f-input f-incident-form__text"
              rows="4"
              data-incident-description
              [id]="ids + '-description'"
              [attr.maxlength]="max"
              [value]="description()"
              [attr.aria-invalid]="descriptionError() ? 'true' : null"
              [attr.aria-describedby]="
                ids + '-description-help' + (descriptionError() ? ' ' + ids + '-description-error' : '')
              "
              (input)="description.set(typed($event))"
            ></textarea>
          </furar-field>
        </furar-field-group>
        <!-- As fotos e, debaixo delas e á dereita, «Engadir foto» (fóra da fila de botóns do pé). -->
        <div class="f-incident-form__group" role="group" [attr.aria-labelledby]="ids + '-photos'">
          <div class="f-field__head">
            <span class="f-label" [id]="ids + '-photos'">{{ t + 'photos' | transloco }}</span>
            <furar-help-tip
              [label]="'ui.helpTip.label' | transloco: { label: (t + 'photos' | transloco) }"
              [text]="t + 'photosTip' | transloco"
            />
          </div>
          @if (photos().length) {
            <ul class="f-incident-photos" [attr.aria-labelledby]="ids + '-photos'" data-incident-photos>
              @for (photo of photos(); track photo.key; let i = $index) {
                <li class="f-incident-photos__item">
                  <img [src]="photo.preview" [alt]="t + 'photo' | transloco: { n: i + 1 }" />
                  <furar-delete-button
                    class="f-incident-photos__remove"
                    [label]="t + 'removePhoto' | transloco: { n: i + 1 }"
                    (pressed)="remove(photo.key)"
                  />
                </li>
              }
            </ul>
          }
          @if (photos().length < maxPhotos) {
            <div class="f-incident-photos__add">
              <label furarButton class="f-incident-photos__pick" data-incident-add-photo>
                <furar-icon name="plus" />
                {{ t + 'addPhoto' | transloco }}
                <input type="file" class="sr-only" [accept]="accept" multiple (change)="pick($event)" />
              </label>
            </div>
          }
          @for (problem of fileProblems(); track $index) {
            <span class="f-field-error" role="alert">{{ problem }}</span>
          }
        </div>
        @if (problem(); as p) {
          <furar-callout tone="danger" live="alert" data-incident-error>{{ p.translated }}</furar-callout>
        }
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton (click)="ref.close(false)">{{ 'common.cancel' | transloco }}</button>
        <button type="submit" furarButton="primary" data-incident-send [attr.form]="ids + '-form'" [busy]="busy()">
          {{ t + 'send' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class IncidentReportDialogComponent {
  protected readonly data = inject<IncidentReportData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<boolean>>(DialogRef);
  private readonly store = inject(IncidentReportStore);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);

  protected readonly ids = `furar-incident-${nextId++}`;
  protected readonly staff = this.data.mode === 'staff';
  private readonly langChanges = toSignal(this.transloco.langChanges$);
  /** «Que pasa», un despregable (Avaría, Limpeza, Seguridade, Outro). */
  protected readonly typeOptions = computed<SelectOption<IncidentType>[]>(() => {
    this.langChanges();
    return INCIDENT_TYPES.map((value) => ({ value, label: this.transloco.translate('incident.types.' + value) }));
  });
  protected readonly max = DESCRIPTION_MAX;
  protected readonly maxPhotos = MAX_PHOTOS;
  protected readonly accept = PHOTO_TYPES.join(',');

  protected readonly facility = signal<string | null>(this.data.facilityId ?? null);
  protected readonly type = signal<IncidentType | null>(null);
  protected readonly description = signal('');
  protected readonly photos = signal<readonly Picked[]>([]);
  protected readonly fileProblems = signal<readonly string[]>([]);
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  private nextKey = 0;

  protected readonly facilityError = computed(() => this.submitted() && !this.facility());
  protected readonly typeError = computed(() => this.submitted() && !this.type());
  protected readonly descriptionError = computed(() => this.submitted() && !this.description().trim());

  protected typed = (event: Event): string => (event.target as HTMLTextAreaElement).value;

  protected async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    const problems: string[] = [];
    const room = MAX_PHOTOS - this.photos().length;
    const accepted: Picked[] = [];
    for (const file of files) {
      const key = photoProblem(file);
      if (key) {
        problems.push(this.transloco.translate(key, { name: file.name }));
      } else if (accepted.length < room) {
        accepted.push({ key: this.nextKey++, file, preview: await dataUrl(file) });
      }
    }
    this.fileProblems.set(problems);
    this.photos.update((current) => [...current, ...accepted]);
  }

  protected remove(key: number): void {
    this.photos.update((current) => current.filter((photo) => photo.key !== key));
  }

  protected async send(): Promise<void> {
    if (this.busy()) return;
    this.submitted.set(true);
    this.problem.set(null);
    const facilityId = this.facility();
    const type = this.type();
    const description = this.description().trim();
    if (!facilityId || !type || !description) {
      // O foco ao primeiro erro lévao furar-dialog (o envío do formulario).
      return;
    }
    const body = {
      facilityId,
      type,
      description,
      photos: this.photos().map((photo) => photo.file),
      ...(this.data.bookingId ? { bookingId: this.data.bookingId } : {}),
    };
    this.busy.set(true);
    try {
      if (this.staff) {
        await this.store.record(this.data.slug, body);
        this.toasts.show('incident.report.recorded');
      } else {
        await this.store.report(this.data.slug, body);
        this.toasts.show('incident.report.sent');
      }
      this.ref.close(true);
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
