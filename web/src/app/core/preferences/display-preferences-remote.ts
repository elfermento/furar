// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Provider } from '@angular/core';
import { inject } from '@angular/core';
import { Api } from '../../api/api';
import type { DisplayPreferences, DisplayPreferencesRemote } from './display-preferences';
import { DISPLAY_PREFERENCES_REMOTE, parsePreferences } from './display-preferences';

/**
 * Os axustes de accesibilidade na conta (`/api/v1/me/display-preferences`): `load` devolve `null`
 * se a conta nunca os gardou (204). As funcións da API cárganse á parte, ao usalas (orzamento, D27).
 */
export function accountDisplayPreferences(api: Api): DisplayPreferencesRemote {
  return {
    async load(): Promise<DisplayPreferences | null> {
      const { getMyDisplayPreferences } = await import('../../api/fn/people/get-my-display-preferences');
      const response = await api.invoke$Response(getMyDisplayPreferences);
      return response.status === 204 || !response.body ? null : parsePreferences(response.body);
    },
    async save(preferences: DisplayPreferences): Promise<void> {
      const { saveMyDisplayPreferences } = await import('../../api/fn/people/save-my-display-preferences');
      await api.invoke(saveMyDisplayPreferences, {
        body: {
          textSize: preferences.textSize,
          highContrast: preferences.highContrast,
          reducedMotion: preferences.reducedMotion,
        },
      });
    },
  };
}

/** Con sesión, os axustes gárdanse tamén na conta. */
export function provideAccountDisplayPreferences(): Provider {
  return { provide: DISPLAY_PREFERENCES_REMOTE, useFactory: () => accountDisplayPreferences(inject(Api)) };
}
