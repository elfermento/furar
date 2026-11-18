// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service;

import gal.furar.facility.PhotoSanitizer;
import gal.furar.facility.SanitizedPhoto;
import gal.furar.facility.application.port.out.PhotoProcessor;
import gal.furar.facility.domain.ProcessedPhoto;
import org.springframework.stereotype.Service;

/**
 * Implementa {@link PhotoSanitizer} (API pública) co mesmo {@link PhotoProcessor} ca as fotos das
 * instalacións. Sen permisos propios: non é un caso de uso.
 */
@Service
class PhotoSanitizerService implements PhotoSanitizer {

  private final PhotoProcessor processor;

  PhotoSanitizerService(PhotoProcessor processor) {
    this.processor = processor;
  }

  @Override
  public SanitizedPhoto sanitize(byte[] upload) {
    ProcessedPhoto photo = processor.process(upload);
    return new SanitizedPhoto(
        photo.large().bytes(),
        photo.thumb().bytes(),
        photo.large().contentType(),
        photo.width(),
        photo.height());
  }
}
