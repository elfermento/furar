// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../testing/i18n';
import { preview } from '../../../testing/seasons';
import type { SchedulePreview } from './schedule';
import { SchedulePreviewComponent } from './schedule-preview.component';

describe('furar-schedule-preview: os avisos dos días que non se crean', () => {
  async function render(value: SchedulePreview) {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(SchedulePreviewComponent);
    fixture.componentRef.setInput('preview', value);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return [...el.querySelectorAll('[data-skipped]')].map((p) => p.textContent?.trim());
  }

  it('sen días ocupados, o primeiro aviso «non se crean» e os seguintes «tampouco»', async () => {
    const lines = await render(preview({ conflicts: [], useOff: ['2026-12-08'], closed: ['2026-12-25'] }));
    expect(lines).toEqual([
      'Os festivos (6 xan.) non se crean.',
      'Os días sen o uso (8 dec.) tampouco se crean.',
      'Os días pechados (25 dec.) tampouco se crean.',
    ]);
  });

  it('despois dos días ocupados, todos «tampouco»', async () => {
    expect(await render(preview({ holidays: [], closed: ['2026-12-25'] }))).toEqual([
      'Os días pechados (25 dec.) tampouco se crean.',
    ]);
  });
});
