// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { @Name@Response } from './@name-kebab@.store';
import type { ApiProblem } from '../../core/errors/api-problem';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import { asProblem } from '../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { @Name@Store } from './@name-kebab@.store';

/** Lista accesible do concello actual; quen xestiona ve as ligazóns de alta e edición. */
@Component({
  selector: 'furar-@name-kebab@-list-page',
  imports: [RouterLink, TranslocoPipe, ProblemAlertComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './@name-kebab@-list.page.html',
})
export class @Name@ListPage implements OnInit {
  private readonly store = inject(@Name@Store);
  protected readonly municipality = inject(CurrentMunicipality);
  /** O rol de persoal sae da API (ADR-0027). */
  protected readonly canManage = inject(StaffAccessService).canManage;
  protected readonly items = signal<@Name@Response[] | null>(null);
  protected readonly problem = signal<ApiProblem | null>(null);

  ngOnInit(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    try {
      this.items.set(await this.store.list(slug));
    } catch (error: unknown) {
      this.problem.set(asProblem(error));
    }
  }
}
