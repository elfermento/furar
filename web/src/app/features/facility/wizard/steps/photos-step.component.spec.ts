// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { By } from '@angular/platform-browser';
import { backend, expectRequest, flushProblem } from '../../../../../testing/api';
import {
  WIZARD_BASE,
  drawing,
  libraryPhoto,
  openWizard,
  renderWizardStep,
  setupWizard,
} from '../../../../../testing/facility-wizard';
import { facility } from '../../detail/facility.fixture';
import { fromLibrary } from '../../photos/photos';
import type { WizardStore } from '../wizard.store';
import { PhotosStepComponent } from './photos-step.component';

const DRAWINGS = [
  drawing('pool', 'Piscina'),
  drawing('football_field', 'Campo de fútbol'),
  drawing('track', 'Pista de atletismo'),
  drawing('padel_court', 'Pista de pádel'),
  drawing('basketball', 'Baloncesto'),
  drawing('tennis', 'Tenis'),
  drawing('gym', 'Ximnasio'),
  drawing('rink', 'Pista de patinaxe'),
];

/** Un ficheiro escollido no explorador, do tipo e tamaño que se diga. */
function file(name: string, type: string, size = 1024): File {
  const picked = new File(['x'], name, { type });
  Object.defineProperty(picked, 'size', { value: size });
  return picked;
}

describe('Paso «Fotos» do asistente', () => {
  let store: WizardStore;

  beforeEach(async () => {
    store = setupWizard();
    await openWizard(store, facility({ id: 'f2', published: false }));
  });

  afterEach(() => backend().verify());

  async function render(drawings = DRAWINGS) {
    const view = await renderWizardStep(PhotosStepComponent);
    (await expectRequest(`${WIZARD_BASE}/drawings`)).flush(drawings);
    await view.stable();
    const radios = () => [...view.el.querySelectorAll<HTMLInputElement>('[data-drawings] input[type="radio"]')];
    /** Escolle ficheiros co botón «Subir fotos». */
    const pick = async (...files: File[]) => {
      const input = view.el.querySelector<HTMLInputElement>('furar-file-drop input[type="file"]')!;
      Object.defineProperty(input, 'files', { value: files, configurable: true });
      input.dispatchEvent(new Event('change'));
      await view.stable();
    };
    const notes = () => [...view.el.querySelectorAll('.f-field-error')].map((note) => note.textContent?.trim());
    return { ...view, radios, pick, notes };
  }

  it('sen fotos, o debuxo: o suxerido primeiro e marcado, despois por nome; «Ver os 8 debuxos» amósaos todos', async () => {
    const { el, radios, click } = await render();
    expect(radios()).toHaveLength(6);
    const codes = () => radios().map((radio) => radio.closest('[data-drawing]')?.getAttribute('data-drawing'));
    expect(codes()).toEqual(['football_field', 'basketball', 'pool', 'track', 'padel_court', 'rink']);
    expect(radios()[0].checked).toBe(true);
    expect(radios()[0].getAttribute('aria-label')).toBe('Campo de fútbol, suxerido');
    expect(radios()[1].getAttribute('aria-label')).toBe('Baloncesto');

    const all = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Ver os 8 debuxos'));
    await click(all!);
    expect(radios()).toHaveLength(8);
    expect([...el.querySelectorAll('button')].some((b) => b.textContent?.includes('Ver os'))).toBe(false);

    radios()[2].dispatchEvent(new Event('change'));
    expect(store.forms.drawing()).toBe('pool');
    expect(store.shownDrawing()).toBe('pool');
  });

  it('o debuxo escollido vese aínda que quede fóra dos primeiros', async () => {
    store.chooseDrawing('gym');
    const { radios } = await render();
    expect(radios()).toHaveLength(7);
    expect(radios()[6].closest('[data-drawing]')?.getAttribute('data-drawing')).toBe('gym');
    expect(radios()[6].checked).toBe(true);
  });

  it('subir: os ficheiros que non son JPEG ou PNG, ou pasan de 8 MB, avisan sen subir; a boa vai á tira', async () => {
    const { el, pick, notes } = await render();
    expect(el.querySelector('.f-file-drop__help')?.textContent).toBe('JPEG ou PNG, ata 8 MB · ata 12 fotos');
    await pick(
      file('plano.gif', 'image/gif'),
      file('grande.png', 'image/png', 9 * 1024 * 1024),
      file('pista.jpg', 'image/jpeg'),
    );
    const upload = await expectRequest({ method: 'POST', url: `${WIZARD_BASE}/photos` });
    expect((upload.request.body as FormData).get('file')).toBeInstanceOf(File);
    upload.flush(libraryPhoto('p1'));
    await vi.waitFor(() => expect(el.querySelector('[data-photos]')).not.toBeNull());

    expect(notes()).toEqual([
      'plano.gif: Sube a foto en JPEG ou PNG.',
      'grande.png: A foto pode ter como máximo 8 MB.',
    ]);
    expect(store.images().map((image) => image.id)).toEqual(['p1']);
    expect(el.querySelector('[data-photos] p')?.textContent?.trim()).toBe('1 foto');
    expect(el.querySelector('.f-file-drop__help')?.textContent).toBe('JPEG ou PNG, ata 8 MB · ata 12 fotos, quedan 11');
    expect(el.querySelector('button[data-key="PHOTO:p1"]')?.getAttribute('aria-label')).toBe('Foto 1 de 1, portada');
    expect(el.querySelector('section > p[role="status"]')?.textContent).toBe('pista.jpg subida e marcada.');
    // Con fotos, o debuxo xa non se ofrece.
    expect(el.querySelector('[data-drawings]')).toBeNull();
  });

  it('se a API rexeita a subida, o aviso leva o nome do ficheiro', async () => {
    const { pick, notes } = await render();
    await pick(file('pista.jpg', 'image/jpeg'));
    flushProblem(
      await expectRequest({ method: 'POST', url: `${WIZARD_BASE}/photos` }),
      400,
      'facility.photo-unsupported-type',
    );
    await vi.waitFor(() => expect(notes()).toEqual(['pista.jpg: Sube a foto en JPEG ou PNG.']));
    expect(store.images()).toEqual([]);
  });

  it('a descrición escríbese na foto marcada; quitala descarta a subida', async () => {
    const { el, pick, type, click } = await render();
    await pick(file('pista.jpg', 'image/jpeg'), file('vestiarios.png', 'image/png'));
    (await expectRequest({ method: 'POST', url: `${WIZARD_BASE}/photos` })).flush(libraryPhoto('p1'));
    (await expectRequest({ method: 'POST', url: `${WIZARD_BASE}/photos` })).flush(libraryPhoto('p2'));
    await vi.waitFor(() => expect(store.images()).toHaveLength(2));
    // A última subida queda marcada.
    expect(el.querySelector('button[data-key="PHOTO:p2"]')?.getAttribute('aria-pressed')).toBe('true');
    await type(el.querySelector('#wizard-photo-alt'), 'Vestiarios');
    expect(store.images()[1].alt.gl).toBe('Vestiarios');
    expect(el.querySelector('button[data-key="PHOTO:p2"]')?.getAttribute('aria-label')).toBe('Foto 2 de 2: Vestiarios');

    await click(el.querySelector('button[data-key="PHOTO:p1"]'));
    expect(el.querySelector('button[data-key="PHOTO:p1"]')?.getAttribute('aria-pressed')).toBe('true');
    expect((el.querySelector('#wizard-photo-alt') as HTMLTextAreaElement).value).toBe('');

    await click(el.querySelector('[data-photo-remove] button'));
    (await expectRequest({ method: 'DELETE', url: `${WIZARD_BASE}/photos/p1` })).flush(null);
    expect(store.images().map((image) => image.id)).toEqual(['p2']);
    expect(el.querySelector('section > p[role="status"]')?.textContent).toBe(
      'Foto quitada da instalación. Segue na biblioteca.',
    );
    expect(el.querySelector('button[data-key="PHOTO:p2"]')?.getAttribute('aria-pressed')).toBe('true');
  });

  it('ordenar a tira cambia a portada e a foto marcada segue marcada', async () => {
    store.forms.setPhotos([
      fromLibrary(libraryPhoto('p1', { gl: 'Pista', es: 'Pista' })),
      fromLibrary(libraryPhoto('p2', { gl: 'Grada', es: 'Grada' })),
    ]);
    const { el, fixture, stable } = await render();
    fixture.debugElement.query(By.css('furar-carousel')).triggerEventHandler('reordered', ['PHOTO:p2', 'PHOTO:p1']);
    await stable();
    expect(store.images().map((image) => image.id)).toEqual(['p2', 'p1']);
    expect(el.querySelector('button[data-key="PHOTO:p1"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(el.querySelector('button[data-key="PHOTO:p2"]')?.getAttribute('aria-label')).toBe(
      'Foto 1 de 2, portada: Grada',
    );
    expect(el.querySelector('[data-photos] p')?.textContent?.trim()).toBe('2 fotos');
  });

  it('con 12 fotos non se sube máis', async () => {
    store.forms.setPhotos(
      Array.from({ length: 12 }, (_, i) => fromLibrary(libraryPhoto(`p${i}`, { gl: 'Foto', es: 'Foto' }))),
    );
    const { el, fixture, stable } = await render();
    expect(el.querySelector('furar-file-drop')?.hasAttribute('data-disabled')).toBe(true);
    expect(el.querySelector('.f-file-drop__help')?.textContent).toContain('quedan 0');
    fixture.debugElement
      .query(By.css('furar-file-drop'))
      .triggerEventHandler('files', [file('mais.jpg', 'image/jpeg')]);
    await stable();
    expect(store.images()).toHaveLength(12);
  });
});
