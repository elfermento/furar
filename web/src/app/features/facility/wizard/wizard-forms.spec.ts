// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend } from '../../../../testing/api';
import { setupWizard, wizardWeek } from '../../../../testing/facility-wizard';
import { SUGGESTED, facility } from '../detail/facility.fixture';
import { DAYS } from '../schedule/schedule.store';
import type { Season } from '../schedule/schedule.store';
import { intervalForm } from '../schedule/week-form';
import { DEFAULT_HOURS } from './wizard';
import { WizardForms } from './wizard-forms';

const season = (week: Season['week']): Season => ({
  id: 's1',
  name: 'Todo o ano',
  startDate: '2026-01-01',
  endDate: '2026-12-31',
  version: 1,
  week,
});

describe('WizardForms: formularios do asistente', () => {
  let forms: WizardForms;

  beforeEach(() => {
    setupWizard();
    forms = TestBed.inject(WizardForms);
  });

  afterEach(() => backend().verify());

  const first = () => forms.seasons()[0];
  const days = () => first().week.controls;
  const closeAll = () => days().forEach((day) => day.controls.open.setValue(false));

  it('o paso 1 pide deportes, o enderezo e a chincheta (co mapa)', () => {
    expect(forms.data.controls.sports.hasError('facility.sports-required')).toBe(true);
    expect(forms.location.controls.point.hasError('required')).toBe(true);
    forms.name.setValue('   ');
    expect(forms.name.hasError('required')).toBe(true);
  });

  it('a primeira tempada: sen nome (obrigatorio), desde o 1 de xaneiro e sen fin', () => {
    const season = first();
    expect(forms.seasons()).toHaveLength(1);
    expect(season.form.getRawValue()).toMatchObject({ name: '', noEnd: true, endDate: null });
    expect(season.form.controls.startDate.value).toMatch(/^\d{4}-01-01$/);
    expect(season.hasEnd.value).toBe(false);
    expect(season.form.controls.endDate.disabled).toBe(true);
    expect(season.form.controls.name.hasError('validation.required')).toBe(true);
    expect(forms.scheduleOk()).toBe(false);
    season.form.controls.name.setValue('Todo o ano');
    expect(forms.scheduleOk()).toBe(true);
    // Acender «Ata» pide a data de fin.
    season.hasEnd.setValue(true);
    expect(season.form.controls.endDate.enabled).toBe(true);
    expect(forms.scheduleOk()).toBe(false);
    season.hasEnd.setValue(false);
    expect(forms.scheduleOk()).toBe(true);
    expect(season.body().endDate).toBeUndefined();
  });

  it('«Mesmo horario»: de 8:00 a 23:00 por defecto, de luns a domingo e festivos pechados; o fin antes do inicio non vale', () => {
    const season = first();
    season.form.controls.name.setValue('Todo o ano');
    expect(season.sameHours.getRawValue()).toEqual(DEFAULT_HOURS);
    const week = season.weekBody();
    expect(week.map((row) => row.day)).toEqual([...DAYS]);
    expect(week.at(-1)?.intervals).toEqual([]);
    expect(forms.scheduleOk()).toBe(true);
    expect(season.noOpenDay()).toBe(false);
    season.sameHours.setValue({ startMinute: 600, endMinute: 600 });
    expect(season.sameHours.hasError('schedule.end-before-start')).toBe(true);
    expect(forms.scheduleOk()).toBe(false);
  });

  it('pasar a «Por días» sen ningún día aberto parte do tramo único; volver toma o primeiro tramo aberto', () => {
    const season = first();
    season.form.controls.name.setValue('Todo o ano');
    season.markPristine();
    season.sameHours.setValue({ startMinute: 540, endMinute: 840 });
    closeAll();
    expect(forms.scheduleDirty).toBe(false);
    season.mode.setValue('DAYS');
    expect(forms.scheduleDirty).toBe(true);
    expect(days()[0].controls.intervals.getRawValue()).toEqual([{ startMinute: 540, endMinute: 840 }]);
    expect(season.weekBody()[6]).toEqual({ day: 'SUNDAY', intervals: [{ startMinute: 540, endMinute: 840 }] });

    closeAll();
    expect(season.noOpenDay()).toBe(true);
    expect(forms.scheduleOk()).toBe(false);
    const tuesday = days()[1];
    tuesday.controls.open.setValue(true);
    tuesday.controls.intervals.clear();
    tuesday.controls.intervals.push(intervalForm({ startMinute: 1020, endMinute: 1260 }));
    expect(forms.scheduleOk()).toBe(true);
    season.mode.setValue('SAME');
    expect(season.sameHours.getRawValue()).toEqual({ startMinute: 1020, endMinute: 1260 });

    forms.markSchedulePristine();
    expect(forms.scheduleDirty).toBe(false);
  });

  it('pasar a «Por días» con días abertos conserva a semana; volver sen ningún aberto deixa o tramo único', () => {
    const season = first();
    season.mode.setValue('DAYS');
    days()[5].controls.open.setValue(false);
    season.mode.setValue('SAME');
    season.mode.setValue('DAYS');
    expect(days()[5].controls.open.value).toBe(false);
    closeAll();
    season.sameHours.setValue({ startMinute: 420, endMinute: 600 });
    season.mode.setValue('SAME');
    expect(season.sameHours.getRawValue()).toEqual({ startMinute: 420, endMinute: 600 });
  });

  it('«Engadir tempada»: sen nome nin datas, con fin e co horario da anterior; quitala volve á lista de antes', () => {
    const year = first();
    year.form.controls.name.setValue('Todo o ano');
    year.mode.setValue('DAYS');
    days()[6].controls.open.setValue(false);
    const summer = forms.addSeason();
    expect(forms.seasons()).toEqual([year, summer]);
    expect(summer.form.getRawValue()).toMatchObject({ name: '', startDate: null, endDate: null, noEnd: false });
    expect(summer.mode.value).toBe('DAYS');
    expect(summer.weekBody()[6].intervals).toEqual([]);
    expect(summer.copiedFrom).toBe(year.key);
    expect(forms.scheduleDirty).toBe(true);
    expect(forms.scheduleOk()).toBe(false);

    summer.form.controls.name.setValue('Verán');
    summer.form.controls.startDate.setValue('2026-07-01');
    // Sen «Ata» aínda, o día de inicio xa pisa «Todo o ano» (sen fin).
    expect(forms.overlapOf(summer)).toBe(year);
    expect(forms.overlapOf(year)).toBeNull();
    summer.form.controls.endDate.setValue('2026-08-31');
    expect(forms.scheduleOk()).toBe(false);
    year.hasEnd.setValue(true);
    year.form.controls.endDate.setValue('2026-06-30');
    expect(forms.overlapOf(summer)).toBeNull();
    expect(forms.scheduleOk()).toBe(true);

    forms.removeSeason(summer);
    expect(forms.seasons()).toEqual([year]);
    // Nova e sen gardar: non hai nada que borrar na API.
    expect(forms.removedSeasons().size).toBe(0);
  });

  it('enche cos datos gardados: servizos, normas, pago, debuxo escollido e as tempadas (a semana «Por días»)', () => {
    const chosen = { ...SUGGESTED, id: 'padel_court' };
    const saturday = wizardWeek().map((row) =>
      row.day === 'SATURDAY' ? { ...row, intervals: [{ startMinute: 600, endMinute: 840 }] } : row,
    ) as Season['week'];
    forms.fill(
      facility({
        amenities: ['PARKING'],
        houseRules: { gl: 'Calzado limpo', es: 'Calzado limpio' },
        paymentNotes: { payment: { gl: 'Na oficina', es: 'En la oficina' } },
        images: [chosen],
      }),
      [
        season(saturday),
        { ...season(wizardWeek() as Season['week']), id: 's2', name: 'Verán', startDate: '2027-07-01', endDate: null },
      ],
    );
    expect(forms.amenities.getRawValue()).toMatchObject({ PARKING: true });
    expect(forms.houseRules.getRawValue()).toEqual({ gl: 'Calzado limpo', es: 'Calzado limpio' });
    expect(forms.payment.getRawValue()).toEqual({
      payment: { gl: 'Na oficina', es: 'En la oficina' },
      refund: { gl: '', es: '' },
    });
    expect(forms.drawing()).toBe('padel_court');
    expect(forms.images()).toEqual([]);
    const [year, summer] = forms.seasons();
    expect(year.id).toBe('s1');
    expect(year.mode.value).toBe('DAYS');
    expect(year.hasEnd.value).toBe(true);
    expect(year.weekBody()[5].intervals).toEqual([{ startMinute: 600, endMinute: 840 }]);
    expect(summer.mode.value).toBe('SAME');
    expect(summer.hasEnd.value).toBe(false);
    expect(forms.scheduleDirty).toBe(false);
    expect(forms.scheduleOk()).toBe(true);
    forms.removeSeason(summer);
    expect([...forms.removedSeasons()]).toEqual(['s2']);
    expect(forms.scheduleDirty).toBe(true);
  });

  it('enche sen tempada nin enderezo: o debuxo suxerido é «ningún»', () => {
    forms.fill(facility({ address: undefined, paymentNotes: undefined }), [], '2026-11-03');
    expect(forms.drawing()).toBeNull();
    expect(forms.seasons()).toHaveLength(1);
    expect(forms.seasons()[0].mode.value).toBe('SAME');
    expect(forms.seasons()[0].form.controls.startDate.value).toBe('2026-01-01');
    expect(forms.payment.getRawValue().refund).toEqual({ gl: '', es: '' });
  });
});
