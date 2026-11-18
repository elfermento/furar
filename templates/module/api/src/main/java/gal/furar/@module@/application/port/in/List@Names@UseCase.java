// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.@module@.application.port.in;

import gal.furar.shared.domain.PageResult;

public interface List@Names@UseCase {

  PageResult<@Name@View> list(int page, int size);
}
