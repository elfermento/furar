// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { expectRequest, fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { AccountPage } from './account.page';

const MEMBERSHIPS = [
  { slug: 'ares', name: 'Ares', role: 'STAFF' as const },
  { slug: 'naron', name: 'Narón', role: 'MANAGER' as const },
];

async function render(session = fakeSession(), place = true) {
  TestBed.configureTestingModule({ providers: provideTestApi(session) });
  if (place) TestBed.inject(CurrentMunicipality).set(municipality());
  const fixture = TestBed.createComponent(AccountPage);
  await fixture.whenStable();
  return { fixture, el: fixture.nativeElement as HTMLElement };
}

const text = (el: Element | null | undefined) => el?.textContent?.replace(/\s+/g, ' ').trim();

describe('A miña conta', () => {
  it('persoal: quen é, o último acceso, os seus concellos co actual e as accións; pasa axe', async () => {
    const { el } = await render(
      fakeSession({ manager: 'naron', previousSignInAt: '2026-11-17T17:05:00Z', memberships: MEMBERSHIPS }),
    );
    expect(text(el.querySelector('h1'))).toBe('A miña conta');
    expect(text(el.querySelector('[data-account-name]'))).toBe('Xestora Narón');
    expect(text(el.querySelector('[data-account-email]'))).toBe('xestora@naron.example');
    // ENS op.acc.6 R5: o acceso anterior, en hora de Madrid.
    expect(text(el.querySelector('[data-last-sign-in]'))).toBe('Último acceso: 17/11/2026, 18:05');
    const places = [...el.querySelectorAll<HTMLAnchorElement>('[data-account-place]')];
    expect(places.map((a) => [text(a), a.getAttribute('href')])).toEqual([
      ['Concello de Ares Recepción', '/ares/xestion/reservas'],
      ['Concello de Narón Actual Xestión', '/naron/xestion/concello'],
    ]);
    expect(el.querySelectorAll('[data-account-current]')).toHaveLength(1);
    expect(el.querySelector('[data-account-security]')?.getAttribute('href')).toBe(
      'http://localhost/auth/realms/furar/account/account-security/signing-in',
    );
    const remove = el.querySelector('[data-account-delete]')!;
    expect(text(remove)).toBe('Eliminar a miña conta');
    expect(remove.closest('furar-action-item')?.getAttribute('data-tone')).toBe('danger');
    await expectNoAxeViolations(el);
  });

  it('veciñanza: sen concellos nin último acceso; a seguridade e eliminar a conta, si', async () => {
    const { el } = await render(fakeSession({}));
    expect(el.querySelector('[data-account-place]')).toBeNull();
    expect(el.querySelector('[data-last-sign-in]')).toBeNull();
    expect(el.querySelector('[data-account-security]')).not.toBeNull();
    expect(el.querySelector('[data-account-delete]')).not.toBeNull();
    await expectNoAxeViolations(el);
  });

  it('sen concello escollido: os concellos, ningún «Actual» e sen último acceso', async () => {
    const { el } = await render(fakeSession({ manager: 'naron', memberships: MEMBERSHIPS }), false);
    expect(el.querySelectorAll('[data-account-place]')).toHaveLength(2);
    expect(el.querySelector('[data-account-current]')).toBeNull();
    expect(el.querySelector('[data-last-sign-in]')).toBeNull();
  });

  it('«Eliminar a miña conta»: pregunta á API e, se se pode, vai a Keycloak', async () => {
    const session = fakeSession({});
    const { fixture, el } = await render(session);
    el.querySelector<HTMLButtonElement>('[data-account-delete]')!.click();
    (await expectRequest('/api/v1/me/account-deletion')).flush({ allowed: true, blockers: [] });
    await vi.waitFor(async () => {
      await fixture.whenStable();
      expect(session.deleteAccount).toHaveBeenCalled();
    });
  });
});
