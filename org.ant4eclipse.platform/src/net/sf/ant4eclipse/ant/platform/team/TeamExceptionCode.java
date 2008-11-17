/**********************************************************************
 * Copyright (c) 2005-2006 ant4eclipse project team.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nils Hartmann, Daniel Kasmeroglu, Gerd Wuetherich
 **********************************************************************/
package net.sf.ant4eclipse.ant.platform.team;

import net.sf.ant4eclipse.core.exception.AbstractExceptionCode;

public class TeamExceptionCode extends AbstractExceptionCode {
  private static final String           ERROR_WHILE_EXECUTING_CVS_COMMAND_MSG  = "Error while executing CVS '%s' command: '%s' ";

  public static final TeamExceptionCode ERROR_WHILE_EXECUTING_CVS_COMMAND      = new TeamExceptionCode(
                                                                                   ERROR_WHILE_EXECUTING_CVS_COMMAND_MSG);

  private static final String           ERROR_WHILE_EXECUTING_SVN_COMMAND_MSG  = "Error while executing SVN '%s' command: '%s' ";

  public static final TeamExceptionCode ERROR_WHILE_EXECUTING_SVN_COMMAND      = new TeamExceptionCode(
                                                                                   ERROR_WHILE_EXECUTING_SVN_COMMAND_MSG);

  private static final String           COULD_NOT_BUILD_SVNURL_FOR_PROJECT_MSG = "Could not create an SVNUrl from URL '%s' of team project description '%s': '%s'";

  public static final TeamExceptionCode COULD_NOT_BUILD_SVNURL_FOR_PROJECT     = new TeamExceptionCode(
                                                                                   COULD_NOT_BUILD_SVNURL_FOR_PROJECT_MSG);

  public static final TeamExceptionCode UNKNOWN_TEAM_PROJECT_SET_PROVIDER      = new TeamExceptionCode(
                                                                                   "The team project set provider with id '%s' is unkown");

  public TeamExceptionCode(String message) {
    super(message);
  }

}
