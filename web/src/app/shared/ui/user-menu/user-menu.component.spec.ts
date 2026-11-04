// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { initials } from './initials';
import type { UserMenuItem, UserMenuSwitcher } from './user-menu.component';
import { UserMenuComponent } from './user-menu.component';

@Component({
  imports: [UserMenuComponent],
  template: `<furar-user-menu
    name="Xestora Narón"
    roleName="Xestión"
    context="Concello de Narón"
    email="xestora@naron.example"
    [items]="items"
    (selected)="picked.set($event)"
  />`,
})
class HostComponent {
  readonly picked = signal('');
  readonly items: UserMenuItem[] = [
    { id: 'account', label: 'A miña conta', icon: 'user', href: '/auth/realms/furar/account' },
    { id: 'public', label: 'Ver a web pública', icon: 'globe', link: ['/naron'] },
    { id: 'logout', label: 'Pechar sesión', icon: 'logout', separatorBefore: true },
  ];
}

@Component({
  imports: [UserMenuComponent],
  template: `<furar-user-menu
    name="Xestora Narón"
    [items]="items"
    [switcher]="switcher"
    (selected)="picked.set($event)"
    (switched)="switched.set($event)"
  />`,
})
class SwitcherHostComponent {
  readonly picked = signal('');
  readonly switched = signal('');
  readonly items: UserMenuItem[] = [
    { id: 'account', label: 'A miña conta', icon: 'user', href: '/auth/realms/furar/account' },
    { id: 'logout', label: 'Pechar sesión', icon: 'logout', separatorBefore: true },
  ];
  switcher: UserMenuSwitcher = {
    label: 'Concello',
    current: 'Narón',
    options: [
      { id: 'ares', label: 'Ares', detail: 'Recepción' },
      { id: 'naron', label: 'Narón', detail: 'Xestión', current: true },
    ],
  };
}

describe('UserMenuComponent', () => {
  afterEach(() => document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = '')));

  it('iniciais das dúas primeiras palabras', () => {
    expect(initials('Xestora Narón')).toBe('XN');
    expect(initials('  ana  ')).toBe('A');
    expect(initials('Úrsula de la Fuente')).toBe('ÚD');
    expect(initials('')).toBe('');
  });

  it('pastilla só co nome e as iniciais (sen rol nin chevron); abre o menú con cabeceira e entradas', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const trigger = el.querySelector<HTMLButtonElement>('button')!;
    expect(trigger.hasAttribute('aria-label')).toBe(false);
    expect(trigger.textContent?.replace(/\s+/g, ' ').trim()).toBe('Xestora Narón XN');
    expect(trigger.getAttribute('aria-haspopup')).toBe('menu');
    expect(trigger.lastElementChild?.textContent?.trim()).toBe('XN');
    expect(trigger.querySelector('furar-icon')).toBeNull();
    await expectNoAxeViolations(el);

    trigger.click();
    await fixture.whenStable();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    const panel = document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    expect(panel.querySelector('.f-usermenu__head')?.textContent).toContain('Xestión · Concello de Narón');
    expect(panel.querySelector('[role="menu"]')?.getAttribute('aria-labelledby')).toBe(
      panel.querySelector('.f-usermenu__head')?.id,
    );
    const items = [...panel.querySelectorAll<HTMLElement>('[role="menuitem"]')];
    expect(items.map((i) => i.getAttribute('href'))).toEqual(['/auth/realms/furar/account', '/naron', null]);
    expect(panel.querySelectorAll('[role="separator"]')).toHaveLength(1);
    await expectNoAxeViolations(panel);
    items[2].click();
    expect(fixture.componentInstance.picked()).toBe('logout');
  });

  describe('segundo nivel no mesmo panel (cambiar de concello)', () => {
    const panel = () => document.querySelector<HTMLElement>('.f-usermenu__panel')!;
    const menu = () => panel().querySelector<HTMLElement>('[role="menu"]')!;
    const key = (target: HTMLElement, k: string) =>
      target.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true }));

    async function open() {
      TestBed.configureTestingModule({ providers: [provideRouter([])] });
      const fixture = TestBed.createComponent(SwitcherHostComponent);
      await fixture.whenStable();
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();
      await fixture.whenStable();
      return fixture;
    }

    it('primeiro nivel: «Concello» co actual e ›, á mesma marxe ca as outras filas e sen ✓', async () => {
      await open();
      const row = menu().querySelector<HTMLElement>('[data-item="switch"]')!;
      expect(row.getAttribute('role')).toBe('menuitem');
      expect(row.getAttribute('aria-haspopup')).toBe('menu');
      expect(row.textContent?.replace(/\s+/g, ' ').trim()).toBe('Concello Narón');
      expect(row.querySelectorAll('furar-icon')).toHaveLength(2);
      expect(menu().querySelectorAll('[role="menuitemradio"]')).toHaveLength(0);
      expect(menu().getAttribute('aria-labelledby')).toBe(panel().querySelector('.f-usermenu__head')?.id);
      await expectNoAxeViolations(panel());
    });

    it('clic → a lista no mesmo panel, co foco na volta; ← ou «‹ Concello» volven sen pechar', async () => {
      const fixture = await open();
      menu().querySelector<HTMLElement>('[data-item="switch"]')!.click();
      await fixture.whenStable();
      expect(document.querySelectorAll('.f-usermenu__panel')).toHaveLength(1);
      expect(menu().getAttribute('aria-label')).toBe('Concello');
      const back = menu().querySelector<HTMLElement>('[data-item="back"]')!;
      expect(document.activeElement).toBe(back);
      const options = [...menu().querySelectorAll<HTMLElement>('[role="menuitemradio"]')];
      expect(options.map((o) => o.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
        'Ares Recepción',
        'Narón Xestión',
      ]);
      expect(options.map((o) => o.getAttribute('aria-checked'))).toEqual(['false', 'true']);
      await expectNoAxeViolations(panel());

      key(back, 'ArrowLeft');
      await fixture.whenStable();
      const row = menu().querySelector<HTMLElement>('[data-item="switch"]')!;
      expect(document.activeElement).toBe(row);

      key(row, 'ArrowRight');
      await fixture.whenStable();
      menu().querySelector<HTMLElement>('[data-item="back"]')!.click();
      await fixture.whenStable();
      expect(menu().querySelector('[data-item="switch"]')).not.toBeNull();
    });

    it('cunha ligazón extra («Novo concello»): ao final do segundo nivel, tras unha liña; ← volve', async () => {
      TestBed.configureTestingModule({ providers: [provideRouter([])] });
      const fixture = TestBed.createComponent(SwitcherHostComponent);
      fixture.componentInstance.switcher = {
        ...fixture.componentInstance.switcher,
        label: 'Concellos',
        extra: { id: 'new-municipality', label: 'Novo concello', icon: 'plus', link: ['/plataforma/concellos/novo'] },
      };
      await fixture.whenStable();
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();
      await fixture.whenStable();
      expect(menu().querySelector('[data-item="new-municipality"]')).toBeNull();
      menu().querySelector<HTMLElement>('[data-item="switch"]')!.click();
      await fixture.whenStable();
      const extra = menu().querySelector<HTMLElement>('[data-item="new-municipality"]')!;
      expect(extra.getAttribute('role')).toBe('menuitem');
      expect(extra.getAttribute('href')).toBe('/plataforma/concellos/novo');
      expect(extra.textContent?.trim()).toBe('Novo concello');
      expect(extra.previousElementSibling?.getAttribute('role')).toBe('separator');
      expect(menu().lastElementChild).toBe(extra);
      await expectNoAxeViolations(panel());
      key(extra, 'ArrowLeft');
      await fixture.whenStable();
      expect(document.activeElement).toBe(menu().querySelector('[data-item="switch"]'));
    });

    it('escoller un concello emite o seu id e pecha; ao reabrir, primeiro nivel', async () => {
      const fixture = await open();
      menu().querySelector<HTMLElement>('[data-item="switch"]')!.click();
      await fixture.whenStable();
      menu().querySelector<HTMLElement>('[data-option="ares"]')!.click();
      await fixture.whenStable();
      expect(fixture.componentInstance.switched()).toBe('ares');
      expect(document.querySelector('.f-usermenu__panel')).toBeNull();
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();
      await fixture.whenStable();
      expect(menu().querySelector('[data-item="switch"]')).not.toBeNull();
    });
  });
});
