/**********************************************************************
 * Copyright (c) 2005-2009 ant4eclipse project team.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nils Hartmann, Daniel Kasmeroglu, Gerd Wuetherich
 **********************************************************************/
package org.ant4eclipse.pydt.model;

import org.ant4eclipse.core.Assert;

import java.io.File;

/**
 * Resolved library record.
 * 
 * @author Daniel Kasmeroglu (Daniel.Kasmeroglu@Kasisoft.net)
 */
public class ResolvedLibraryEntry implements ResolvedPathEntry {

  private File _location;

  /**
   * Sets up this entry with resolved location of a python library.
   * 
   * @param location
   *          The location of the library.
   */
  public ResolvedLibraryEntry(final File location) {
    Assert.notNull(location);
    _location = location;
  }

  /**
   * {@inheritDoc}
   */
  public ReferenceKind getKind() {
    return ReferenceKind.Library;
  }

  /**
   * Returns the location of the library.
   * 
   * @return The location of the library. Not <code>null</code>.
   */
  public File getLocation() {
    return _location;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object object) {
    if (this == object) {
      return true;
    }
    if (object == null) {
      return false;
    }
    if (object.getClass() != getClass()) {
      return false;
    }
    final ResolvedLibraryEntry other = (ResolvedLibraryEntry) object;
    return _location.equals(other._location);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    return _location.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    final StringBuffer buffer = new StringBuffer();
    buffer.append("[ResolvedLibraryEntry:");
    buffer.append(" _location: ");
    buffer.append(_location);
    buffer.append("]");
    return buffer.toString();
  }

} /* ENDCLASS */