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

  const days = () => forms.week().controls.week.controls;
  const closeAll = () => days().forEach((day) => day.controls.open.setValue(false));

  it('o paso 1 pide deportes e o enderezo, a chincheta (co mapa) e o código INE do concello', () => {
    expect(forms.data.controls.sports.hasError('facility.sports-required')).toBe(true);
    expect(forms.location.controls.point.hasError('required')).toBe(true);
    expect(forms.location.controls.ineCode.value).toBe('15054');
    forms.name.setValue('   ');
    expect(forms.name.hasError('required')).toBe(true);
  });

  it('«Mesmo horario»: de 8:00 a 23:00 por defecto, de luns a domingo e festivos pechados; o fin antes do inicio non vale', () => {
    expect(forms.sameHours.getRawValue()).toEqual(DEFAULT_HOURS);
    const week = forms.weekBody();
    expect(week.map((row) => row.day)).toEqual([...DAYS]);
    expect(week.at(-1)?.intervals).toEqual([]);
    expect(forms.scheduleOk()).toBe(true);
    expect(forms.noOpenDay()).toBe(false);
    forms.sameHours.setValue({ startMinute: 600, endMinute: 600 });
    expect(forms.sameHours.hasError('schedule.end-before-start')).toBe(true);
    expect(forms.scheduleOk()).toBe(false);
  });

  it('pasar a «Por días» sen ningún día aberto parte do tramo único; volver toma o primeiro tramo aberto', () => {
    forms.sameHours.setValue({ startMinute: 540, endMinute: 840 });
    closeAll();
    expect(forms.scheduleDirty).toBe(false);
    forms.scheduleMode.setValue('DAYS');
    expect(forms.scheduleTouched()).toBe(true);
    expect(forms.scheduleDirty).toBe(true);
    expect(days()[0].controls.intervals.getRawValue()).toEqual([{ startMinute: 540, endMinute: 840 }]);
    expect(forms.weekBody()[6]).toEqual({ day: 'SUNDAY', intervals: [{ startMinute: 540, endMinute: 840 }] });

    closeAll();
    expect(forms.noOpenDay()).toBe(true);
    expect(forms.scheduleOk()).toBe(false);
    const tuesday = days()[1];
    tuesday.controls.open.setValue(true);
    tuesday.controls.intervals.clear();
    tuesday.controls.intervals.push(intervalForm({ startMinute: 1020, endMinute: 1260 }));
    expect(forms.scheduleOk()).toBe(true);
    forms.scheduleMode.setValue('SAME');
    expect(forms.sameHours.getRawValue()).toEqual({ startMinute: 1020, endMinute: 1260 });

    forms.markSchedulePristine();
    expect(forms.scheduleDirty).toBe(false);
  });

  it('pasar a «Por días» con días abertos conserva a semana; volver sen ningún aberto deixa o tramo único', () => {
    forms.scheduleMode.setValue('DAYS');
    days()[5].controls.open.setValue(false);
    forms.scheduleMode.setValue('SAME');
    forms.scheduleMode.setValue('DAYS');
    expect(days()[5].controls.open.value).toBe(false);
    closeAll();
    forms.sameHours.setValue({ startMinute: 420, endMinute: 600 });
    forms.scheduleMode.setValue('SAME');
    expect(forms.sameHours.getRawValue()).toEqual({ startMinute: 420, endMinute: 600 });
  });

  it('enche cos datos gardados: servizos, normas, pago, debuxo escollido e a semana «Por días»', () => {
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
      season(saturday),
    );
    expect(forms.amenities.getRawValue()).toMatchObject({ PARKING: true });
    expect(forms.houseRules.getRawValue()).toEqual({ gl: 'Calzado limpo', es: 'Calzado limpio' });
    expect(forms.payment.getRawValue()).toEqual({
      payment: { gl: 'Na oficina', es: 'En la oficina' },
      refund: { gl: '', es: '' },
    });
    expect(forms.drawing()).toBe('padel_court');
    expect(forms.images()).toEqual([]);
    expect(forms.scheduleMode.value).toBe('DAYS');
    expect(forms.scheduleDirty).toBe(false);
    expect(forms.weekBody()[5].intervals).toEqual([{ startMinute: 600, endMinute: 840 }]);
  });

  it('enche sen tempada nin enderezo: o debuxo suxerido é «ningún» e o código INE, o do concello', () => {
    forms.fill(facility({ address: undefined, paymentNotes: undefined }), null);
    expect(forms.drawing()).toBeNull();
    expect(forms.location.controls.ineCode.value).toBe('15054');
    expect(forms.scheduleMode.value).toBe('SAME');
    expect(forms.payment.getRawValue().refund).toEqual({ gl: '', es: '' });
  });
});
