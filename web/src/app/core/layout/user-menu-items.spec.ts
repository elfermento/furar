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
  canManage: false,
  workAccount: false,
  platformAdmin: false,
  inManagement: false,
  municipalities: null,
  homeLink: ['/', 'naron'],
  accountUrl: '/auth/realms/furar/account',
  securityUrl: '/auth/realms/furar/account/account-security/signing-in',
};

const ids = (c: Partial<UserMenuContext>) => userMenuItems({ ...base, ...c }, t).map((i) => i.id);

describe('menú da persoa: a entrada á xestión, primeira (maqueta 56)', () => {
  it('plataforma sen ningún concello: «Novo concello» en negra e unha liña antes da conta', () => {
    const items = userMenuItems({ ...base, slug: null, platformAdmin: true, municipalities: 0 }, t);
    expect(items[0]).toMatchObject({
      id: 'new-municipality',
      label: 'session.newMunicipality',
      link: ['/plataforma/concellos/novo'],
      strong: true,
    });
    expect(items[1]).toMatchObject({ id: 'account', separatorBefore: true });
    expect(items.map((i) => i.id)).toEqual(['new-municipality', 'account', 'platform-activity', 'security', 'logout']);
  });

  it('plataforma con concellos (ou mentres non o sabe): sen «Novo concello» no primeiro nivel', () => {
    const c = { platformAdmin: true, canManage: true, workAccount: true };
    expect(ids({ ...c, municipalities: 2 })).toEqual(['account', 'public', 'platform-activity', 'security', 'logout']);
    expect(ids({ ...c, municipalities: null })).toEqual([
      'account',
      'public',
      'platform-activity',
      'security',
      'logout',
    ]);
  });

  it('Xestión na web pública: «Xestión do concello» á sección «Concello», sen «Ver a web pública»', () => {
    const items = userMenuItems({ ...base, staffRole: 'MANAGER', canManage: true, workAccount: true }, t);
    expect(items[0]).toMatchObject({ id: 'manage', link: ['/naron/xestion/concello'], strong: true });
    expect(items.map((i) => i.id)).toEqual(['manage', 'account', 'security', 'logout']);
  });

  it('Xestión na xestión: sen «Xestión do concello», coa web pública', () => {
    expect(ids({ staffRole: 'MANAGER', canManage: true, workAccount: true, inManagement: true })).toEqual([
      'account',
      'public',
      'security',
      'logout',
    ]);
  });

  it('Recepción: «Xestión do concello» leva ás reservas; nunca a web pública', () => {
    const items = userMenuItems({ ...base, staffRole: 'STAFF', canManage: true, workAccount: true }, t);
    expect(items[0]).toMatchObject({ id: 'manage', link: ['/naron/xestion/reservas'] });
    expect(items.map((i) => i.id)).toEqual(['manage', 'account', 'security', 'logout']);
    expect(ids({ staffRole: 'STAFF', canManage: true, workAccount: true, inManagement: true })).toEqual([
      'account',
      'security',
      'logout',
    ]);
  });

  it('veciñanza: sen entrada á xestión', () => {
    expect(ids({})).toEqual(['account', 'bookings', 'logout']);
    expect(userMenuItems(base, t)[0]?.separatorBefore).toBe(false);
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
