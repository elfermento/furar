// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { SUMMER, season } from './schedule.fixture';
import { toSeason } from './schedule.store';
import { SeasonEdits } from './season-edits';

describe('Tempadas no modo edición da ficha (SeasonEdits)', () => {
  const winter = toSeason(season());
  const summer = toSeason(SUMMER);

  it('parte das gardadas, sen cambios e válidas (Verán, pechada enteira, tamén)', () => {
    const edits = new SeasonEdits();
    edits.start([winter, summer]);
    expect(edits.list().map((s) => s.id)).toEqual(['s1', 's2']);
    expect(edits.dirty()).toBe(false);
    expect(edits.validate()).toBe(true);
    expect(edits.tried()).toBe(true);
  });

  it('unha nova vai ao final co horario da última e queda pendente; quitala desfaise sen máis', () => {
    const edits = new SeasonEdits();
    edits.start([winter]);
    const added = edits.add();
    expect(edits.list().at(-1)).toBe(added);
    expect(added.added).toBe(true);
    expect(added.hasEnd.value).toBe(true);
    expect(added.weekBody()[5].intervals).toHaveLength(2);
    expect(edits.dirty()).toBe(true);
    expect(edits.validate()).toBe(false);
    edits.remove(added);
    expect(edits.removed().size).toBe(0);
    expect(edits.dirty()).toBe(false);
  });

  it('quitar unha gardada déixaa para borrar; forget segue á API e clear bota todo', () => {
    const edits = new SeasonEdits();
    edits.start([winter, summer]);
    edits.remove(edits.list()[1]);
    expect([...edits.removed()]).toEqual(['s2']);
    expect(edits.dirty()).toBe(true);
    edits.forget('s2');
    expect(edits.removed().size).toBe(0);
    edits.clear();
    expect(edits.list()).toEqual([]);
    expect(edits.tried()).toBe(false);
  });

  it('cambiar un campo marca sucio; dúas que se pisan non validan', () => {
    const edits = new SeasonEdits();
    edits.start([winter, summer]);
    const second = edits.list()[1];
    second.form.controls.startDate.setValue('2027-01-01');
    second.form.markAsDirty();
    expect(edits.dirty()).toBe(true);
    expect(edits.validate()).toBe(false);
  });

  it('«Empezar co horario de» copia o modo e a semana', () => {
    const edits = new SeasonEdits();
    edits.start([winter]);
    const added = edits.add();
    added.mode.setValue('SAME');
    edits.copy(added, edits.list()[0]);
    expect(added.copiedFrom).toBe('s1');
    expect(added.weekBody()).toEqual(edits.list()[0].weekBody());
  });
});
