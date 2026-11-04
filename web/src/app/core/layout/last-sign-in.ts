// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Se o URL é dunha páxina da xestión do concello (`/:concello/xestion/...`). */
export function isManagementUrl(url: string): boolean {
  return url.split(/[?#]/)[0]?.split('/')[2] === 'xestion';
}
