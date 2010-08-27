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

/**
 * A simple 1-argument callback interface.
 * <p>
 * Callbacks are typically created as anonymous classes.  In order to make
 * debugging easier, it is recommended to override the {@link Object#toString}
 * method so that it returns a string that briefly explains what the callback
 * does.  If you use {@link Deferred} with {@code DEBUG} logging turned on,
 * this will be really useful to understand what's in the callback chains.
 * @param <R> The return type of the callback.
 * @param <T> The argument type of the callback.
 */
public interface Callback<R, T> {

  /**
   * The callback.
   * @param arg The argument to the callback.
   * @return The return value of the callback.
   * @throws Exception any exception.
   */
  public R call(T arg) throws Exception;

  /** The identity function (returns its argument).  */
  public static final Callback<Object, Object> PASSTHROUGH =
    new Callback<Object, Object>() {
      public Object call(final Object arg) {
        return arg;
      }
      public String toString() {
        return "passthrough";
      }
    };

}
