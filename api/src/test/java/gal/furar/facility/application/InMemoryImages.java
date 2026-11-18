// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application;

import gal.furar.facility.application.service.FacilityGalleries;

/** Biblioteca, listas e debuxos en memoria, enlazados coma na base. */
final class InMemoryImages {

  final InMemoryPhotoRepository photos = new InMemoryPhotoRepository();
  final InMemoryFacilityImageRepository images = new InMemoryFacilityImageRepository();
  final InMemoryDrawingRepository drawings = new InMemoryDrawingRepository();
  final FacilityGalleries galleries = new FacilityGalleries(images, photos, drawings);

  InMemoryImages() {
    photos.images = images;
  }
}
