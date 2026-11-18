// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { provideTestI18n } from '../../../testing/i18n';
import {
  activityDates,
  ageText,
  birthYearFits,
  dayMonthTime,
  eventWhen,
  hasAgeLimit,
  inLang,
  isScheduled,
  madridDayMinute,
  madridInstant,
  ordinal,
  priceText,
  scheduleWhen,
  placeText,
} from './event-format';
import { publicActivity, publicEvent } from './public-event.fixture';

describe('event-format: datas e prazas dos eventos', () => {
  it('a hora de Madrid pasa a instante e volve igual, no inverno e no verán', () => {
    expect(madridInstant('2026-12-20', 11 * 60)).toBe('2026-12-20T10:00:00.000Z');
    expect(madridInstant('2026-07-04', 18 * 60 + 30)).toBe('2026-07-04T16:30:00.000Z');
    expect(madridDayMinute('2026-12-20T10:00:00Z')).toEqual({ day: '2026-12-20', minute: 660 });
  });

  it('«domingo, 20 de decembro · 11:00» e, curta, «dom. 20 dec. · 11:00»', () => {
    expect(eventWhen('2026-12-20T10:00:00Z', 'gl')).toBe('domingo, 20 de decembro · 11:00');
    expect(eventWhen('2026-12-20T10:00:00Z', 'gl', true)).toBe('dom. 20 dec. · 11:00');
    expect(eventWhen('2026-12-20T10:00:00Z', 'es')).toBe('domingo, 20 de diciembre · 11:00');
  });

  it('a tarxeta dun evento dun día: «20 de decembro · 11:00»', () => {
    expect(dayMonthTime('2026-12-20T10:00:00Z', 'gl')).toBe('20 de decembro · 11:00');
    expect(dayMonthTime('2026-12-20T10:00:00Z', 'es')).toBe('20 de diciembre · 11:00');
  });

  describe('actividades con horario', () => {
    function translate(lang: 'gl' | 'es' = 'gl') {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
      const transloco = TestBed.inject(TranslocoService);
      return (key: string, params?: Record<string, unknown>) => transloco.translate(key, params);
    }

    it('o horario resumido e as datas, longas e curtas', () => {
      const t = translate();
      const activity = publicActivity();
      expect(isScheduled(activity)).toBe(true);
      expect(isScheduled(publicEvent())).toBe(false);
      expect(scheduleWhen(activity.schedule!, 'gl', t)).toBe('Luns e mércores · 17:00–18:00');
      expect(scheduleWhen(activity.schedule!, 'gl', t, true)).toBe('Luns e mércores · 17:00');
      expect(scheduleWhen({ ...activity.schedule!, weekdays: [5, 2, 4] }, 'gl', t)).toBe(
        'Martes, xoves e venres · 17:00–18:00',
      );
      expect(activityDates(activity, 'gl', t)).toBe('Do 6 de outubro ao 24 de xuño');
      expect(activityDates(activity, 'gl', t, true)).toBe('Do 6 out. ao 24 xuño');
    });

    it('a idade: entre, desde, ata, un ano ou sen límite; quen entra', () => {
      const t = translate();
      expect(ageText({ birthYearFrom: 2014, birthYearTo: 2016 }, t)).toBe('Nacidos entre 2014 e 2016');
      expect(ageText({ birthYearFrom: 2014 }, t)).toBe('Nacidos desde 2014');
      expect(ageText({ birthYearTo: 2016 }, t)).toBe('Nacidos ata 2016');
      expect(ageText({ birthYearFrom: 2015, birthYearTo: 2015 }, t)).toBe('Nacidos en 2015');
      expect(ageText({}, t)).toBe('');
      expect(hasAgeLimit({})).toBe(false);
      expect(hasAgeLimit({ birthYearTo: 2016 })).toBe(true);
      expect(birthYearFits({ birthYearFrom: 2014, birthYearTo: 2016 }, 2015)).toBe(true);
      expect(birthYearFits({ birthYearFrom: 2014, birthYearTo: 2016 }, 2011)).toBe(false);
      expect(birthYearFits({}, 1950)).toBe(true);
    });

    it('o prezo co período; gratis, baleiro', () => {
      const t = translate();
      expect(priceText({ priceCents: 2500, pricePeriod: 'QUARTER' }, t).replace(/\s/g, ' ')).toBe(
        '25,00 € ao trimestre',
      );
      expect(priceText({ priceCents: 2500, pricePeriod: 'MONTH' }, t)).toContain('ao mes');
      expect(priceText({ priceCents: 2500, pricePeriod: 'SEASON' }, t)).toContain('á tempada');
      expect(priceText({ priceCents: 500, pricePeriod: 'ONCE' }, t).replace(/\s/g, ' ')).toBe('5,00 €');
      expect(priceText({ pricePeriod: 'QUARTER' }, t)).toBe('');
      const es = translate('es');
      expect(priceText({ priceCents: 2500, pricePeriod: 'SEASON' }, es)).toContain('por temporada');
    });

    it('o lugar coa parte, ou o outro sitio', () => {
      expect(placeText(publicActivity({ partName: 'Vaso pequeno' }))).toBe('Piscina municipal · Vaso pequeno');
      expect(placeText(publicActivity())).toBe('Piscina municipal');
      expect(placeText(publicEvent({ facility: undefined, place: 'Praza do concello' }))).toBe('Praza do concello');
    });

    it('o posto: «3.ª»', () => {
      expect(ordinal(3)).toBe('3.ª');
    });
  });

  it('o texto no idioma de quen mira', () => {
    expect(inLang({ gl: 'Carreira', es: 'Carrera' }, 'es')).toBe('Carrera');
    expect(inLang({ gl: 'Carreira', es: 'Carrera' }, 'gl')).toBe('Carreira');
  });
});
