// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gal.furar.shared.domain.DomainException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lista de imaxes: a primeira é a portada, como moito 12 e sen repetidas. */
class FacilityImagesTest {

  static String code(Throwable e) {
    return ((DomainException) e).errorCode().code();
  }

  @Test
  void theFirstIsTheCover() {
    PhotoId photo = PhotoId.newId();
    FacilityImages images =
        new FacilityImages(List.of(ImageRef.drawing("pool"), ImageRef.photo(photo)));
    assertThat(images.cover()).contains(ImageRef.drawing("pool"));
    assertThat(images.photos()).containsExactly(photo);
    assertThat(FacilityImages.NONE.cover()).isEmpty();
  }

  @Test
  void atMostTwelve() {
    List<ImageRef> thirteen = new ArrayList<>();
    for (int i = 0; i < 13; i++) {
      thirteen.add(ImageRef.photo(PhotoId.newId()));
    }
    assertThatThrownBy(() -> new FacilityImages(thirteen))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.photo-limit"));
    assertThat(new FacilityImages(thirteen.subList(0, 12)).images()).hasSize(12);
  }

  @Test
  void noImageTwice() {
    PhotoId photo = PhotoId.newId();
    assertThatThrownBy(
            () -> new FacilityImages(List.of(ImageRef.photo(photo), ImageRef.photo(photo))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.images-invalid"));
    assertThatThrownBy(
            () -> new FacilityImages(List.of(ImageRef.drawing("gym"), ImageRef.drawing("gym"))))
        .satisfies(e -> assertThat(code(e)).isEqualTo("facility.images-invalid"));
  }
}
