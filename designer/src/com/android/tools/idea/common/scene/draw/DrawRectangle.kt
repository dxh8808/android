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
package com.android.tools.idea.common.scene.draw

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.common.scene.SceneContext
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D

data class DrawRectangle(
  private val level: Int,
  @SwingCoordinate private val rectangle: Rectangle2D.Float,
  @SwingCoordinate private val color: Color,
  @SwingCoordinate private val brushThickness: Float
) : DrawCommandBase() {

  private constructor(sp: Array<String>) : this(
    sp[0].toInt(), stringToRect2D(sp[1]),
    stringToColor(sp[2]), sp[3].toFloat()
  )

  constructor(s: String) : this(parse(s, 4))

  override fun getLevel(): Int = level

  override fun serialize(): String = buildString(
    javaClass.simpleName, level, rect2DToString(rectangle),
    colorToString(color), brushThickness
  )

  override fun onPaint(g: Graphics2D, sceneContext: SceneContext) {
    g.setRenderingHints(HQ_RENDERING_HINTS)
    g.color = color
    g.stroke = BasicStroke(brushThickness)
    g.draw(rectangle)
  }
}