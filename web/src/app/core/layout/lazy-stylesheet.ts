// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { inject } from '@angular/core';
import type { ResolveFn } from '@angular/router';

/**
 * Carga unha folla á parte de `angular.json` (`inject: false`), para que non entre na carga
 * inicial. Pono un `<link>` á mesma orixe (`style-src 'self'`) unha soa vez e devolve a promesa de
 * que cargue (ou `true` se xa estaba). Se a folla non chega, segue igual (mellor sen estilo ca sen
 * páxina). Chámase nun contexto de inxección.
 */
export function lazyStylesheet(file: string, linkId: string): () => Promise<true> | true {
  let loading: Promise<true> | undefined;
  return () => {
    const document = inject(DOCUMENT);
    if (document.getElementById(linkId)) {
      return loading ?? true;
    }
    const link = document.createElement('link');
    link.id = linkId;
    link.rel = 'stylesheet';
    link.href = new URL(file, document.baseURI).href;
    loading = new Promise<true>((resolve) => {
      link.addEventListener('load', () => resolve(true), { once: true });
      link.addEventListener('error', () => resolve(true), { once: true });
    });
    document.head.append(link);
    return loading;
  };
}

/** A mesma carga como resolvedor: a ruta agarda pola folla, así a páxina non se pinta sen estilos. */
export function lazyStylesheetResolver(file: string, linkId: string): ResolveFn<true> {
  const load = lazyStylesheet(file, linkId);
  return () => load();
}
