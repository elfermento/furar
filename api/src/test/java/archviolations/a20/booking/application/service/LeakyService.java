// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a20.booking.application.service;

import gal.furar.shared.config.FurarProperties;
import gal.furar.shared.web.ETags;

/** A20: un servizo usa a configuración interna de shared e shared.web. */
public class LeakyService {
  public String tag(FurarProperties properties) {
    return ETags.of(properties.hashCode());
  }
}
