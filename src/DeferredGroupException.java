/*
 * Copyright 2010 StumbleUpon, Inc.
 *
 * This library is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.stumbleupon.async;

import java.util.ArrayList;

/**
 * Exception used when one the {@link Deferred}s in a group failed.
 * <p>
 * You can create a group of {@link Deferred}s using {@link Deferred#group}.
 */
public final class DeferredGroupException extends RuntimeException {

  private final ArrayList<Object> results;

  /**
   * Constructor.
   * @param results All the results of the {@link DeferredGroup}.
   * @param first The first exception among those results.
   */
  DeferredGroupException(final ArrayList<Object> results,
                         final Exception first) {
    super("At least one of the Deferreds failed, first exception:", first);
    this.results = results;
  }

  /**
   * Returns all the results of the group of {@link Deferred}s.
   */
  public ArrayList<Object> results() {
    return results;
  }

  private static final long serialVersionUID = 1281980542;

}
