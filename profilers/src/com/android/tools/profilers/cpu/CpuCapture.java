/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.cpu;


import com.android.tools.adtui.model.ConfigurableDurationData;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.CpuProfiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class CpuCapture implements ConfigurableDurationData {

  private final int myMainThreadId;

  @NotNull
  private ClockType myClockType;

  @NotNull
  private final TraceParser myParser;

  /**
   * ID of the trace used to generate the capture.
   */
  private final int myTraceId;

  /**
   * Technology used to generate the capture.
   */
  private final CpuProfiler.CpuProfilerType myType;

  public CpuCapture(@NotNull TraceParser parser, int traceId, CpuProfiler.CpuProfilerType type) {
    myParser = parser;
    myTraceId = traceId;
    myType = type;

    // Try to find the main thread. If there is no actual main thread, we will fall back to the thread with the most information.
    Map.Entry<CpuThreadInfo, CaptureNode> main = null;
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : myParser.getCaptureTrees().entrySet()) {
      if (entry.getKey().isMainThread()) {
        main = entry;
        break;
      }

      if (main == null || main.getValue().getDuration() < entry.getValue().getDuration()) {
        main = entry;
      }
    }
    assert main != null;
    myMainThreadId = main.getKey().getId();

    // Set clock type
    CaptureNode mainNode = getCaptureNode(myMainThreadId);
    assert mainNode != null;
    myClockType = mainNode.getClockType();
  }

  public int getMainThreadId() {
    return myMainThreadId;
  }

  @NotNull
  public Range getRange() {
    return myParser.getRange();
  }

  @Nullable
  public CaptureNode getCaptureNode(int threadId) {
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : myParser.getCaptureTrees().entrySet()) {
      if (entry.getKey().getId() == threadId) {
        return entry.getValue();
      }
    }
    return null;
  }

  @NotNull
  Set<CpuThreadInfo> getThreads() {
    return myParser.getCaptureTrees().keySet();
  }

  public boolean containsThread(int threadId) {
    return myParser.getCaptureTrees().keySet().stream().anyMatch(info -> info.getId() == threadId);
  }

  @Override
  public long getDurationUs() {
    return (long)myParser.getRange().getLength();
  }

  @Override
  public boolean getSelectableWhenMaxDuration() {
    return false;
  }

  public int getTraceId() {
    return myTraceId;
  }

  @Override
  public boolean canSelectPartialRange() {
    return true;
  }

  public void updateClockType(@NotNull ClockType clockType) {
    if (myClockType == clockType) {
      // Avoid traversing the capture trees if there is no change.
      return;
    }
    myClockType = clockType;

    for (CaptureNode tree : myParser.getCaptureTrees().values()) {
      updateClockType(tree, clockType);
    }
  }

  private static void updateClockType(@Nullable CaptureNode node, @NotNull ClockType clockType) {
    if (node == null) {
      return;
    }
    node.setClockType(clockType);
    for (CaptureNode child : node.getChildren()) {
      updateClockType(child, clockType);
    }
  }

  public boolean isDualClock() {
    return myParser.supportsDualClock();
  }

  public CpuProfiler.CpuProfilerType getType() {
    return myType;
  }
}
