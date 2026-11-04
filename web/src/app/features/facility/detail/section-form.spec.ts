// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApplicationRef, Injector, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { syncWhenPristine } from './section-form';

describe('syncWhenPristine: cada sección énchese só se non está sucia (D1-21a)', () => {
  const settle = () => TestBed.inject(ApplicationRef).whenStable();

  function setup() {
    const source = signal<{ text: string } | null>(null);
    const form = new FormGroup({ text: new FormControl('', { nonNullable: true }) });
    syncWhenPristine(form, () => source(), TestBed.inject(Injector));
    return { source, form };
  }

  it('enche o formulario limpo con cada versión nova', async () => {
    const { source, form } = setup();
    source.set({ text: 'v1' });
    await settle();
    expect(form.getRawValue()).toEqual({ text: 'v1' });
    source.set({ text: 'v2' });
    await settle();
    expect(form.controls.text.value).toBe('v2');
    expect(form.pristine).toBe(true);
  });

  it('non toca o escrito dun formulario sucio e ponse ao día ao volver a limpo', async () => {
    const { source, form } = setup();
    source.set({ text: 'v1' });
    await settle();
    form.controls.text.setValue('escribindo');
    form.markAsDirty();
    source.set({ text: 'v2 doutra persoa' });
    await settle();
    expect(form.controls.text.value).toBe('escribindo');
    // Gardar ou cancelar deixa o formulario limpo: entón trae a última.
    form.markAsPristine();
    expect(form.controls.text.value).toBe('v2 doutra persoa');
  });
});
