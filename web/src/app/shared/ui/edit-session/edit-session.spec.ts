// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { UnsavedChangesService } from '../../../core/unsaved-changes/unsaved-changes.service';
import { DialogService } from '../dialog/dialog.service';
import type { EditParticipant } from './edit-session';
import { EditSession } from './edit-session';

function part(overrides: Partial<EditParticipant> & { dirty?: ReturnType<typeof signal<boolean>> } = {}) {
  const dirty = overrides.dirty === undefined ? signal(true) : overrides.dirty;
  return {
    dirty,
    validate: vi.fn(overrides.validate ?? (() => true)),
    save: vi.fn(overrides.save ?? (() => Promise.resolve())),
    reset: vi.fn(overrides.reset ?? (() => undefined)),
  };
}

describe('EditSession: modo edición global', () => {
  function create() {
    TestBed.configureTestingModule({ providers: [EditSession] });
    return TestBed.inject(EditSession);
  }

  it('garda só as partes con cambios, unha detrás doutra, e sae da edición', async () => {
    const session = create();
    const order: string[] = [];
    const a = part({ save: async () => void order.push('a') });
    const b = part({ dirty: signal(false) });
    const c = part({ save: async () => void order.push('c') });
    [a, b, c].forEach((p) => session.register(p));
    session.start();
    expect(session.dirtyCount()).toBe(2);
    expect(TestBed.inject(UnsavedChangesService).hasUnsaved()).toBe(true);
    expect(await session.save()).toBe('saved');
    expect(order).toEqual(['a', 'c']);
    expect(b.save).not.toHaveBeenCalled();
    expect(session.editing()).toBe(false);
  });

  it('con erros á vista non manda nada', async () => {
    const session = create();
    const a = part({ validate: () => false });
    const b = part();
    session.register(a);
    session.register(b);
    session.start();
    expect(await session.save()).toBe('invalid');
    expect(a.validate).toHaveBeenCalled();
    expect(b.validate).toHaveBeenCalled();
    expect(b.save).not.toHaveBeenCalled();
    expect(session.editing()).toBe(true);
  });

  it('se unha parte falla, gárdanse as demais e segue en edición', async () => {
    const session = create();
    const a = part({ save: () => Promise.reject(new Error('412')) });
    const b = part();
    session.register(a);
    session.register(b);
    session.start();
    expect(await session.save()).toBe('failed');
    expect(b.save).toHaveBeenCalled();
    expect(session.editing()).toBe(true);
    expect(session.saving()).toBe(false);
  });

  it('cancelar desfai todas as partes; dar de baixa unha parte quítaa', async () => {
    const session = create();
    const a = part();
    const leave = session.register(a);
    session.start();
    session.cancel();
    expect(a.reset).toHaveBeenCalled();
    expect(session.editing()).toBe(false);
    leave();
    expect(session.dirtyCount()).toBe(0);
  });

  it('cancelAsking: sen cambios sae sen preguntar; con cambios pregunta e só descarta se se acepta', async () => {
    TestBed.configureTestingModule({ providers: [EditSession, provideTestI18n()] });
    const session = TestBed.inject(EditSession);
    const confirm = vi.spyOn(TestBed.inject(DialogService), 'confirm');
    const clean = part({ dirty: signal(false) });
    const leave = session.register(clean);
    session.start();
    expect(await session.cancelAsking()).toBe(true);
    expect(confirm).not.toHaveBeenCalled();
    leave();

    const dirty = part();
    session.register(dirty);
    session.start();
    confirm.mockResolvedValueOnce(false);
    expect(await session.cancelAsking()).toBe(false);
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({ heading: 'Descartar os cambios?', cancelLabel: 'Seguir editando', tone: 'danger' }),
    );
    expect(dirty.reset).not.toHaveBeenCalled();
    expect(session.editing()).toBe(true);
    confirm.mockResolvedValueOnce(true);
    expect(await session.cancelAsking()).toBe(true);
    expect(dirty.reset).toHaveBeenCalled();
    expect(session.editing()).toBe(false);
  });
});
