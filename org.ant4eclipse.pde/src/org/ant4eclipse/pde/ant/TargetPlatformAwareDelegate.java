/**********************************************************************
 * Copyright (c) 2005-2008 ant4eclipse project team.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nils Hartmann, Daniel Kasmeroglu, Gerd Wuetherich
 **********************************************************************/
package org.ant4eclipse.pde.ant;

/**
 * <p>
 * </p>
 * 
 * @author Gerd W&uuml;therich (gerd@gerd-wuetherich.de)
 */
public class TargetPlatformAwareDelegate implements TargetPlatformAwareComponent {

  /** the target platform id */
  private String _targetPlatformId;

  /**
   * <p>
   * Creates a new instance of type AbstractPdeBuildTask.
   * </p>
   * 
   */
  public TargetPlatformAwareDelegate() {
    super();
  }

  /**
   * <p>
   * Sets the target platform against which the workspace plugins will be compiled and tested.
   * </p>
   * 
   * @param targetPlatformLocation
   *          the target platform against which the workspace plugins will be compiled and tested.
   */
  public final void setTargetPlatformId(final String targetPlatformId) {
    _targetPlatformId = targetPlatformId;
  }

  /**
   * <p>
   * Returns whether the target platform location is set.
   * </p>
   * 
   * @return whether the target platform location is set.
   */
  public final boolean isTargetPlatformId() {
    return this._targetPlatformId != null;
  }

  public final String getTargetPlatformId() {
    return _targetPlatformId;
  }
}