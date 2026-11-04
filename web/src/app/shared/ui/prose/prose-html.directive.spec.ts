// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ProseHtmlDirective } from './prose-html.directive';

@Component({
  imports: [ProseHtmlDirective],
  template: `<div [furarProseHtml]="html()" [anchors]="anchors()"></div>`,
})
class HostComponent {
  readonly html = signal('');
  readonly anchors = signal<readonly string[]>([]);
}

describe('ProseHtmlDirective', () => {
  function render(html: string, anchors: readonly string[] = []): HTMLElement {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.html.set(html);
    fixture.componentInstance.anchors.set(anchors);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('div')!;
  }

  it('copia só a lista branca: o formato do editor e as ligazóns https e de correo', () => {
    const el = render(
      '<h2 class="x" style="color:red">T</h2><p onclick="alert(1)">Un <strong>dous</strong> <em>tres</em><br>' +
        '<a href="https://naron.example" target="_blank">sede</a> <a href="mailto:a@naron.example">correo</a></p>' +
        '<ol><li>un</li></ol><h1>grande</h1>',
    );
    expect(el.innerHTML).toBe(
      '<h2>T</h2><p>Un <strong>dous</strong> <em>tres</em><br>' +
        '<a href="https://naron.example" rel="noopener noreferrer">sede</a> ' +
        '<a href="mailto:a@naron.example" rel="noopener noreferrer">correo</a></p><ol><li>un</li></ol>grande',
    );
  });

  it('o id dun título só se é unha das anclas permitidas', () => {
    const el = render('<h2 id="persoal">A</h2><h3 id="outro">B</h3><p id="persoal">C</p>', ['persoal']);
    expect(el.innerHTML).toBe('<h2 id="persoal">A</h2><h3>B</h3><p>C</p>');
    expect(render('<h2 id="persoal">A</h2>').innerHTML).toBe('<h2>A</h2>');
  });

  it('cargas XSS: nada executable nin descargable', () => {
    (window as unknown as { pwned?: boolean }).pwned = undefined;
    const el = render(
      '<script>window.pwned = true</script><img src=x onerror="window.pwned = true"><p>ok</p>' +
        '<a href="javascript:window.pwned = true">mal</a><svg onload="window.pwned = true"><text>t</text></svg>' +
        '<iframe src="https://evil.example"></iframe><style>p{color:red}</style>',
    );
    expect(el.innerHTML).toBe('<p>ok</p><a rel="noopener noreferrer">mal</a>');
    expect((window as unknown as { pwned?: boolean }).pwned).toBeUndefined();
  });
});
