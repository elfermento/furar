// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out;

import gal.furar.facility.domain.Photo;
import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoVariant;
import gal.furar.facility.domain.ProcessedPhoto;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Porto de saída da biblioteca de fotos do concello. Sempre dentro do concello actual. */
public interface PhotoRepository {

  /** As fotos da biblioteca, sen as pendentes, da máis nova á máis vella. */
  List<Photo> library();

  /** As pedidas que existen (tamén pendentes). */
  Map<PhotoId, Photo> findAll(Collection<PhotoId> ids);

  /** Garda unha foto nova cos bytes das dúas variantes. */
  void add(Photo photo, ProcessedPhoto content);

  /** Actualiza texto alternativo e pendente das dadas (os bytes non cambian nunca). */
  void update(Collection<Photo> photos);

  /** Metadato dun ficheiro furar-export, sen bytes (ADR-0018, P32). */
  void restore(Photo photo);

  /** Borra unha foto (cos bytes e as súas imaxes nas instalacións). */
  void delete(PhotoId id);

  /** Borra as pendentes caducadas antes de {@code now}. Devolve cantas. */
  int purgeExpired(Instant now);

  Optional<PhotoFile> content(PhotoId photo, PhotoVariant variant);
}
