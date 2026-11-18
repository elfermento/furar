// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { season } from './schedule.fixture';
import { toSeason } from './schedule.store';
import { draftBody, seasonDraftForm } from './season-drafts';

describe('Formulario dunha tempada', () => {
  const winter = toSeason(season());

  it('valida nome e datas: «ata» non vai antes de «desde»', () => {
    const form = seasonDraftForm({ name: '  ', startDate: '2026-09-01', endDate: '2026-08-01', week: winter.week });
    form.markAllAsTouched();
    expect(form.controls.name.errors).toEqual({ 'validation.required': {} });
    form.controls.name.setValue('x'.repeat(101));
    expect(form.controls.name.errors).toEqual({ 'validation.size': { min: 1, max: 100 } });
    expect(form.controls.endDate.errors).toEqual({ 'validation.range': {} });
    form.controls.endDate.setValue('2026-10-01');
    expect(form.controls.endDate.errors).toBeNull();
    form.controls.startDate.setValue(null);
    expect(form.controls.startDate.errors).toEqual({ 'validation.required': {} });
  });

  it('sen data de fin, «Ata» queda baleiro e desactivado e o corpo vai sen endDate', () => {
    const form = seasonDraftForm({ name: 'Inverno', startDate: '2026-09-01', endDate: null, week: winter.week });
    expect(form.controls.endDate.disabled).toBe(true);
    expect(draftBody(form)).toMatchObject({ name: 'Inverno', startDate: '2026-09-01', endDate: undefined });
    form.controls.noEnd.setValue(false);
    expect(form.controls.endDate.enabled).toBe(true);
    expect(form.controls.endDate.errors).toEqual({ 'validation.required': {} });
  });
});
