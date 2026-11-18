// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { FileDropComponent } from './file-drop.component';

const photo = () => new File(['x'], 'pista.jpg', { type: 'image/jpeg' });

/** Un evento de arrastre cos ficheiros que se diga (o navegador de proba non trae `DataTransfer`). */
function dragEvent(type: string, files: File[] | null = []): DragEvent {
  const event = new Event(type, { bubbles: true, cancelable: true }) as DragEvent;
  Object.defineProperty(event, 'dataTransfer', { value: files === null ? null : { files } });
  return event;
}

describe('FileDropComponent: zona para subir ficheiros', () => {
  async function render(inputs: Record<string, unknown> = {}) {
    const fixture = TestBed.createComponent(FileDropComponent);
    fixture.componentRef.setInput('title', 'Arrastra aquí as fotos');
    fixture.componentRef.setInput('button', 'Subir fotos');
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const files: File[][] = [];
    fixture.componentInstance.files.subscribe((list) => files.push(list));
    const zone = host.querySelector<HTMLElement>('.f-file-drop__zone')!;
    const input = host.querySelector<HTMLInputElement>('input[type="file"]')!;
    return { fixture, host, files, zone, input };
  }

  it('o botón é un input de ficheiros con etiqueta, a axuda descríbeo e o tipo aceptado pásase', async () => {
    const { host, input } = await render({ help: 'JPEG ou PNG', accept: 'image/jpeg,image/png' });
    const label = host.querySelector('label')!;
    expect(label.getAttribute('for')).toBe(input.id);
    expect(label.textContent?.trim()).toBe('Subir fotos');
    expect(input.multiple).toBe(true);
    expect(input.accept).toBe('image/jpeg,image/png');
    expect(input.getAttribute('aria-describedby')).toBe(`${input.id}-help`);
    expect(host.querySelector(`#${input.id}-help`)?.textContent).toBe('JPEG ou PNG');
  });

  it('sen axuda, nin texto nin descrición', async () => {
    const { host, input } = await render();
    expect(input.hasAttribute('aria-describedby')).toBe(false);
    expect(host.querySelector('.f-file-drop__help')).toBeNull();
  });

  it('escoller ficheiros avisa con eles e baleira o input para poder escoller os mesmos; sen ficheiros, nada', async () => {
    const { input, files } = await render();
    const picked = [photo()];
    Object.defineProperty(input, 'files', { value: picked, configurable: true });
    input.dispatchEvent(new Event('change'));
    expect(files).toEqual([picked]);

    Object.defineProperty(input, 'files', { value: [], configurable: true });
    input.dispatchEvent(new Event('change'));
    Object.defineProperty(input, 'files', { value: null, configurable: true });
    input.dispatchEvent(new Event('change'));
    expect(files).toHaveLength(1);
  });

  it('arrastrar enriba marca a zona; saír desmárcaa; soltar avisa cos ficheiros', async () => {
    const { fixture, host, zone, files } = await render();
    const over = dragEvent('dragover');
    zone.dispatchEvent(over);
    await fixture.whenStable();
    expect(over.defaultPrevented).toBe(true);
    expect(host.hasAttribute('data-over')).toBe(true);
    zone.dispatchEvent(dragEvent('dragleave'));
    await fixture.whenStable();
    expect(host.hasAttribute('data-over')).toBe(false);

    zone.dispatchEvent(dragEvent('dragover'));
    const drop = dragEvent('drop', [photo()]);
    zone.dispatchEvent(drop);
    await fixture.whenStable();
    expect(drop.defaultPrevented).toBe(true);
    expect(host.hasAttribute('data-over')).toBe(false);
    expect(files.map((list) => list.map((item) => item.name))).toEqual([['pista.jpg']]);

    // Soltar sen ficheiros (texto, por exemplo) non avisa.
    zone.dispatchEvent(dragEvent('drop', []));
    zone.dispatchEvent(dragEvent('drop', null));
    expect(files).toHaveLength(1);
  });

  it('desactivada: nin se marca ao arrastrar nin acepta o que se solta', async () => {
    const { fixture, host, zone, input, files } = await render({ disabled: true });
    expect(host.hasAttribute('data-disabled')).toBe(true);
    expect(input.disabled).toBe(true);
    const over = dragEvent('dragover');
    zone.dispatchEvent(over);
    await fixture.whenStable();
    expect(over.defaultPrevented).toBe(false);
    expect(host.hasAttribute('data-over')).toBe(false);
    const drop = dragEvent('drop', [photo()]);
    zone.dispatchEvent(drop);
    // Evita que o navegador abra o ficheiro, pero non o sube.
    expect(drop.defaultPrevented).toBe(true);
    expect(files).toEqual([]);
  });
});
