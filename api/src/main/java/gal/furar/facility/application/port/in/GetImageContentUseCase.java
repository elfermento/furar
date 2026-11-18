// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.PhotoFile;
import gal.furar.facility.domain.PhotoId;
import gal.furar.facility.domain.PhotoVariant;
import org.jspecify.annotations.Nullable;

/**
 * Bytes dunha imaxe, sen sesión. Unha foto vese sen máis se a usa unha instalación que se ve no
 * público; as outras (borradores, sen publicar, pendentes), só coa firma que a API puxo na ruta que
 * deu ({@code ?s=…}, ADR-0024). Nunca as que só usan instalacións de baixa. O contido dun id non
 * cambia nunca.
 */
public interface GetImageContentUseCase {

  /**
   * @param signature a firma da ruta ({@code s}); nula ou inválida, só as fotos públicas
   */
  PhotoFile photo(PhotoId photo, PhotoVariant variant, @Nullable String signature);

  PhotoFile drawing(String code, PhotoVariant variant);
}
