// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { SUGGESTED } from '../detail/facility.fixture';
import type { DraftImage, Image } from './photos';
import { altField, arrangeBody, countChanges, fileProblem, fromImage, missingAlt, reorder } from './photos';

function photo(n: number): Image {
  return {
    kind: 'PHOTO',
    id: `p${n}`,
    width: 1600,
    height: 1200,
    alt: { gl: `Vista ${n}`, es: `Vista ${n} es` },
    large: `/api/v1/municipalities/naron/photos/p${n}/large`,
    thumb: `/api/v1/municipalities/naron/photos/p${n}/thumb`,
    uploadedAt: '2026-10-25T10:00:00Z',
  };
}

const drafts = (count: number) => Array.from({ length: count }, (_, i) => fromImage(photo(i + 1)));

describe('Fotos: borrador', () => {
  it('conta engadidas, quitadas, textos cambiados e un máis se cambia a orde', () => {
    const before = drafts(3);
    expect(countChanges(before, before)).toBe(0);
    const moved = reorder(before, ['PHOTO:p2', 'PHOTO:p1', 'PHOTO:p3']);
    expect(countChanges(before, moved)).toBe(1);
    const edited = moved.map((d) => (d.id === 'p3' ? { ...d, alt: { ...d.alt, es: 'Outra' } } : d));
    expect(countChanges(before, edited)).toBe(2);
    // Quitada a 2 e texto da 3; as que quedan seguen na mesma orde.
    expect(countChanges(before, edited.slice(1))).toBe(2);
    expect(countChanges(before, reorder(moved, ['PHOTO:p1', 'PHOTO:p2', 'PHOTO:p3']))).toBe(0);
  });

  it('só o suxerido gárdase como lista baleira; o resto, fotos co seu texto e debuxos polo código', () => {
    const suggested: DraftImage[] = [fromImage(SUGGESTED)];
    expect(arrangeBody(suggested, SUGGESTED)).toEqual({ images: [] });
    const list = [...drafts(1).map((d) => ({ ...d, alt: { gl: ' Campo ', es: 'Campo' } })), fromImage(SUGGESTED)];
    expect(arrangeBody(list, SUGGESTED)).toEqual({
      images: [{ photoId: 'p1', alt: { gl: 'Campo', es: 'Campo' } }, { drawing: 'football_field' }],
    });
  });

  it('atopa o primeiro texto que falta, le os erros da API e comproba o ficheiro', () => {
    const list = drafts(2).map((d, i) => (i === 1 ? { ...d, alt: { gl: 'Ben', es: '  ' } } : d));
    expect(missingAlt([fromImage(SUGGESTED), ...list])).toEqual({ index: 2, lang: 'es' });
    expect(missingAlt(drafts(2))).toBeNull();
    expect(altField('images[3].alt.gl')).toEqual({ index: 3, lang: 'gl' });
    expect(altField('images')).toBeNull();
    expect(fileProblem(new File(['x'], 'a.gif', { type: 'image/gif' }))).toBe('facility.photo-unsupported-type');
    expect(fileProblem(new File(['x'], 'a.png', { type: 'image/png' }))).toBeNull();
  });
});
