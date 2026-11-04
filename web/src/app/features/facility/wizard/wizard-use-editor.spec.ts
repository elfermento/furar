// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { WIZARD_BASE, openWizard, setupWizard } from '../../../../testing/facility-wizard';
import { facility } from '../detail/facility.fixture';
import { use } from '../uses/uses.fixture';
import type { WizardUseEditor } from './wizard-use-editor';

const USES = `${WIZARD_BASE}/facilities/f2/uses`;
const draft = facility({ id: 'f2', published: false });

describe('WizardUseEditor: o uso que se escribe no paso «Usos»', () => {
  let editing: WizardUseEditor;

  beforeEach(async () => {
    const store = setupWizard();
    await openWizard(store, draft, { uses: [use({ facilityId: 'f2' })] });
    editing = store.useEditor;
  });

  afterEach(() => backend().verify());

  it('sen ningún aberto, «Aceptar» non ten nada que gardar', async () => {
    expect(await editing.apply()).toBe(true);
  });

  it('un atallo por partes enche o nome e as partes; un por prazas, a capacidade', () => {
    editing.open(null, { name: 'Media pista', rentalMode: 'PARTS', parts: ['Norte', 'Sur'], capacity: null });
    let form = editing.editor()!.form;
    expect(editing.editor()?.use).toBeNull();
    expect(form.controls.name.value).toBe('Media pista');
    expect(form.controls.parts.getRawValue().map((part) => part.name)).toEqual(['Norte', 'Sur']);

    editing.open(null, { name: 'Nado libre', rentalMode: 'SEATS', parts: [], capacity: 40 });
    form = editing.editor()!.form;
    expect(form.controls.rentalMode.value).toBe('SEATS');
    expect(form.controls.capacity.value).toBe(40);

    editing.close();
    expect(editing.editor()).toBeNull();
  });

  it('un uso novo sen nome non vai á API e queda aberto cos erros', async () => {
    editing.open(null);
    expect(await editing.apply()).toBe(false);
    expect(editing.editor()?.form.controls.name.touched).toBe(true);
  });

  it('un uso novo vai á API ao momento, pecha e volve ler o que falta', async () => {
    editing.open(null, { name: 'Pista enteira', rentalMode: 'WHOLE', parts: [], capacity: null });
    const applied = editing.apply();
    const created = await expectRequest({ method: 'POST', url: USES });
    expect(created.request.body).toMatchObject({ name: 'Pista enteira', rentalMode: 'WHOLE' });
    created.flush(use({ id: 'u9', facilityId: 'f2', name: 'Pista enteira' }));
    (await expectRequest({ method: 'GET', url: `${WIZARD_BASE}/facilities/f2` })).flush(draft);
    expect(await applied).toBe(true);
    expect(editing.editor()).toBeNull();
    expect(editing.busy()).toBe(false);
  });

  it('editar un uso garda co If-Match da súa versión', async () => {
    const saved = use({ facilityId: 'f2' });
    editing.open(saved);
    editing.editor()!.form.controls.name.setValue('Fútbol 11 nocturno');
    const applied = editing.apply();
    const updated = await expectRequest({ method: 'PUT', url: `${USES}/u1` });
    expect(updated.request.headers.get('If-Match')).toBe('"2"');
    updated.flush(use({ facilityId: 'f2', name: 'Fútbol 11 nocturno', version: 3 }));
    (await expectRequest({ method: 'GET', url: `${WIZARD_BASE}/facilities/f2` })).flush(draft);
    expect(await applied).toBe(true);
  });

  it('o nome repetido vai ao campo do nome; o que non ten campo, ao aviso do uso', async () => {
    editing.open(null, { name: 'Fútbol 11', rentalMode: 'WHOLE', parts: [], capacity: null });
    let applied = editing.apply();
    flushProblem(await expectRequest({ method: 'POST', url: USES }), 409, 'facility.use-name-taken');
    expect(await applied).toBe(false);
    expect(editing.editor()?.form.controls.name.hasError('facility.use-name-taken')).toBe(true);
    expect(editing.problem()).toBeNull();

    editing.open(use({ facilityId: 'f2' }));
    applied = editing.apply();
    flushProblem(await expectRequest({ method: 'PUT', url: `${USES}/u1` }), 409, 'facility.use-has-bookings');
    expect(await applied).toBe(false);
    expect(editing.problem()?.code).toBe('facility.use-has-bookings');
    expect(editing.editor()).not.toBeNull();
  });
});
