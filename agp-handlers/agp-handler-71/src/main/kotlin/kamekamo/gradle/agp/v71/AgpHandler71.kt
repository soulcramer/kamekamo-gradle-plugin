/*
 * Copyright (C) 2022 Slack Technologies, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kamekamo.gradle.agp.v71

import com.android.Version
import com.google.auto.service.AutoService
import kamekamo.gradle.agp.AgpHandler
import kamekamo.gradle.agp.AgpHandlerFactory
import kamekamo.gradle.agp.VersionNumber

@AutoService(AgpHandlerFactory::class)
internal class AgpHandlerFactory71 : AgpHandlerFactory {
  override val minVersion: VersionNumber = VersionNumber.parse("7.1.0")

  override fun currentVersion(): String {
    return Version.ANDROID_GRADLE_PLUGIN_VERSION
  }

  override fun create(): AgpHandler {
    return AgpHandler71()
  }
}

private class AgpHandler71 : AgpHandler {

  override val agpVersion: String = Version.ANDROID_GRADLE_PLUGIN_VERSION
}
