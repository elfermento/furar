// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { StaffMembershipsService } from '../../../core/auth/staff-memberships.service';
import { backend, expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { MEMBERS, STAFF_URL, member, renderStaff, rowOf, text, useNow } from '../../../../testing/staff-members';
import { ToastService } from '../../../shared/ui/toast/toast.service';

const toastText = () => {
  const toast = TestBed.inject(ToastService).current();
  return toast ? TestBed.inject(TranslocoService).translate(toast.key, toast.params) : undefined;
};

const confirmDialog = () =>
  vi.waitFor(() => {
    const found = document.querySelector<HTMLElement>('[role="alertdialog"]');
    if (!found) throw new Error('sen confirmación');
    return found;
  });

describe('Persoal: «Engadirme como xestor»', () => {
  useNow();

  it('só a plataforma que aínda non é xestora; xunto a «Invitar»', async () => {
    const platform = await renderStaff(fakeSession({ platform: true }));
    const join = platform.el.querySelector('[data-join]');
    expect(text(join)).toBe('Engadirme como xestor');
    const actions = (e: Element | null | undefined) => e?.closest('.f-page-header__actions');
    expect(actions(join)).toBe(actions(platform.el.querySelector('[data-invite]')));
    expect(actions(join)).not.toBeNull();
    TestBed.resetTestingModule();

    const manager = await renderStaff(fakeSession({ manager: 'naron' }));
    expect(manager.el.querySelector('[data-join]')).toBeNull();
    TestBed.resetTestingModule();

    const both = await renderStaff(fakeSession({ platform: true, manager: 'naron' }));
    expect(both.el.querySelector('[data-join]')).toBeNull();
  });

  it('confirma co que pasa; ao aceptar, a fila, o toast e o acceso ao día; axe', async () => {
    const { el, settle } = await renderStaff(fakeSession({ platform: true }));
    const reload = vi.spyOn(TestBed.inject(StaffMembershipsService), 'reload');
    el.querySelector<HTMLButtonElement>('[data-join]')!.click();
    const confirm = await confirmDialog();
    await settle();
    expect(text(confirm.querySelector('h2'))).toBe('Engadirte como xestor de Narón?');
    expect(text(confirm)).toContain('Desde agora verás as reservas e os datos da veciñanza de Narón.');
    expect(text(confirm)).toContain('Queda anotado no rexistro.');
    expect(text(confirm.querySelector('[data-confirm-accept]'))).toBe('Engadirme');
    await expectNoAxeViolations(confirm);
    backend().expectNone(`${STAFF_URL}/join`);

    confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const post = await expectRequest((r) => r.url === `${STAFF_URL}/join` && r.method === 'POST');
    post.flush(member({ id: 'p1', email: 'plataforma@example.org', name: 'Plataforma', lastSignInAt: undefined }));
    await vi.waitFor(async () => {
      await settle();
      expect(toastText()).toBe('Xa es xestor de Narón');
    });
    expect(text(rowOf(el, 'p1').querySelector('[data-email]'))).toBe('plataforma@example.org');
    expect(text(rowOf(el, 'p1').querySelector('furar-tag'))).toBe('Activa');
    expect(reload).toHaveBeenCalled();
  });

  it('cancelar non pide nada; un erro sae na páxina', async () => {
    const { el, settle } = await renderStaff(fakeSession({ platform: true }), MEMBERS);
    el.querySelector<HTMLButtonElement>('[data-join]')!.click();
    let confirm = await confirmDialog();
    confirm.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!.click();
    await settle();
    backend().expectNone(`${STAFF_URL}/join`);

    el.querySelector<HTMLButtonElement>('[data-join]')!.click();
    confirm = await confirmDialog();
    confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    flushProblem(await expectRequest(`${STAFF_URL}/join`), 409, 'staff.join-personal-account');
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-alert] p'))).toContain('reservas persoais');
    });
  });
});
