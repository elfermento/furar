// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ScrollHide } from './scroll-hide';

describe('barra que se esconde ao baixar', () => {
  it('escóndese ao baixar pasados 80 px e volve ao subir', () => {
    const bar = new ScrollHide();
    bar.update(40);
    expect(bar.hidden()).toBe(false);
    bar.update(79);
    expect(bar.hidden()).toBe(false);
    bar.update(200);
    expect(bar.hidden()).toBe(true);
    bar.update(200);
    expect(bar.hidden()).toBe(true);
    bar.update(150);
    expect(bar.hidden()).toBe(false);
    bar.update(400);
    bar.update(60);
    expect(bar.hidden()).toBe(false);
  });

  it('co foco dentro vese sempre e, ao saír, segue á vista ata baixar outra vez', () => {
    const bar = new ScrollHide();
    bar.update(300);
    bar.update(500);
    expect(bar.hidden()).toBe(true);
    bar.focus(true);
    expect(bar.hidden()).toBe(false);
    bar.update(800);
    expect(bar.hidden()).toBe(false);
    bar.focus(false);
    expect(bar.hidden()).toBe(true);
    bar.update(700);
    expect(bar.hidden()).toBe(false);
  });
});
