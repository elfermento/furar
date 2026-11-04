// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package archviolations.a8.adapter.out.persistence;

import org.hibernate.Session;

/** SQL nativo de Hibernate (Session.createNativeQuery) nunha clase sen @NativeSql. */
public class HibernateWorkReader {
  private final Session session;

  public HibernateWorkReader(Session session) {
    this.session = session;
  }

  public Object count() {
    return session
        .createNativeQuery("select count(*) from custom_field", Object.class)
        .getSingleResult();
  }
}
