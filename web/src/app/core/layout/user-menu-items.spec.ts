// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { municipalitySwitcher, platformSwitcher } from './municipality-switcher';
import type { UserMenuContext } from './user-menu-items';
import { userMenuItems } from './user-menu-items';

/** A clave como texto: así o test mira que entrada é, non a tradución. */
const t = (key: string) => key;

const base: UserMenuContext = {
  slug: 'naron',
  staffRole: null,
  workAccount: false,
  platformAdmin: false,
  inManagement: false,
  municipalities: null,
};

const ids = (c: Partial<UserMenuContext>) => userMenuItems({ ...base, ...c }, t).map((i) => i.id);

describe('menú da persoa: a entrada á xestión, primeira', () => {
  it('plataforma sen ningún concello: «Novo concello» en negra e unha liña antes da conta', () => {
    const items = userMenuItems({ ...base, slug: null, platformAdmin: true, municipalities: 0 }, t);
    expect(items[0]).toMatchObject({
      id: 'new-municipality',
      label: 'session.newMunicipality',
      link: ['/plataforma/concellos/novo'],
      strong: true,
    });
    expect(items[1]).toMatchObject({ id: 'account', separatorBefore: true });
    expect(items.map((i) => i.id)).toEqual(['new-municipality', 'account', 'platform-activity', 'logout']);
  });

  it('plataforma con concellos (ou mentres non o sabe): sen «Novo concello» no primeiro nivel', () => {
    const c = { platformAdmin: true, workAccount: true };
    expect(ids({ ...c, municipalities: 2 })).toEqual(['account', 'platform-indicators', 'platform-activity', 'logout']);
    expect(ids({ ...c, municipalities: null })).toEqual([
      'account',
      'platform-indicators',
      'platform-activity',
      'logout',
    ]);
  });

  it('plataforma: «Indicadores da plataforma» coa icona do gráfico, xusto antes do rexistro', () => {
    const items = userMenuItems({ ...base, platformAdmin: true, workAccount: true, municipalities: 3 }, t);
    const index = items.findIndex((i) => i.id === 'platform-indicators');
    expect(items[index]).toMatchObject({
      label: 'session.platformIndicators',
      icon: 'chart',
      link: ['/', 'plataforma', 'indicadores'],
    });
    expect(items[index + 1]?.id).toBe('platform-activity');
    expect(ids({ staffRole: 'MANAGER', workAccount: true })).not.toContain('platform-indicators');
  });

  it('Xestión na web pública: «Xestión do concello» á sección «Concello», e a conta', () => {
    const items = userMenuItems({ ...base, staffRole: 'MANAGER', workAccount: true }, t);
    expect(items[0]).toMatchObject({ id: 'manage', link: ['/naron/xestion/concello'], strong: true });
    expect(items.map((i) => i.id)).toEqual(['manage', 'account', 'logout']);
  });

  it('Xestión na xestión: só a conta e pechar sesión (a web pública é o escudo da barra)', () => {
    expect(ids({ staffRole: 'MANAGER', workAccount: true, inManagement: true })).toEqual(['account', 'logout']);
  });

  it('Recepción: «Xestión do concello» leva ás reservas', () => {
    const items = userMenuItems({ ...base, staffRole: 'STAFF', workAccount: true }, t);
    expect(items[0]).toMatchObject({ id: 'manage', link: ['/naron/xestion/reservas'] });
    expect(items.map((i) => i.id)).toEqual(['manage', 'account', 'logout']);
    expect(ids({ staffRole: 'STAFF', workAccount: true, inManagement: true })).toEqual(['account', 'logout']);
  });

  it('veciñanza: sen entrada á xestión', () => {
    expect(ids({})).toEqual(['account', 'bookings', 'dependents', 'logout']);
    expect(userMenuItems(base, t)[0]?.separatorBefore).toBe(false);
  });

  it('«Persoas ao meu cargo»: só a veciñanza, tamén sen concello; abre un diálogo, sen ligazón', () => {
    const entry = userMenuItems(base, t).find((i) => i.id === 'dependents');
    expect(entry).toMatchObject({ label: 'session.dependents' });
    expect(entry?.link).toBeUndefined();
    expect(entry?.href).toBeUndefined();
    expect(ids({ slug: null })).toEqual(['account', 'dependents', 'logout']);
    expect(ids({ staffRole: 'STAFF', workAccount: true })).not.toContain('dependents');
    expect(ids({ workAccount: true })).not.toContain('dependents');
    expect(ids({ platformAdmin: true, workAccount: true })).not.toContain('dependents');
  });
});

describe('«Concellos ›» da plataforma', () => {
  const list = [
    { slug: 'naron', name: 'Narón' },
    { slug: 'a-coruna', name: 'A Coruña' },
  ];

  it('todos por nome, o actual con ✓ e «Novo concello» ao final', () => {
    const switcher = platformSwitcher(list, 'naron', t, 'gl')!;
    expect(switcher.label).toBe('session.municipalities');
    expect(switcher.current).toBe('Narón');
    expect(switcher.options).toEqual([
      { id: 'a-coruna', label: 'A Coruña', current: false },
      { id: 'naron', label: 'Narón', current: true },
    ]);
    expect(switcher.extra).toMatchObject({ id: 'new-municipality', link: ['/plataforma/concellos/novo'] });
  });

  it('onde se engadiu como xestora, o rol á dereita', () => {
    const switcher = platformSwitcher(list, 'naron', t, 'gl', [{ slug: 'naron', name: 'Narón', role: 'MANAGER' }])!;
    expect(switcher.options).toEqual([
      { id: 'a-coruna', label: 'A Coruña', current: false },
      { id: 'naron', label: 'Narón', detail: 'session.role.MANAGER', current: true },
    ]);
  });

  it('fóra dun concello, ningún marcado; sen ningún, sen cambio', () => {
    expect(platformSwitcher(list, null, t, 'gl')?.current).toBe('');
    expect(platformSwitcher([], 'naron', t, 'gl')).toBeNull();
  });

  it('o do persoal segue igual: só con dous ou máis concellos e sen «Novo concello»', () => {
    const memberships = [
      { slug: 'naron', name: 'Narón', role: 'MANAGER' as const },
      { slug: 'ares', name: 'Ares', role: 'STAFF' as const },
    ];
    expect(municipalitySwitcher(memberships.slice(0, 1), 'naron', t)).toBeNull();
    expect(municipalitySwitcher(memberships, 'naron', t)?.extra).toBeUndefined();
  });
});

describe('menú da persoa: «A miña conta», a pantalla da app', () => {
  it('no concello actual ou, sen concello, na raíz; a seguridade e eliminar a conta van nela', () => {
    expect(userMenuItems(base, t).find((i) => i.id === 'account')).toMatchObject({
      label: 'session.account',
      link: ['/', 'naron', 'conta'],
    });
    expect(userMenuItems({ ...base, slug: null }, t).find((i) => i.id === 'account')?.link).toEqual(['/', 'conta']);
    for (const c of [{}, { staffRole: 'MANAGER' as const, workAccount: true }, { platformAdmin: true }]) {
      const all = ids(c);
      expect(all).not.toContain('security');
      expect(all).not.toContain('delete-account');
      expect(all).not.toContain('public');
      expect(all.at(-1)).toBe('logout');
    }
  });
});
