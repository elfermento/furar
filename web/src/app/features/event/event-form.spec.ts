// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { eventBody, eventForm, scheduleRequest } from './event-form';
import { managedActivity, managedEvent } from './event.fixture';

describe('event-form: o formulario do evento', () => {
  it('baleiro pide o nome nos dous idiomas, a data e a instalación', () => {
    const form = eventForm(null);
    form.updateValueAndValidity();
    expect(form.controls.nameGl.hasError('required')).toBe(true);
    expect(form.controls.date.hasError('required')).toBe(true);
    expect(form.controls.facilityId.hasError('required')).toBe(true);
    form.controls.placeKind.setValue('OTHER');
    expect(form.controls.facilityId.errors).toBeNull();
    expect(form.controls.place.hasError('required')).toBe(true);
  });

  it('prazas e prezo: baleiros valen (sen límite, gratis); se non, nos seus rangos', () => {
    const form = eventForm(null);
    form.controls.capacity.setValue('0');
    expect(form.controls.capacity.hasError('event.capacity-invalid')).toBe(true);
    form.controls.capacity.setValue('48');
    expect(form.controls.capacity.errors).toBeNull();
    form.controls.price.setValue('abc');
    expect(form.controls.price.hasError('event.price-invalid')).toBe(true);
    form.controls.price.setValue('5,00');
    expect(form.controls.price.errors).toBeNull();
  });

  it('«Apuntarse ata» só co interruptor e non despois do evento', () => {
    const form = eventForm(null);
    form.patchValue({ date: '2099-12-20', deadlineOn: true });
    expect(form.controls.deadline.hasError('required')).toBe(true);
    form.controls.deadline.setValue('2099-12-21');
    expect(form.controls.deadline.hasError('event.deadline-invalid')).toBe(true);
    form.controls.deadline.setValue('2099-12-18');
    expect(form.controls.deadline.errors).toBeNull();
  });

  it('o corpo: hora de Madrid, lugar e opcionais; a data límite remata ese día', () => {
    const form = eventForm(null);
    form.patchValue({
      nameGl: ' Carreira ',
      nameEs: 'Carrera',
      date: '2099-12-20',
      minute: 11 * 60,
      placeKind: 'OTHER',
      place: 'Praza de Galicia',
      capacity: '200',
      price: '5',
      deadlineOn: true,
      deadline: '2099-12-18',
      published: true,
    });
    expect(eventBody(form)).toEqual({
      kind: 'ONE_DAY',
      nameGl: 'Carreira',
      nameEs: 'Carrera',
      descriptionGl: undefined,
      descriptionEs: undefined,
      startsAt: '2099-12-20T10:00:00.000Z',
      facilityId: undefined,
      place: 'Praza de Galicia',
      capacity: 200,
      priceCents: 500,
      pricePeriod: 'ONCE',
      birthYearFrom: undefined,
      birthYearTo: undefined,
      registrationDeadline: '2099-12-18T22:59:00.000Z',
      published: true,
      approval: false,
    });
  });

  it('edición: enche co evento (na hora de Madrid)', () => {
    const form = eventForm(managedEvent({ priceCents: 500, registrationDeadline: '2099-12-18T22:59:00Z' }));
    const v = form.getRawValue();
    expect(v.date).toBe('2099-12-20');
    expect(v.minute).toBe(660);
    expect(v.facilityId).toBe('f1');
    expect(v.price).toBe('5,00');
    expect(v.deadlineOn).toBe(true);
    expect(v.deadline).toBe('2099-12-18');
  });

  it('con horario: pide os días, as horas, as datas e o uso; non a data dun día', () => {
    const form = eventForm(null);
    form.patchValue({ kind: 'SCHEDULED', facilityId: 'f1' });
    expect(form.controls.date.errors).toBeNull();
    expect(form.controls.weekdays.hasError('booking.series-weekdays-required')).toBe(true);
    expect(form.controls.startMinute.hasError('required')).toBe(true);
    expect(form.controls.firstDate.hasError('required')).toBe(true);
    expect(form.controls.useId.hasError('required')).toBe(true);
    form.patchValue({
      weekdays: 5,
      startMinute: 18 * 60,
      endMinute: 17 * 60,
      firstDate: '2099-10-05',
      lastDate: '2101-01-01',
    });
    expect(form.controls.startMinute.hasError('booking.series-time-invalid')).toBe(true);
    expect(form.controls.firstDate.hasError('booking.series-range-invalid')).toBe(true);
    form.patchValue({ endMinute: 19 * 60, lastDate: '2100-06-16', useId: 'u1', partRequired: true });
    expect(form.controls.partId.hasError('required')).toBe(true);
    form.patchValue({ partId: 'p1' });
    expect(form.valid).toBe(false);
    form.patchValue({ nameGl: 'Natación', nameEs: 'Natación' });
    expect(form.valid).toBe(true);
    expect(scheduleRequest(form, ['2099-12-02'])).toEqual({
      weekdays: [1, 3],
      startMinute: 1080,
      endMinute: 1140,
      firstDate: '2099-10-05',
      lastDate: '2100-06-16',
      useId: 'u1',
      partId: 'p1',
      skip: ['2099-12-02'],
    });
  });

  it('os anos de nacemento: de 1900 ata hoxe e «desde» non despois de «ata»', () => {
    const form = eventForm(null);
    form.controls.birthYearFrom.setValue('1800');
    expect(form.controls.birthYearFrom.hasError('event.birth-years-invalid')).toBe(true);
    form.patchValue({ birthYearFrom: '2016', birthYearTo: '2014' });
    expect(form.controls.birthYearTo.hasError('event.birth-years-invalid')).toBe(true);
    form.patchValue({ birthYearTo: '2017' });
    expect(form.controls.birthYearTo.errors).toBeNull();
  });

  it('editar unha actividade: o horario e o lugar van coma estaban; o prezo co seu período', () => {
    const form = eventForm(managedActivity());
    expect(form.controls.locked.value).toBe(true);
    const body = eventBody(form);
    expect(body.kind).toBe('SCHEDULED');
    expect(body.facilityId).toBe('f2');
    expect(body.schedule).toEqual({
      weekdays: [1, 3],
      startMinute: 1020,
      endMinute: 1080,
      firstDate: '2099-10-05',
      lastDate: '2100-06-16',
      useId: 'u1',
    });
    expect(body.pricePeriod).toBe('QUARTER');
    expect(body.birthYearFrom).toBe(2014);
    expect(body.startsAt).toBeUndefined();
  });

  it('duplicar: « (copia)» no nome, sen límite de inscrición e sen as datas que xa pasaron', () => {
    const copy = eventForm(
      managedActivity({
        schedule: { ...managedActivity().schedule!, firstDate: '2020-10-05', lastDate: '2021-06-16' },
      }),
      true,
    );
    const v = copy.getRawValue();
    expect(v.locked).toBe(false);
    expect(v.nameGl).toBe('Natación · Iniciación (copia)');
    expect(v.weekdays).toBe(5);
    expect([v.firstDate, v.lastDate]).toEqual([null, null]);
    expect(eventForm(managedActivity(), true).getRawValue().firstDate).toBe('2099-10-05');
    expect(eventForm(managedEvent({ startsAt: '2020-01-01T10:00:00Z' }), true).getRawValue().date).toBeNull();
  });
});
