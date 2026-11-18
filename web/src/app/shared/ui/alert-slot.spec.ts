// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ApiProblem } from '../../core/errors/api-problem';
import { provideTestI18n } from '../../../testing/i18n';
import { FormErrorsSummaryComponent } from './form-errors-summary.component';
import { ProblemAlertComponent } from './problem-alert.component';

describe('avisos de erro baleiros', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: provideTestI18n() }));

  it('furar-form-errors-summary: baleiro marca data-empty e garda o role="alert"', async () => {
    const fixture = TestBed.createComponent(FormErrorsSummaryComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.classList).toContain('f-alert-slot');
    expect(el.hasAttribute('data-empty')).toBe(true);
    expect(el.querySelector('[role="alert"]')).not.toBeNull();

    fixture.componentRef.setInput('count', 2);
    await fixture.whenStable();
    expect(el.hasAttribute('data-empty')).toBe(false);
    expect(el.querySelector('furar-callout')).not.toBeNull();
  });

  it('furar-problem-alert: baleiro marca data-empty; cun problema, non', async () => {
    const fixture = TestBed.createComponent(ProblemAlertComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.hasAttribute('data-empty')).toBe(true);
    expect(el.querySelector('[role="alert"]')).not.toBeNull();

    fixture.componentRef.setInput('problem', new ApiProblem(0, 'common.unexpected', {}, [], undefined, ''));
    await fixture.whenStable();
    expect(el.hasAttribute('data-empty')).toBe(false);
    expect(el.querySelector('furar-callout')).not.toBeNull();
  });
});
