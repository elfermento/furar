// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { backend, expectRequest, flushProblem } from '../../../../testing/api';
import { renderDetail } from '../../../../testing/facility-detail';
import { expectNoAxeViolations } from '../../../../testing/axe';
import type { Facility } from '../detail/facility-detail.store';
import { SUGGESTED, facility } from '../detail/facility.fixture';
import type { DraftImage, Image } from './photos';
import { altField, arrangeBody, countChanges, fileProblem, fromImage, missingAlt, reorder } from './photos';
import type { DrawingResponse, LibraryPhotoResponse } from './photos.store';

const URL = '/api/v1/municipalities/naron/facilities/f1';
const LIBRARY = '/api/v1/municipalities/naron/photos';
const DRAWINGS = '/api/v1/municipalities/naron/drawings';

function photo(n: number): Image {
  return {
    kind: 'PHOTO',
    id: `p${n}`,
    width: 1600,
    height: 1200,
    alt: { gl: `Vista ${n}`, es: `Vista ${n} es` },
    large: `${LIBRARY}/p${n}/large`,
    thumb: `${LIBRARY}/p${n}/thumb`,
    uploadedAt: '2026-10-25T10:00:00Z',
  };
}

const photos = (count: number) => Array.from({ length: count }, (_, i) => photo(i + 1));
const drafts = (count: number) => photos(count).map(fromImage);

function libraryPhoto(id: string, pending = false): LibraryPhotoResponse {
  return {
    id,
    pending,
    width: 1600,
    height: 1200,
    uploadedAt: '2026-10-26T10:00:00Z',
    large: `${LIBRARY}/${id}/large`,
    thumb: `${LIBRARY}/${id}/thumb`,
    usedIn: pending ? [] : ['Pavillón de Piñeiros'],
    ...(pending ? {} : { alt: { gl: `Foto ${id}`, es: `Foto ${id} es` } }),
  };
}

const POOL: DrawingResponse = {
  code: 'pool',
  name: { gl: 'Piscina', es: 'Piscina' },
  alt: { gl: 'Debuxo dunha piscina', es: 'Dibujo de una piscina' },
  width: 1200,
  height: 800,
  large: `${DRAWINGS}/pool/large`,
  thumb: `${DRAWINGS}/pool/thumb`,
};

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

describe('Fotos da ficha', () => {
  afterEach(() => {
    backend()
      .match({ method: 'DELETE' })
      .forEach((request) => request.flush(null));
    backend().verify();
    document.querySelectorAll('.cdk-overlay-container').forEach((c) => (c.innerHTML = ''));
  });

  async function render(start: Partial<Facility> = {}) {
    const view = await renderDetail({ facility: start });
    const { el, stable } = view;
    const section = () => el.querySelector<HTMLElement>('#facility-photos')!;
    const dialog = () => document.querySelector<HTMLElement>('furar-facility-photos-dialog');
    const library = () => document.querySelector<HTMLElement>('furar-photo-library-dialog');
    const open = async () => {
      if (!el.querySelector('[data-edit-save]')) await view.edit();
      section().querySelector<HTMLButtonElement>('[data-change]')!.click();
      await vi.waitFor(async () => {
        await stable();
        expect(dialog()).not.toBeNull();
      });
    };
    const thumbs = () => [...dialog()!.querySelectorAll<HTMLButtonElement>('.f-photos__thumb')];
    const order = () => thumbs().map((t) => t.dataset['key']);
    const textarea = () => dialog()!.querySelector<HTMLTextAreaElement>('#facility-photo-alt')!;
    const type = async (value: string) => {
      textarea().value = value;
      textarea().dispatchEvent(new Event('input'));
      await stable();
    };
    const click = async (element: HTMLElement | null) => {
      element!.click();
      await stable();
    };
    const openLibrary = async (photosInLibrary: LibraryPhotoResponse[] = []) => {
      await click(dialog()!.querySelector('[data-add]'));
      (await expectRequest(LIBRARY)).flush(photosInLibrary);
      (await expectRequest(DRAWINGS)).flush([POOL]);
      await vi.waitFor(async () => {
        await stable();
        expect(library()?.querySelector('.f-library__grid') ?? library()?.querySelector('[data-empty]')).toBeTruthy();
      });
    };
    return { ...view, section, dialog, library, open, thumbs, order, textarea, type, click, stable, openLibrary };
  }

  it('cinco fotos: portada grande primeiro, alt no idioma, «Cambiar fotos» e sen erros de axe', async () => {
    const { section } = await render({ images: photos(5) });
    const imgs = [...section().querySelectorAll('img')];
    expect(imgs.map((img) => img.alt)).toEqual(['Vista 1', 'Vista 2', 'Vista 3', 'Vista 4', 'Vista 5']);
    expect(imgs[0].getAttribute('src')).toBe(`${LIBRARY}/p1/large`);
    expect(imgs[0].getAttribute('srcset')).toContain(`${LIBRARY}/p1/thumb 480w`);
    expect(section().querySelector('[data-change]')).toBeNull();
    await expectNoAxeViolations(section());
  });

  it('«Cambiar fotos» só no modo edición', async () => {
    const { section, edit } = await render({ images: photos(2) });
    await edit();
    expect(section().querySelector('[data-change]')?.textContent).toContain('Cambiar fotos');
    await expectNoAxeViolations(section());
  });

  it('sen fotos gardadas vese o debuxo suxerido', async () => {
    const { section } = await render();
    expect(section().querySelector('img')?.getAttribute('src')).toBe(SUGGESTED.large);
    expect(section().querySelector('img')?.alt).toBe('Debuxo do campo de fútbol');
  });

  it('sen fotos, se «Datos» cambia o que decide o suxerido, o novo vese xa na galería e no modal', async () => {
    const outdoor: Image = { ...SUGGESTED, id: 'outdoor_court', large: `${DRAWINGS}/outdoor_court/large` };
    const hall: Image = {
      ...SUGGESTED,
      id: 'sports_hall',
      alt: { gl: 'Debuxo dun pavillón', es: 'Dibujo de un pabellón' },
      large: `${DRAWINGS}/sports_hall/large`,
      thumb: `${DRAWINGS}/sports_hall/thumb`,
    };
    const sport = (code: string) => ({ code, label: { gl: code, es: code } });
    const { el, section, dialog, open, order, click, edit, cancel, stable } = await render({
      images: [outdoor],
      suggestedImage: outdoor,
      details: {
        onlineBooking: true,
        showFreeSlots: false,
        sports: [sport('futsal'), sport('basketball')],
        covered: false,
        customFields: {},
      },
    });
    const img = () => section().querySelector('img');
    await edit();
    backend().expectNone((request) => request.url.endsWith('/drawings/suggestion'));
    const yes = [...el.querySelectorAll<HTMLButtonElement>('[aria-labelledby="facility-data-covered"] button')].find(
      (button) => button.textContent?.trim() === 'Si',
    )!;
    await click(yes);
    const request = await expectRequest((r) => r.url.endsWith('/drawings/suggestion'));
    expect(request.request.params.getAll('sport')).toEqual(['futsal', 'basketball']);
    expect(request.request.params.get('covered')).toBe('true');
    expect(img()?.getAttribute('src')).toBe(outdoor.large);
    request.flush(hall);
    await stable();
    expect(img()?.getAttribute('src')).toBe(hall.large);
    expect(img()?.alt).toBe('Debuxo dun pavillón');
    await open();
    expect(order()).toEqual(['DRAWING:sports_hall']);
    await click(dialog()!.querySelector('[data-cancel]'));
    await cancel();
    expect(img()?.getAttribute('src')).toBe(outdoor.large);
  });

  it('no móbil, unha cada vez con «1 de 3» e botóns para pasar', async () => {
    const { section, stable } = await render({ images: photos(3) });
    const position = () => section().querySelector('[data-position]')?.textContent?.trim();
    expect(position()).toBe('1 de 3');
    section().querySelector<HTMLButtonElement>('[data-next]')!.click();
    await stable();
    expect(position()).toBe('2 de 3');
  });

  it('o modal: grande, tarxeta, tira con aviso e nada sen cambios; Alt + frecha move e anuncia', async () => {
    const { dialog, open, thumbs, order, click, stable } = await render({ images: photos(3) });
    await open();
    await expectNoAxeViolations(dialog()!);
    expect(dialog()!.querySelector('h2')?.textContent).toContain('Campo de fútbol 1 de O Alto');
    expect(thumbs().map((t) => t.getAttribute('aria-label'))).toEqual([
      'Foto 1 de 3, portada',
      'Foto 2 de 3',
      'Foto 3 de 3',
    ]);
    expect(thumbs()[0].getAttribute('aria-pressed')).toBe('true');
    expect(dialog()!.querySelector('[role="tooltip"]')?.textContent).toContain('A primeira é a portada');
    expect(dialog()!.querySelector('[data-changes]')?.textContent?.trim()).toBe('');
    expect(dialog()!.querySelector('[data-meta]')?.textContent).toContain('Subida o 25/10/2026');

    await click(thumbs()[1]);
    expect(dialog()!.querySelector('.f-photos__big img')?.getAttribute('alt')).toBe('Vista 2');
    thumbs()[1].focus();
    thumbs()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', altKey: true, bubbles: true }));
    await stable();
    expect(order()).toEqual(['PHOTO:p2', 'PHOTO:p1', 'PHOTO:p3']);
    expect(dialog()!.querySelector('furar-carousel [role="status"]')?.textContent).toContain('posición 1 de 3');
    expect(dialog()!.querySelector('[data-changes]')?.textContent).toContain('1 cambio sen gardar');
  });

  it('«Aceptar» deixa a lista na ficha; «Gardar» manda a lista enteira co If-Match e o texto en gl e es', async () => {
    const { dialog, section, open, thumbs, type, click, stable, save, el } = await render({ images: photos(2) });
    await open();
    await click(thumbs()[1]);
    await click(dialog()!.querySelector('[data-lang="es"]'));
    await type('  Pista cuberta ');
    await click(dialog()!.querySelector('[data-save]'));
    expect(dialog()).toBeNull();
    backend().expectNone({ method: 'PUT', url: `${URL}/images` });
    expect([...section().querySelectorAll('img')].map((img) => img.alt)).toEqual(['Vista 1', 'Vista 2']);
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/images` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      images: [
        { photoId: 'p1', alt: { gl: 'Vista 1', es: 'Vista 1 es' } },
        { photoId: 'p2', alt: { gl: 'Vista 2', es: 'Pista cuberta' } },
      ],
    });
    put.flush(facility({ version: 4, images: photos(2) }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
  });

  it('sen texto non acepta: escolle a foto, cambia ao idioma que falta e leva o foco', async () => {
    const { dialog, open, thumbs, textarea, type, click } = await render({ images: photos(2) });
    await open();
    await click(thumbs()[1]);
    await click(dialog()!.querySelector('[data-lang="es"]'));
    await type('');
    await click(thumbs()[0]);
    await click(dialog()!.querySelector('[data-save]'));
    expect(dialog()).not.toBeNull();
    expect(thumbs()[1].getAttribute('aria-pressed')).toBe('true');
    expect(textarea().getAttribute('lang')).toBe('es');
    expect(textarea().getAttribute('aria-invalid')).toBe('true');
    expect(dialog()!.querySelector('#facility-photo-alt-error')?.textContent).toContain('Castelán');
    expect(document.activeElement).toBe(textarea());
    await type('Ben');
    await click(dialog()!.querySelector('[data-save]'));
    expect(dialog()).toBeNull();
  });

  it('eliminar quita só desta instalación; sen ningunha volve o debuxo suxerido e garda baleira', async () => {
    const { dialog, open, order, click, save } = await render({ images: photos(1) });
    await open();
    await click(dialog()!.querySelector('[data-remove] button'));
    expect(order()).toEqual(['DRAWING:football_field']);
    expect(dialog()!.querySelector('[data-drawing-alt]')?.textContent).toContain('Debuxo do campo');
    await click(dialog()!.querySelector('[data-save]'));
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/images` });
    expect(put.request.body).toEqual({ images: [] });
    put.flush(facility({ version: 4 }));
  });

  it('a biblioteca: marca un debuxo e sobe unha foto; engade as dúas ao final', async () => {
    const { dialog, library, open, order, openLibrary, click, stable } = await render({ images: photos(1) });
    await open();
    await openLibrary([libraryPhoto('p1'), libraryPhoto('p9')]);
    await expectNoAxeViolations(library()!);
    const inList = library()!.querySelector<HTMLButtonElement>('[data-key="PHOTO:p1"]')!;
    expect(inList.getAttribute('aria-disabled')).toBe('true');
    expect(inList.getAttribute('aria-label')).toContain('Xa está nesta instalación');

    const input = library()!.querySelector<HTMLInputElement>('#photo-library-file')!;
    const png = new File(['png'], 'pista.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: [png], configurable: true });
    input.dispatchEvent(new Event('change'));
    const post = await expectRequest({ method: 'POST', url: LIBRARY });
    expect((post.request.body as FormData).get('file')).toBe(png);
    post.flush(libraryPhoto('p10', true));
    await stable();
    expect(library()!.querySelector('[data-key="PHOTO:p10"]')?.getAttribute('aria-pressed')).toBe('true');

    await click(library()!.querySelector('[data-tab="drawings"]'));
    await click(library()!.querySelector('[data-key="DRAWING:pool"]'));
    expect(library()!.querySelector('[data-selected]')?.textContent).toContain('2 marcadas');
    await click(library()!.querySelector('[data-add-selected]'));
    await vi.waitFor(async () => {
      await stable();
      expect(library()).toBeNull();
    });
    expect(order()).toEqual(['PHOTO:p1', 'PHOTO:p10', 'DRAWING:pool']);
    expect(dialog()!.querySelector('[data-meta]')?.textContent).toContain('Subida agora');

    // Cancelar: a subida descártase, non queda na biblioteca.
    await click(dialog()!.querySelector('[data-cancel]'));
    const discarded = await expectRequest({ method: 'DELETE', url: `${LIBRARY}/p10` });
    discarded.flush(null);
  });

  it.each([
    [413, 'facility.photo-too-large', { max: '8 MB' }, '8 MB'],
    [415, 'facility.photo-unsupported-type', {}, 'JPEG ou PNG'],
    [422, 'facility.photo-too-many-pixels', { max: 40 }, '40 megapíxeles'],
  ])('subir: %i %s sae xunto ao nome do ficheiro', async (status, code, params, text) => {
    const { library, open, openLibrary, stable } = await render();
    await open();
    await openLibrary();
    const input = library()!.querySelector<HTMLInputElement>('#photo-library-file')!;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'foto.jpg', { type: 'image/jpeg' })] });
    input.dispatchEvent(new Event('change'));
    flushProblem(await expectRequest({ method: 'POST', url: LIBRARY }), status, code, { params });
    await vi.waitFor(async () => {
      await stable();
      expect(library()!.querySelector('[data-error]')?.textContent).toContain(text);
    });
    expect(library()!.querySelector('[data-error]')?.textContent).toContain('foto.jpg');
  });

  it('Cancelar na barra despois de aceptar: a lista volve á gardada e as subidas descártanse', async () => {
    const { dialog, library, open, openLibrary, click, stable, cancel, section } = await render({ images: photos(1) });
    await open();
    await openLibrary([]);
    const input = library()!.querySelector<HTMLInputElement>('#photo-library-file')!;
    Object.defineProperty(input, 'files', { value: [new File(['png'], 'nova.png', { type: 'image/png' })] });
    input.dispatchEvent(new Event('change'));
    (await expectRequest({ method: 'POST', url: LIBRARY })).flush(libraryPhoto('p20', true));
    await stable();
    await click(library()!.querySelector('[data-add-selected]'));
    await vi.waitFor(async () => {
      await stable();
      expect(library()).toBeNull();
    });
    const alt = dialog()!.querySelector<HTMLTextAreaElement>('#facility-photo-alt')!;
    alt.value = 'Nova';
    alt.dispatchEvent(new Event('input'));
    await click(dialog()!.querySelector('[data-lang="es"]'));
    const es = dialog()!.querySelector<HTMLTextAreaElement>('#facility-photo-alt')!;
    es.value = 'Nueva';
    es.dispatchEvent(new Event('input'));
    await click(dialog()!.querySelector('[data-save]'));
    expect(section().querySelectorAll('img')).toHaveLength(2);
    await cancel();
    (await expectRequest({ method: 'DELETE', url: `${LIBRARY}/p20` })).flush(null);
    expect(section().querySelectorAll('img')).toHaveLength(1);
  });
});
