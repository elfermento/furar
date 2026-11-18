// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

/**
 * O tratamento das fotos das instalacións para outros módulos (ADR-0024; ADR-0036): só JPEG e PNG
 * polo contido, ancho e alto lidos antes de decodificar (máis de 40 Mpx, fóra), orientación EXIF
 * aplicada, todo o EXIF fóra (tamén a localización) e reencodado a JPEG, a grande e a miniatura.
 * Non garda nada nin comproba permisos: quen chama garda os bytes nas súas táboas. Erros, coma nas
 * instalacións: {@code facility.photo-unsupported-type}, {@code facility.photo-too-many-pixels} e
 * {@code facility.photo-unreadable}.
 */
public interface PhotoSanitizer {

  SanitizedPhoto sanitize(byte[] upload);
}
