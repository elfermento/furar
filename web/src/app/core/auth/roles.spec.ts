// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { canConfigure, canManage, canViewBookings, mainRole } from './roles';

describe('roles', () => {
  it('xestión só co rol MANAGER da API; a plataforma e a veciñanza non', () => {
    expect(canManage('MANAGER')).toBe(true);
    expect(canManage('STAFF')).toBe(false);
    expect(canManage(null)).toBe(false);
  });

  it('configurar o concello: o xestor e a plataforma; nin o persoal do mostrador nin a veciñanza', () => {
    expect(canConfigure('MANAGER', false)).toBe(true);
    expect(canConfigure(null, true)).toBe(true);
    expect(canConfigure('STAFF', false)).toBe(false);
    expect(canConfigure(null, false)).toBe(false);
  });

  it('reservas do concello: xestión e persoal do mostrador; nin a plataforma nin a veciñanza', () => {
    expect(canViewBookings('STAFF')).toBe(true);
    expect(canViewBookings('MANAGER')).toBe(true);
    expect(canViewBookings(null)).toBe(false);
  });

  it('o rol da barra: a plataforma por riba; se non, o da API', () => {
    expect(mainRole(true, null)).toBe('PLATFORM_ADMIN');
    expect(mainRole(false, 'STAFF')).toBe('STAFF');
    expect(mainRole(false, null)).toBeNull();
  });
});
