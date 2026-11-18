// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { ReviewComponent } from './review.component';

@Component({
  imports: [ReviewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul>
      <li data-a>
        <furar-review
          [stars]="4"
          date="12 nov"
          datetime="2026-11-12T10:00:00Z"
          place="Pavillón de Piñeiros"
          comment="Moi ben coidada."
        >
          <span data-chip>Oculto</span>
        </furar-review>
      </li>
      <li data-b>
        <furar-review [stars]="2" date="3 de novembro" datetime="2026-11-03T10:00:00Z" noComment="Sen comentario" />
      </li>
    </ul>
  `,
})
class HostComponent {}

describe('furar-review', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('lugar e día arriba, a nota e o comentario no seu parágrafo; o contido, ao final', async () => {
    const el = await render();
    const a = el.querySelector('[data-a] furar-review')!;
    const parts = [...a.children].map((child) => child.className || child.tagName.toLowerCase());
    expect(parts).toEqual(['f-review__meta', 'f-stars', 'f-review__comment', 'f-review__chips']);
    expect(a.querySelector('[data-review-place]')?.textContent).toBe('Pavillón de Piñeiros');
    expect(a.querySelector('.f-review__meta time')?.textContent).toBe('12 nov');
    expect(a.querySelector('time')?.getAttribute('datetime')).toBe('2026-11-12T10:00:00Z');
    expect(a.querySelector('[data-review-text]')?.textContent).toBe('Moi ben coidada.');
    expect(a.querySelector('.f-review__chips [data-chip]')).not.toBeNull();
    await expectNoAxeViolations(el);
  });

  it('sen lugar só o día; sen comentario, o texto que se lle dea', async () => {
    const el = await render();
    const b = el.querySelector('[data-b] furar-review')!;
    expect(b.querySelector('[data-review-place]')).toBeNull();
    expect(b.querySelector('.f-review__meta')?.textContent?.trim()).toBe('3 de novembro');
    expect(b.querySelector('[data-review-text]')).toBeNull();
    expect(b.querySelector('.f-review__none')?.textContent).toBe('Sen comentario');
  });
});
