// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApiProblem } from '../../../core/errors/api-problem';
import { StagedElements } from './staged-elements';

interface Item {
  readonly id: string;
  readonly name: string;
}

const preview = (body: string, base: Item | null, key: string): Item => ({ id: base?.id ?? key, name: body });
const SAVED: Item[] = [
  { id: 'a', name: 'A' },
  { id: 'b', name: 'B' },
];

describe('StagedElements: cambios pendentes dunha lista', () => {
  it('a vista aplica cambios, quita baixas e engade altas ao final', () => {
    const staged = new StagedElements<Item, string>(preview);
    staged.stage('A2', SAVED[0]);
    staged.remove('b', SAVED[1]);
    const key = staged.stage('C', null);
    expect(staged.view(SAVED)).toEqual([
      { id: 'a', name: 'A2' },
      { id: key, name: 'C' },
    ]);
    expect(staged.dirty()).toBe(true);
    staged.stage('C2', null, key);
    expect(staged.view(SAVED).at(-1)?.name).toBe('C2');
    staged.remove(key, null);
    expect(staged.get(key)).toBeUndefined();
    staged.clear();
    expect(staged.dirty()).toBe(false);
  });

  it('garda baixas, cambios e altas por esa orde; o que falla queda co seu problema', async () => {
    const staged = new StagedElements<Item, string>(preview);
    staged.stage('nova', null);
    staged.stage('A2', SAVED[0]);
    staged.remove('b', SAVED[1]);
    const calls: string[] = [];
    const ok = await staged.flush({
      remove: async (id) => {
        calls.push(`remove ${id}`);
        return true;
      },
      update: async (id) => {
        calls.push(`update ${id}`);
        throw new ApiProblem(409, 'facility.use-name-taken', {}, [], undefined, '');
      },
      create: async (body) => {
        calls.push(`create ${body}`);
        return null;
      },
    });
    expect(calls).toEqual(['remove b', 'update a', 'create nova']);
    expect(ok).toBe(false);
    expect(staged.failed().map((c) => c.key)).toEqual(['a']);
    expect(staged.get('b')).toBeUndefined();
    expect(staged.view(SAVED).map((i) => i.name)).toEqual(['A2', 'B', 'nova']);
  });
});
