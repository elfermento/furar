// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { ApiFnRequired } from '../api/api';
import { Api } from '../api/api';

/**
 * Como `Api.invoke` do cliente xerado, pero cancelable cun `AbortSignal`: ao abortar, Angular
 * deixa a subscrición e o `fetch` córtase (a API non segue a mandar unha resposta que xa non se
 * le) e a promesa rexéitase co motivo do sinal. Para pantallas que piden de novo ao cambiar un
 * filtro: a petición superada non compite coa nova.
 */
@Injectable({ providedIn: 'root' })
export class AbortableApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(Api);

  invoke<P, R>(fn: ApiFnRequired<P, R>, params: P, signal: AbortSignal): Promise<R> {
    return new Promise<R>((resolve, reject) => {
      if (signal.aborted) {
        reject(signal.reason);
        return;
      }
      const onAbort = () => {
        subscription.unsubscribe();
        reject(signal.reason);
      };
      const subscription = fn(this.http, this.api.rootUrl, params).subscribe({
        next: (event) => {
          if (event instanceof HttpResponse) resolve(event.body);
        },
        error: (error: unknown) => {
          signal.removeEventListener('abort', onAbort);
          reject(error);
        },
        complete: () => signal.removeEventListener('abort', onAbort),
      });
      if (!subscription.closed) signal.addEventListener('abort', onAbort, { once: true });
    });
  }
}
