// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ApiProblem } from '../../../core/errors/api-problem';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { MEMBERS, STAFF_URL, member } from '../../../../testing/staff-members';
import { StaffMembersStore } from './staff-members.store';

const fail = { status: 500, statusText: 'x' };
const isList = (r: { url: string; method: string }) => r.url === STAFF_URL && r.method === 'GET';

describe('StaffMembersStore', () => {
  let store: StaffMembersStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideTestApi(), StaffMembersStore] });
    store = TestBed.inject(StaffMembersStore);
  });
  afterEach(() => backend().verify());

  it('lista: erro sen lista; despois, un erro non borra o que hai; a última petición manda', async () => {
    let load = store.load('naron');
    expect(store.state()).toBe('loading');
    (await expectRequest(isList)).flush(null, fail);
    await load;
    expect(store.state()).toBe('error');

    load = store.load('naron');
    (await expectRequest(isList)).flush(MEMBERS);
    await load;
    expect(store.state()).toBe('ready');

    load = store.load('naron');
    expect(store.state()).toBe('ready');
    (await expectRequest(isList)).flush(null, fail);
    await load;
    expect(store.state()).toBe('ready');
    expect(store.members()).toHaveLength(MEMBERS.length);

    const first = store.load('naron');
    load = store.load('naron');
    const [old, latest] = backend().match(isList);
    latest.flush([member()]);
    old.flush(MEMBERS);
    await Promise.all([first, load]);
    expect(store.members().map((m) => m.id)).toEqual(['m1']);
  });

  it('invitar: POST co corpo e a persoa nova ao principio', async () => {
    store.members.set([member()]);
    const invite = store.invite('naron', { email: 'nova@naron.example', role: 'STAFF', language: 'es' });
    const post = await expectRequest((r) => r.url === STAFF_URL && r.method === 'POST');
    expect(post.request.body).toEqual({ email: 'nova@naron.example', role: 'STAFF', language: 'es' });
    post.flush(member({ id: 'm9', status: 'INVITED' }));
    expect((await invite).id).toBe('m9');
    expect(store.members().map((m) => m.id)).toEqual(['m9', 'm1']);
  });

  it('reenviar, cambiar o rol, desactivar e reactivar: If-Match coa versión da fila e a resposta substitúe a fila', async () => {
    store.members.set(MEMBERS);
    const iago = MEMBERS[1];

    const resend = store.resend('naron', iago);
    let request = await expectRequest(`${STAFF_URL}/m2/invitation`);
    expect([request.request.method, request.request.headers.get('If-Match')]).toEqual(['POST', '"1"']);
    request.flush({ ...iago, version: 2 });
    await resend;

    const role = store.changeRole('naron', store.members()[1], 'MANAGER');
    request = await expectRequest(`${STAFF_URL}/m2/role`);
    expect([request.request.method, request.request.headers.get('If-Match')]).toEqual(['PUT', '"2"']);
    expect(request.request.body).toEqual({ role: 'MANAGER' });
    request.flush({ ...iago, role: 'MANAGER', version: 3 });
    await role;

    const deactivate = store.deactivate('naron', store.members()[1]);
    request = await expectRequest(`${STAFF_URL}/m2/deactivate`);
    expect([request.request.method, request.request.headers.get('If-Match')]).toEqual(['POST', '"3"']);
    request.flush({ ...iago, role: 'MANAGER', status: 'DEACTIVATED', version: 4 });
    await deactivate;
    expect(store.members()[1]).toMatchObject({ id: 'm2', role: 'MANAGER', status: 'DEACTIVATED', version: 4 });

    const reactivate = store.reactivate('naron', MEMBERS[4]);
    request = await expectRequest(`${STAFF_URL}/m5/reactivate`);
    expect([request.request.method, request.request.headers.get('If-Match')]).toEqual(['POST', '"4"']);
    request.flush({ ...MEMBERS[4], status: 'ACTIVE', deactivatedAt: undefined, version: 5 });
    await reactivate;
    expect(store.members()[4]).toMatchObject({ id: 'm5', role: 'STAFF', status: 'ACTIVE', version: 5 });
    expect(store.members()).toHaveLength(MEMBERS.length);
  });

  it('se a acción falla, rexeita co erro e non toca a lista', async () => {
    store.members.set(MEMBERS);
    const deactivate = store.deactivate('naron', MEMBERS[0]).catch((error: unknown) => error);
    flushProblem(await expectRequest(`${STAFF_URL}/m1/deactivate`), 409, 'staff.last-manager');
    const error = await deactivate;
    expect(error).toBeInstanceOf(ApiProblem);
    expect((error as ApiProblem).code).toBe('staff.last-manager');
    expect(store.members()).toEqual(MEMBERS);
  });
});
