// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { SUMMER, season } from './schedule.fixture';
import { toSeason } from './schedule.store';
import { SeasonDrafts, draftBody, draftWeek, seasonDraftForm } from './season-drafts';

describe('Tempadas no modo edición (SeasonDrafts)', () => {
  const winter = toSeason(season());
  const summer = toSeason(SUMMER);

  it('parte das gardadas en orde de datas e sen cambios', () => {
    const drafts = new SeasonDrafts();
    drafts.start([summer, winter]);
    expect(drafts.list().map((d) => d.key)).toEqual(['s1', 's2']);
    expect(drafts.dirty()).toBe(false);
    expect(drafts.validate()).toBe(true);
  });

  it('unha nova copia a semana e queda pendente; eliminala desfaise sen máis', () => {
    const drafts = new SeasonDrafts();
    drafts.start([winter]);
    const key = drafts.add({ name: 'Outono', startDate: '2027-07-01', endDate: '2027-09-30' }, winter.week);
    expect(drafts.dirty()).toBe(true);
    const form = drafts.get(key)!.form;
    expect(draftWeek(form)[5].intervals).toHaveLength(2);
    expect(draftBody(form)).toMatchObject({ name: 'Outono', startDate: '2027-07-01', endDate: '2027-09-30' });
    drafts.remove(key);
    expect(drafts.get(key)).toBeUndefined();
    expect(drafts.removed().size).toBe(0);
    expect(drafts.dirty()).toBe(false);
  });

  it('eliminar unha gardada déixaa para borrar; forget e replace seguen á API', () => {
    const drafts = new SeasonDrafts();
    drafts.start([winter, summer]);
    drafts.remove('s2');
    expect([...drafts.removed()]).toEqual(['s2']);
    drafts.forget('s2');
    expect(drafts.removed().size).toBe(0);
    const key = drafts.add({ name: 'Nova', startDate: '2028-01-01', endDate: '2028-01-31' }, []);
    const form = drafts.get(key)!.form;
    drafts.replace(key, toSeason(season({ id: 's9', name: 'Nova' })), form);
    expect(drafts.get('s9')?.saved?.id).toBe('s9');
    drafts.clear();
    expect(drafts.list()).toEqual([]);
  });

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
    const drafts = new SeasonDrafts();
    drafts.start([winter]);
    drafts.get('s1')!.form.controls.name.setValue('');
    expect(drafts.validate()).toBe(false);
  });
});
