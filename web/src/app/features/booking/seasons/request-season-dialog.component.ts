// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { MySeriesRequestResponse, SeriesRequestRequest } from './my-organizations.store';
import { SeasonFormComponent } from './season-form.component';
import type { DraftErrors } from './seasons';
import { EMPTY_DRAFT, draftErrors, requestBody } from './seasons';

export interface RequestSeasonData {
  readonly slug: string;
  /** O nome da entidade (debaixo do título). */
  readonly organizationName: string;
  /** Manda a solicitude (a pestana da entidade sabe a quen). */
  readonly send: (body: SeriesRequestRequest) => Promise<MySeriesRequestResponse>;
}

/**
 * «Solicitar tempada» da entidade: a entidade debaixo do título; Instalación (calquera
 * que se poida reservar), Que, Parte, Días, Desde e Ata e Datas; a Nota; «Enviar solicitude». Sen
 * vista previa dos choques: decide a xestión ao aprobar. Pecha coa solicitude enviada.
 */
@Component({
  selector: 'furar-request-season-dialog',
  imports: [ButtonDirective, DialogComponent, ProblemAlertComponent, SeasonFormComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'booking.seasons.ask.';
    <furar-dialog [heading]="t + 'title' | transloco" [subheading]="data.organizationName" [busy]="busy()">
      <form novalidate class="gap-form grid" id="furar-request-season" (submit)="$event.preventDefault(); send()">
        <furar-season-form note [slug]="data.slug" [(draft)]="draft" [errors]="visibleErrors()" />
        <furar-problem-alert [problem]="problem()" />
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-ask-cancel (click)="ref.close()">
          {{ 'common.cancel' | transloco }}
        </button>
        <button type="submit" form="furar-request-season" furarButton="primary" data-ask-send [busy]="busy()">
          {{ t + 'send' | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class RequestSeasonDialogComponent {
  protected readonly data = inject<RequestSeasonData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<MySeriesRequestResponse>>(DialogRef);
  private readonly form = viewChild.required(SeasonFormComponent);
  private readonly today = madridToday();

  protected readonly draft = signal(EMPTY_DRAFT);
  protected readonly submitted = signal(false);
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly errors = computed<DraftErrors>(() =>
    draftErrors(this.draft(), { organization: false, part: this.form().partRequired(), today: this.today }),
  );
  protected readonly visibleErrors = computed<DraftErrors>(() => (this.submitted() ? this.errors() : {}));

  protected async send(): Promise<void> {
    this.submitted.set(true);
    this.problem.set(null);
    const body = requestBody(this.draft());
    // O foco ao primeiro erro pono `furar-dialog` (vixía o envío do formulario).
    if (Object.keys(this.errors()).length || !body || this.busy()) return;
    this.busy.set(true);
    try {
      this.ref.close(await this.data.send(body));
    } catch (error) {
      this.problem.set(asProblem(error));
    } finally {
      this.busy.set(false);
    }
  }
}
