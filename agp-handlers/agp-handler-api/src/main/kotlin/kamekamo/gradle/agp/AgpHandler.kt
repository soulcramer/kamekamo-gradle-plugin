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
package kamekamo.gradle.agp

/** An interface for handling different AGP versions via (mostly) version-agnostic APIs. */
public interface AgpHandler {

  /** The current AGP version. */
  public val agpVersion: String
}

/**
 * Raw AGP [VersionNumber], which can include qualifiers like `-beta03`. Not usually what you want
 * in comparisons.
 */
internal val AgpHandler.agpVersionNumberRaw: VersionNumber
  get() = VersionNumber.parse(agpVersion)

/**
 * Base AGP [VersionNumber], which won't include qualifiers like `-beta03`. Usually what you want in
 * comparisons.
 */
public val AgpHandler.agpVersionNumber: VersionNumber
  get() = agpVersionNumberRaw.baseVersion
