// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { fakeSession } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { MEMBERS, member, renderStaff, rowOf, text, useMobile, useNow } from '../../../../testing/staff-members';

describe('Persoal (D4-14): lista', () => {
  useNow();

  it('táboa: usuario (o correo), rol, estado coa paleta e último acceso ou a invitación; axe', async () => {
    const { el } = await renderStaff();
    expect(text(el.querySelector('h1'))).toBe('Persoal');
    const table = el.querySelector('[data-group="current"] table')!;
    expect(text(table.querySelector('caption'))).toBe('Persoal');
    expect([...table.querySelectorAll('thead th')].map((th) => th.getAttribute('scope'))).toEqual(Array(5).fill('col'));
    expect([...table.querySelectorAll('thead th')].map(text).slice(0, 4)).toEqual([
      'Usuario',
      'Rol',
      'Estado',
      'Último acceso',
    ]);
    const cells = (id: string) => {
      const row = rowOf(el, id);
      return {
        email: text(row.querySelector('[data-email]')),
        role: text(row.querySelector('[data-cell="role"]')),
        chip: [row.querySelector('furar-tag')?.getAttribute('data-tone'), text(row.querySelector('furar-tag'))],
        access: text(row.querySelector('[data-access]')),
        danger: !!row.querySelector('.f-staff__access--danger'),
      };
    };
    expect(cells('m1')).toEqual({
      email: 'ana@naron.example',
      role: 'Xestión',
      chip: ['success', 'Activa'],
      access: '27/10/2026, 18:05',
      danger: false,
    });
    // Sen nome nin quen invitou: só o correo.
    expect(rowOf(el, 'm1').textContent).not.toContain('Ana Rey');
    expect(rowOf(el, 'm2').textContent).not.toContain('Invitou');
    expect(cells('m2')).toEqual({
      email: 'iago@naron.example',
      role: 'Recepción',
      chip: ['warn', 'Pendente'],
      access: 'Invitación enviada o 28/10/2026',
      danger: false,
    });
    // Caducada: segue pendente; en vermello e con «Reenviar» á vista.
    expect(cells('m3')).toMatchObject({ chip: ['warn', 'Pendente'], access: 'Invitación caducada', danger: true });
    expect(rowOf(el, 'm3').querySelector('[data-resend]')?.getAttribute('aria-label')).toBe(
      'Reenviar a invitación a eva@naron.example',
    );
    expect(cells('m4')).toMatchObject({ access: 'Non se puido enviar', danger: true });
    expect(rowOf(el, 'm4').querySelector('[data-resend]')).not.toBeNull();
    expect(rowOf(el, 'm2').querySelector('[data-resend]')).toBeNull();
    expect(rowOf(el, 'm1').querySelector('[data-actions]')?.getAttribute('aria-label')).toBe(
      'Accións: ana@naron.example',
    );

    // As desactivadas, á parte, en gris e con «Reactivar» como única acción.
    const deactivated = el.querySelector('[data-group="deactivated"]')!;
    expect(text(deactivated.querySelector('h2'))).toBe('Desactivadas (1)');
    expect(cells('m5')).toMatchObject({ chip: ['muted', 'Desactivada'] });
    expect(rowOf(el, 'm5').querySelector('[data-actions]')?.getAttribute('aria-label')).toBe(
      'Accións: rosa@naron.example',
    );
    await expectNoAxeViolations(el);
  });

  it('en castelán: os mesmos datos cos seus textos; axe', async () => {
    const { el, settle } = await renderStaff();
    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    expect(text(el.querySelector('h1'))).toBe('Personal');
    expect(text(rowOf(el, 'm2').querySelector('furar-tag'))).toBe('Pendiente');
    expect(text(rowOf(el, 'm2').querySelector('[data-access]'))).toBe('Invitación enviada el 28/10/2026');
    expect(text(rowOf(el, 'm3').querySelector('[data-access]'))).toBe('Invitación caducada');
    await expectNoAxeViolations(el);
  });

  it('a plataforma: a mesma intro e as mesmas accións ca a xestión', async () => {
    const { el } = await renderStaff(fakeSession({ platform: true }));
    expect(text(el.querySelector('.f-page-header__intro'))).toBe('Quen entra na xestión do concello e con que rol.');
    expect(rowOf(el, 'm2').querySelector('[data-actions]')).not.toBeNull();
    expect(rowOf(el, 'm4').querySelector('[data-resend]')).not.toBeNull();
    rowOf(el, 'm1').querySelector<HTMLButtonElement>('[data-actions]')!.click();
    await vi.waitFor(() => expect(document.querySelector('[role="menu"]')).not.toBeNull());
    expect(
      [...document.querySelectorAll('[role="menu"] [data-action]')].map((b) => b.getAttribute('data-action')),
    ).toEqual(['role', 'deactivate']);
  });

  it('pendente sen invitación: «Invitación caducada»; anonimizada: «Persoa anonimizada», sen correo; axe', async () => {
    const withoutInvitation = member({
      id: 'm6',
      email: 'lois@naron.example',
      name: undefined,
      role: 'STAFF',
      status: 'INVITED',
      acceptedAt: undefined,
      lastSignInAt: undefined,
      version: 0,
    });
    const anonymized = member({
      id: 'm7',
      email: undefined,
      anonymized: true,
      name: undefined,
      role: 'STAFF',
      status: 'DEACTIVATED',
      acceptedAt: undefined,
      lastSignInAt: undefined,
      deactivatedAt: '2026-10-28T03:45:00Z',
      version: 1,
    });
    const blocked = member({
      id: 'm8',
      email: undefined,
      blocked: true,
      name: undefined,
      role: 'STAFF',
      status: 'DEACTIVATED',
      deactivatedAt: '2025-06-01T09:00:00Z',
      version: 2,
    });
    const { el, settle } = await renderStaff(fakeSession(), [member(), withoutInvitation, anonymized, blocked]);
    const chip = (id: string) => [
      rowOf(el, id).querySelector('furar-tag')?.getAttribute('data-tone'),
      text(rowOf(el, id).querySelector('furar-tag')),
    ];
    expect(chip('m6')).toEqual(['warn', 'Pendente']);
    expect(text(rowOf(el, 'm6').querySelector('[data-access]'))).toBe('Invitación caducada');
    expect(text(rowOf(el, 'm6').querySelector('[data-email]'))).toBe('lois@naron.example');
    expect(rowOf(el, 'm6').querySelector('[data-resend]')).not.toBeNull();

    const gone = rowOf(el, 'm7');
    expect(gone.closest('[data-group="deactivated"]')).not.toBeNull();
    expect(text(gone.querySelector('[data-email]'))).toBe('Persoa anonimizada');
    expect(gone.querySelector('[data-anonymized]')).not.toBeNull();
    expect(text(gone.querySelector('[data-access]'))).toBe('Nunca');
    expect(gone.textContent).not.toContain('anonimizada.invalid');
    expect(gone.querySelector('[data-actions]')).toBeNull();
    expect(chip('m7')).toEqual(['muted', 'Desactivada']);
    // Desactivada hai máis dun ano: a identidade, bloqueada; pódese reactivar.
    const blockedRow = rowOf(el, 'm8');
    expect(text(blockedRow.querySelector('[data-email]'))).toBe('Persoa desactivada');
    expect(blockedRow.querySelector('[data-actions]')).not.toBeNull();
    expect(blockedRow.querySelector('[data-blocked-identity]')).not.toBeNull();
    await expectNoAxeViolations(el);

    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    expect(text(rowOf(el, 'm7').querySelector('[data-email]'))).toBe('Persona anonimizada');
    expect(text(rowOf(el, 'm6').querySelector('furar-tag'))).toBe('Pendiente');
  });

  it('sen persoal: baleiro que leva a «Invitar»', async () => {
    const { el } = await renderStaff(fakeSession({ platform: true }), []);
    expect(text(el.querySelector('furar-status-state'))).toContain('Invita a primeira persoa de Xestión');
    expect(el.querySelector('table')).toBeNull();
  });
});

describe('Persoal (D4-14): no móbil', () => {
  useNow();
  useMobile();

  it('tarxetas no canto da táboa, cos mesmos datos e as accións; axe', async () => {
    const { el } = await renderStaff();
    expect(el.querySelector('table')).toBeNull();
    const card = rowOf(el, 'm4');
    expect(card.tagName).toBe('LI');
    expect(text(card.querySelector('[data-email]'))).toBe('xan@naron.example');
    expect(text(card.querySelector('furar-tag'))).toBe('Pendente');
    expect([...card.querySelectorAll('dt')].map(text)).toEqual(['Rol', 'Último acceso']);
    expect(text(card.querySelector('[data-access]'))).toBe('Non se puido enviar');
    expect(card.querySelector('[data-resend]')).not.toBeNull();
    expect(card.querySelector('[data-actions]')).not.toBeNull();
    expect(MEMBERS.every((m) => rowOf(el, m.id))).toBe(true);
    expect(member().id).toBe('m1');
    await expectNoAxeViolations(el);
  });
});
