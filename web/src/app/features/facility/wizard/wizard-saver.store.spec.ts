// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { WIZARD_BASE, libraryPhoto, openWizard, setupWizard, wizardSeason } from '../../../../testing/facility-wizard';
import { SUGGESTED, facility } from '../detail/facility.fixture';
import { fromLibrary } from '../photos/photos';
import { use } from '../uses/uses.fixture';
import { REVIEW, STEP } from './wizard';
import type { WizardStore } from './wizard.store';

const F = `${WIZARD_BASE}/facilities`;
const PADEL = { code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } };

/** Borrador co nome, os deportes e o enderezo; sen usos nin horario. */
const draft = (overrides: Parameters<typeof facility>[0] = {}) =>
  facility({
    id: 'f2',
    name: 'Pista de pádel 3',
    published: false,
    missing: ['USES', 'SCHEDULE'],
    details: { onlineBooking: true, showFreeSlots: false, sports: [PADEL], customFields: {} },
    ...overrides,
  });

describe('WizardSaver: gardado de cada paso do asistente, publicar e descartar', () => {
  let store: WizardStore;

  afterEach(() => backend().verify());

  beforeEach(async () => {
    store = setupWizard();
    await openWizard(store, draft(), { uses: [use({ facilityId: 'f2' })] });
  });

  it('cambiar o nome dun borrador xa creado renoméao co If-Match', async () => {
    store.goTo(STEP.data);
    store.name.setValue('Pista 4');
    store.name.markAsDirty();
    const step = store.next();
    const rename = await expectRequest({ method: 'PUT', url: `${F}/f2/name` });
    expect(rename.request.body).toEqual({ name: 'Pista 4' });
    expect(rename.request.headers.get('If-Match')).toBe('"3"');
    rename.flush(draft({ name: 'Pista 4', version: 4 }));
    expect(await step).toBe(true);
    expect(store.facility()?.name).toBe('Pista 4');
  });

  it('contacto e servizos: os Datos enteiros (cos campos propios doutros) e os servizos marcados', async () => {
    store.goTo(STEP.contact);
    store.data.controls.phone.setValue('981 000 000');
    store.data.markAsDirty();
    const [first] = Object.values(store.amenities.controls);
    first.setValue(true);
    store.amenities.markAsDirty();
    const step = store.next();
    const details = await expectRequest({ method: 'PUT', url: `${F}/f2/details` });
    expect(details.request.body).toMatchObject({ phone: '981 000 000', sports: [{ code: 'padel' }] });
    details.flush(draft({ version: 4 }));
    const amenities = await expectRequest({ method: 'PUT', url: `${F}/f2/amenities` });
    expect((amenities.request.body as { amenities: string[] }).amenities).toHaveLength(1);
    amenities.flush(draft({ version: 5 }));
    expect(await step).toBe(true);
    expect(store.step()).toBe(STEP.photos);
  });

  it('fotos: as subidas coa súa descrición; sen fotos, o debuxo escollido; o suxerido, lista baleira', async () => {
    store.goTo(STEP.photos);
    store.addPhoto(fromLibrary(libraryPhoto('p9', { gl: ' Pista ', es: 'Pista ' })));
    let step = store.next();
    const arranged = await expectRequest({ method: 'PUT', url: `${F}/f2/images` });
    expect(arranged.request.body).toEqual({ images: [{ photoId: 'p9', alt: { gl: 'Pista', es: 'Pista' } }] });
    arranged.flush(draft({ version: 4 }));
    expect(await step).toBe(true);
    // Xa gardada: quitala non a descarta da biblioteca.
    store.removePhoto(store.images()[0]);

    store.goTo(STEP.photos);
    store.chooseDrawing('padel_court');
    expect(store.shownDrawing()).toBe('padel_court');
    step = store.next();
    const withDrawing = await expectRequest({ method: 'PUT', url: `${F}/f2/images` });
    expect(withDrawing.request.body).toEqual({ images: [{ drawing: 'padel_court' }] });
    withDrawing.flush(draft({ version: 5 }));
    expect(await step).toBe(true);

    store.goTo(STEP.photos);
    store.chooseDrawing(SUGGESTED.id);
    expect(store.forms.drawing()).toBeNull();
    expect(store.shownDrawing()).toBe(SUGGESTED.id);
    step = store.next();
    const suggested = await expectRequest({ method: 'PUT', url: `${F}/f2/images` });
    expect(suggested.request.body).toEqual({ images: [] });
    suggested.flush(draft({ version: 6 }));
    expect(await step).toBe(true);

    // Sen cambios, «Seguinte» non garda nada.
    store.goTo(STEP.photos);
    expect(await store.next()).toBe(true);
  });

  it('unha foto subida e quitada sen gardar descártase; unha foto sen descrición non deixa seguir', async () => {
    store.goTo(STEP.photos);
    const photo = fromLibrary(libraryPhoto('p7'));
    store.addPhoto(photo);
    expect(store.ok().photos).toBe(false);
    expect(await store.next()).toBe(false);
    expect(store.missing().map((item) => item.key)).toContain('photos');
    store.removePhoto(photo);
    (await expectRequest({ method: 'DELETE', url: `${WIZARD_BASE}/photos/p7` })).flush(null);
    expect(store.images()).toEqual([]);
    expect(store.ok().photos).toBe(true);
  });

  it('horario: a primeira vez crea «Todo o ano» sen fin, garda a semana e volve ler a instalación', async () => {
    store.goTo(STEP.schedule);
    const step = store.next();
    const created = await expectRequest({ method: 'POST', url: `${F}/f2/seasons` });
    expect(created.request.body).toEqual({
      name: 'Todo o ano',
      startDate: '2026-01-01',
    });
    created.flush(wizardSeason([]));
    const updated = await expectRequest({ method: 'PUT', url: `${F}/f2/seasons/s1` });
    const body = updated.request.body as { week: { day: string; intervals: unknown[] }[] };
    expect(body.week).toHaveLength(8);
    expect(body.week[0]).toEqual({ day: 'MONDAY', intervals: [{ startMinute: 480, endMinute: 1380 }] });
    expect(body.week[7]).toEqual({ day: 'HOLIDAYS', intervals: [] });
    updated.flush({ ...wizardSeason(), version: 2 });
    (await expectRequest({ method: 'GET', url: `${F}/f2` })).flush(draft({ missing: [], version: 4 }));
    expect(await step).toBe(true);
    expect(store.forms.scheduleDirty).toBe(false);
    expect(store.step()).toBe(STEP.rules);
  });

  it('normas e pago por instalación: garda as normas e o pago que cambiaron', async () => {
    store.forms.scope.set('FACILITY');
    store.goTo(STEP.rules);
    store.houseRules.setValue({ gl: 'Calzado limpo', es: 'Calzado limpio' });
    store.houseRules.markAsDirty();
    store.payment.setValue({
      payment: { gl: ' Na oficina ', es: 'En la oficina' },
      refund: { gl: '', es: '' },
    });
    store.payment.markAsDirty();
    const step = store.next();
    const rules = await expectRequest({ method: 'PUT', url: `${F}/f2/house-rules` });
    expect(rules.request.body).toEqual({ gl: 'Calzado limpo', es: 'Calzado limpio' });
    rules.flush(draft({ version: 4 }));
    const notes = await expectRequest({ method: 'PUT', url: `${F}/f2/payment-notes` });
    expect(notes.request.body).toEqual({
      payment: { gl: 'Na oficina', es: 'En la oficina' },
      refund: { gl: '', es: '' },
    });
    notes.flush(draft({ version: 5 }));
    expect(await step).toBe(true);
    expect(store.step()).toBe(REVIEW);
  });

  it('quitar un uso arquívao e volve ler o que falta', async () => {
    const removed = store.removeUse(store.uses.uses()[0]);
    const archive = await expectRequest({ method: 'POST', url: `${F}/f2/uses/u1/archive` });
    expect(archive.request.headers.get('If-Match')).toBe('"2"');
    archive.flush(null);
    (await expectRequest({ method: 'GET', url: `${F}/f2` })).flush(draft({ missing: ['USES', 'SCHEDULE'] }));
    await removed;
    expect(store.uses.uses()).toEqual([]);
    expect(store.ok().uses).toBe(false);
  });

  it('«Publicar» con algo que falta non garda nada', async () => {
    expect(store.missing().map((item) => item.key)).toEqual([]);
    store.name.setValue(' ');
    expect(await store.publish()).toBeNull();
  });

  it('«Publicar» garda o que quede e publica co If-Match da última versión', async () => {
    store.forms.scheduleTouched.set(true);
    const published = store.publish();
    (await expectRequest({ method: 'POST', url: `${F}/f2/seasons` })).flush(wizardSeason([]));
    (await expectRequest({ method: 'PUT', url: `${F}/f2/seasons/s1` })).flush(wizardSeason());
    (await expectRequest({ method: 'GET', url: `${F}/f2` })).flush(draft({ missing: [], version: 7 }));
    const publish = await expectRequest({ method: 'PUT', url: `${F}/f2/publication` });
    expect(publish.request.headers.get('If-Match')).toBe('"7"');
    publish.flush(draft({ missing: [], published: true, version: 8 }));
    expect((await published)?.published).toBe(true);
  });

  it('«Publicar»: se falla un gardado, non publica e deixa o aviso no paso', async () => {
    store.goTo(REVIEW);
    const published = store.publish();
    flushProblem(await expectRequest({ method: 'POST', url: `${F}/f2/seasons` }), 409, 'facility.season-overlap');
    expect(await published).toBeNull();
    expect(store.problem()?.code).toBe('facility.season-overlap');
    expect(store.step()).toBe(REVIEW);
    expect(store.busy()).toBe(false);
  });

  it('«Publicar» que a API rexeita deixa o aviso no paso', async () => {
    const published = store.publish();
    (await expectRequest({ method: 'POST', url: `${F}/f2/seasons` })).flush(wizardSeason([]));
    (await expectRequest({ method: 'PUT', url: `${F}/f2/seasons/s1` })).flush(wizardSeason());
    (await expectRequest({ method: 'GET', url: `${F}/f2` })).flush(draft({ missing: [] }));
    flushProblem(
      await expectRequest({ method: 'PUT', url: `${F}/f2/publication` }),
      409,
      'facility.not-ready-to-publish',
    );
    expect(await published).toBeNull();
    expect(store.problem()?.code).toBe('facility.not-ready-to-publish');
  });

  it('descartar un borrador gardado elimínao; se a API non deixa, queda', async () => {
    let discarded = store.discard();
    (await expectRequest({ method: 'DELETE', url: `${F}/f2` })).flush('', { status: 409, statusText: 'Conflict' });
    expect(await discarded).toBe(false);
    expect(store.problem()).not.toBeNull();
    expect(store.busy()).toBe(false);

    discarded = store.discard();
    const deleted = await expectRequest({ method: 'DELETE', url: `${F}/f2` });
    expect(deleted.request.headers.get('If-Match')).toBe('"3"');
    deleted.flush(null);
    expect(await discarded).toBe(true);
  });
});
