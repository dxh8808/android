/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.resourceExplorer.sketchImporter

import com.android.tools.idea.resourceExplorer.sketchImporter.parser.SketchParser
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.document.SketchDocument
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.meta.SketchMeta
import com.android.tools.idea.resourceExplorer.sketchImporter.parser.pages.SketchPage
import java.io.File
import java.io.FileInputStream

class SketchTestUtils {
  companion object {
    fun parsePage(path: String): SketchPage {
      return SketchParser.parseJson(FileInputStream(File(path)), SketchPage::class.java)!!
    }

    fun parseDocument(path: String): SketchDocument {
      return SketchParser.parseJson(FileInputStream(File(path)), SketchDocument::class.java)!!
    }

    fun parseMeta(path: String): SketchMeta {
      return SketchParser.parseJson(FileInputStream(File(path)), SketchMeta::class.java)!!
    }
  }
}