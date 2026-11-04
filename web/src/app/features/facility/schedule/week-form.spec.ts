// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApiProblem } from '../../../core/errors/api-problem';
import { season } from './schedule.fixture';
import { toSeason } from './schedule.store';
import { intervalsText, weekdayName } from './week-format';
import { draftBody, seasonDraftForm } from './season-drafts';
import { addInterval, applyWeekErrors, dayError, removeInterval, weekForm } from './week-form';

const SATURDAY = 5;
const SUNDAY = 6;
const HOLIDAYS = 7;

describe('Semana dunha tempada: formulario e regras (D1-23a)', () => {
  const winter = () => toSeason(season());

  it('toSeason deixa sempre as 8 filas na orde da API e os tramos por inicio', () => {
    const shuffled = toSeason(
      season({
        week: [
          {
            day: 'SUNDAY',
            intervals: [
              { startMinute: 960, endMinute: 1200 },
              { startMinute: 540, endMinute: 840 },
            ],
          },
        ],
      }),
    );
    expect(shuffled.week.map((d) => d.day)).toEqual([
      'MONDAY',
      'TUESDAY',
      'WEDNESDAY',
      'THURSDAY',
      'FRIDAY',
      'SATURDAY',
      'SUNDAY',
      'HOLIDAYS',
    ]);
    expect(shuffled.week[SUNDAY].intervals.map((i) => i.startMinute)).toEqual([540, 960]);
    expect(shuffled.week[0].intervals).toEqual([]);
  });

  it('un día pechado desactiva os tramos sen borralos; abrilo propón 9:00–14:00', () => {
    const form = seasonDraftForm(winter());
    const holidays = form.controls.week.at(HOLIDAYS);
    expect(holidays.controls.open.value).toBe(false);
    holidays.controls.open.setValue(true);
    expect(holidays.controls.intervals.getRawValue()).toEqual([{ startMinute: 540, endMinute: 840 }]);

    const saturday = form.controls.week.at(SATURDAY);
    saturday.controls.open.setValue(false);
    expect(saturday.controls.intervals.disabled).toBe(true);
    expect(draftBody(form).week[SATURDAY].intervals).toEqual([]);
    saturday.controls.open.setValue(true);
    expect(saturday.controls.intervals.length).toBe(2);
  });

  it('valida por fila: sen tramos, fin antes do inicio e tramos que se pisan (co tramo que falla)', () => {
    const form = weekForm(winter());
    const sunday = form.controls.week.at(SUNDAY);
    removeInterval(sunday, 0);
    expect(dayError(sunday)?.error.key).toBe('facility.schedule.errors.noIntervals');
    expect(form.invalid).toBe(true);

    addInterval(sunday);
    sunday.controls.intervals.at(0).controls.endMinute.setValue(480);
    expect(dayError(sunday)).toEqual({
      error: { key: 'facility.schedule.errors.endBeforeStart', params: { n: 1 } },
      index: 0,
    });

    const saturday = form.controls.week.at(SATURDAY);
    saturday.controls.intervals.at(1).controls.startMinute.setValue(780);
    expect(dayError(saturday)).toEqual({
      error: { key: 'facility.schedule.errors.overlap', params: { n: 2 } },
      index: 1,
    });
    expect(dayError(form.controls.week.at(0))).toBeNull();
  });

  it('«+ Tramo» empeza unha hora despois do último e non pasa das 24:00', () => {
    const form = weekForm(winter());
    const saturday = form.controls.week.at(SATURDAY);
    addInterval(saturday);
    expect(saturday.controls.intervals.at(2).getRawValue()).toEqual({ startMinute: 1260, endMinute: 1440 });
    expect(dayError(saturday)).toBeNull();
  });

  it('o corpo leva nome e datas da tempada e a semana por orde de inicio', () => {
    const form = seasonDraftForm(winter());
    const saturday = form.controls.week.at(SATURDAY);
    saturday.controls.intervals.at(0).setValue({ startMinute: 1080, endMinute: 1200 });
    saturday.controls.intervals.at(1).setValue({ startMinute: 600, endMinute: 720 });
    const body = draftBody(form);
    expect(body).toMatchObject({ name: 'Inverno', startDate: '2026-09-01', endDate: '2027-06-30' });
    expect(body.week).toHaveLength(8);
    expect(body.week[SATURDAY]).toEqual({
      day: 'SATURDAY',
      intervals: [
        { startMinute: 600, endMinute: 720 },
        { startMinute: 1080, endMinute: 1200 },
      ],
    });
    expect(body.week[HOLIDAYS]).toEqual({ day: 'HOLIDAYS', intervals: [] });
  });

  it('os erros da API van ao seu tramo e din que días abrir', () => {
    const form = weekForm(winter());
    const problem = new ApiProblem(
      422,
      'facility.interval-overlap',
      {},
      [{ field: 'week[5].intervals[1]', code: 'facility.interval-overlap', params: {} }],
      undefined,
      '',
    );
    expect(applyWeekErrors(form, problem)).toEqual({ days: [SATURDAY], unmatched: 0 });
    expect(dayError(form.controls.week.at(SATURDAY))).toEqual({
      error: { key: 'errors.facility.interval-overlap', params: {} },
      index: 1,
    });
  });

  it('textos: nomes dos días por Intl e tramos co formato do idioma', () => {
    expect(weekdayName(0, 'gl')).toBe('Luns');
    expect(weekdayName(SATURDAY, 'es')).toBe('Sábado');
    expect(intervalsText(winter().week[SATURDAY].intervals, 'gl')).toBe('9:00 – 14:00 · 16:00 – 20:00');
    expect(intervalsText([{ startMinute: 1320, endMinute: 1440 }], 'es')).toBe('22:00 – 24:00');
  });
});
