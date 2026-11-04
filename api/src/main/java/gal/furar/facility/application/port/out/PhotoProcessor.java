// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.ProcessedPhoto;

/**
 * Comproba e reencoda unha imaxe subida (plan do dominio 1, §1.2.9): só JPEG e PNG polo contido;
 * ancho e alto lidos antes de decodificar (máis de 40 Mpx, fóra); orientación EXIF aplicada e todo
 * o EXIF fóra; variante grande e miniatura. Erros: {@code facility.photo-unsupported-type}, {@code
 * facility.photo-too-many-pixels}, {@code facility.photo-unreadable}.
 */
public interface PhotoProcessor {

  ProcessedPhoto process(byte[] upload);
}
