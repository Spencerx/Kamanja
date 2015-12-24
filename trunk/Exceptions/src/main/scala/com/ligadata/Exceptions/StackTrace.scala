/*
 * Copyright 2015 ligaDATA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ligadata.Exceptions

import java.io.{ InputStream, FileInputStream, File, StringWriter, PrintWriter }

object StackTrace {
  def ThrowableTraceString(t: Throwable): String = {
    if (t == null) return ""
    val sw = new StringWriter();
    val pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }
}

