// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { arrangeFacilityImages } from '../../../api/fn/facilities/arrange-facility-images';
import { discardLibraryPhoto } from '../../../api/fn/facilities/discard-library-photo';
import { listDrawings } from '../../../api/fn/facilities/list-drawings';
import { listLibraryPhotos } from '../../../api/fn/facilities/list-library-photos';
import type { SuggestDrawing$Params } from '../../../api/fn/facilities/suggest-drawing';
import { suggestDrawing } from '../../../api/fn/facilities/suggest-drawing';
import { uploadLibraryPhoto } from '../../../api/fn/facilities/upload-library-photo';
import type { ArrangeImagesRequest } from '../../../api/models/arrange-images-request';
import type { DrawingResponse } from '../../../api/models/drawing-response';
import type { ImageResponse } from '../../../api/models/image-response';
import type { LibraryPhotoResponse } from '../../../api/models/library-photo-response';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';

export type { ArrangeImagesRequest, DrawingResponse, ImageResponse, LibraryPhotoResponse };

/**
 * Imaxes da instalación e biblioteca do concello (especificación, bloque 1, «Fotos»). Gardar a
 * lista cambia a versión da instalación: a versión ponna a cola da ficha (`FacilityTarget`).
 * Subir e descartar non: a foto queda pendente ata que unha instalación a garda.
 */
@Injectable({ providedIn: 'root' })
export class PhotosStore {
  private readonly api = inject(Api);

  arrange({ slug, id, ifMatch }: FacilityTarget, body: ArrangeImagesRequest): Promise<Facility> {
    return this.api.invoke(arrangeFacilityImages, { slug, id, 'If-Match': ifMatch, body });
  }

  upload(slug: string, file: File): Promise<LibraryPhotoResponse> {
    return this.api.invoke(uploadLibraryPhoto, { slug, body: { file } });
  }

  discard(slug: string, photoId: string): Promise<void> {
    return this.api.invoke(discardLibraryPhoto, { slug, photoId });
  }

  library(slug: string): Promise<LibraryPhotoResponse[]> {
    return this.api.invoke(listLibraryPhotos, { slug });
  }

  drawings(slug: string): Promise<DrawingResponse[]> {
    return this.api.invoke(listDrawings, { slug });
  }

  /** O debuxo que lle iría con estes datos aínda sen gardar (as mesmas regras ca a ficha). */
  suggestion(params: SuggestDrawing$Params): Promise<ImageResponse> {
    return this.api.invoke(suggestDrawing, params);
  }
}
