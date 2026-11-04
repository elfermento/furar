// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Drawing;
import gal.furar.facility.domain.FacilityType;
import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoId;
import gal.furar.shared.domain.Language;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Biblioteca de imaxes (especificación, bloque 1, «Fotos»): as fotos do concello e os debuxos de
 * Furar entre os que se escolle ao engadir imaxes a unha instalación.
 */
public interface PhotoLibraryUseCase {

  /** Sobe unha foto; queda pendente (fóra da biblioteca) ata que a garde unha instalación. */
  Photo upload(UploadPhotoCommand command);

  /** Descarta unha subida pendente (cancelar no modal). Unha foto xa gardada non se descarta. */
  void discard(PhotoId id);

  /** As fotos da biblioteca do concello, da máis nova á máis vella, sen as pendentes. */
  List<LibraryPhoto> library();

  /** Os debuxos de Furar en orde alfabética no idioma de quen mira. */
  List<Drawing> drawings(Language language);

  /**
   * O debuxo que lle iría a unha instalación con estes datos aínda sen gardar (o modo edición da
   * ficha cambia os deportes antes de gardar): as mesmas regras ca o suxerido da ficha. Os códigos
   * de deporte que non son do catálogo non contan.
   */
  ImageView suggestedDrawing(
      @Nullable String name,
      List<String> sports,
      @Nullable FacilityType type,
      @Nullable Boolean covered);
}
