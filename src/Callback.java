/*
 * Copyright (c) 2010-2012  The SUAsync Authors.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *   - Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   - Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   - Neither the name of the StumbleUpon nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
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
