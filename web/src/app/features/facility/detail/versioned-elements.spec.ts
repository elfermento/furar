// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { VersionedElements } from './versioned-elements';

interface Block {
  readonly id: string;
  readonly version: number;
  readonly reason: string;
}

/** Promesa que resolve quen fai o test, para ver a orde das peticións. */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((ok, ko) => {
    resolve = ok;
    reject = ko;
  });
  return { promise, resolve, reject };
}

const tick = () => new Promise((resolve) => setTimeout(resolve));

describe('VersionedElements: versión por elemento (D1-21a)', () => {
  let blocks: VersionedElements<Block>;

  beforeEach(() => {
    blocks = new VersionedElements<Block>();
    blocks.set([
      { id: 'a', version: 1, reason: 'Céspede' },
      { id: 'b', version: 5, reason: 'Pintura' },
    ]);
  });

  it('cada elemento garda co If-Match da súa versión e os do mesmo van en cola', async () => {
    const sent: string[] = [];
    const first = deferred<Block>();
    const one = blocks.save('a', (ifMatch) => {
      sent.push(`a ${ifMatch}`);
      return first.promise;
    });
    const two = blocks.save('a', (ifMatch) => {
      sent.push(`a ${ifMatch}`);
      return Promise.resolve({ id: 'a', version: 3, reason: 'Céspede 3' });
    });
    const other = blocks.save('b', (ifMatch) => {
      sent.push(`b ${ifMatch}`);
      return Promise.resolve({ id: 'b', version: 6, reason: 'Pintura 2' });
    });
    await tick();
    // «b» non espera por «a»; o segundo de «a» si.
    expect(sent).toEqual(['a "1"', 'b "5"']);
    first.resolve({ id: 'a', version: 2, reason: 'Céspede 2' });
    await one;
    await two;
    await other;
    expect(sent).toEqual(['a "1"', 'b "5"', 'a "2"']);
    expect(blocks.list()).toEqual([
      { id: 'a', version: 3, reason: 'Céspede 3' },
      { id: 'b', version: 6, reason: 'Pintura 2' },
    ]);
  });

  it('un erro (412) non toca a lista e o seguinte segue coa mesma versión', async () => {
    const stale = Object.assign(new Error('412'), { status: 412 });
    await expect(blocks.save('a', () => Promise.reject(stale))).rejects.toBe(stale);
    expect(blocks.get('a')?.version).toBe(1);
    let sent = '';
    await blocks.save('a', (ifMatch) => {
      sent = ifMatch;
      return Promise.resolve({ id: 'a', version: 2, reason: 'x' });
    });
    expect(sent).toBe('"1"');
  });

  it('crear engade ao final; quitar borra co If-Match do elemento', async () => {
    await blocks.create(() => Promise.resolve({ id: 'c', version: 0, reason: 'Novo' }));
    expect(blocks.list().map((block) => block.id)).toEqual(['a', 'b', 'c']);
    let sent = '';
    await blocks.remove('b', (ifMatch) => {
      sent = ifMatch;
      return Promise.resolve();
    });
    expect(sent).toBe('"5"');
    expect(blocks.list().map((block) => block.id)).toEqual(['a', 'c']);
    expect(blocks.empty()).toBe(false);
  });

  it('un elemento que non está rexeita sen chamar á API', async () => {
    const send = vi.fn();
    await expect(blocks.save('x', send)).rejects.toThrow('x');
    expect(send).not.toHaveBeenCalled();
  });
});
