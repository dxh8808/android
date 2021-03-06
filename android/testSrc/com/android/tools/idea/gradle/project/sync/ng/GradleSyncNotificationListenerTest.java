/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.testing.IdeComponents;
import com.intellij.build.SyncViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.OutputBuildEvent;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEventUnsupportedImpl;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleSyncNotificationListener}.
 */
public class GradleSyncNotificationListenerTest extends IdeaTestCase {
  @Mock private ProgressIndicator myIndicator;
  @Mock private ExternalSystemTaskId myTaskId;
  @Mock private BuildOutputInstantReaderImpl myOutputReader;
  @Mock private SyncViewManager mySyncViewManager;
  private SyncExecutor.GradleSyncNotificationListener myListener;
  private ArgumentCaptor<BuildEvent> myEventCaptor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    IdeComponents ideComponents = new IdeComponents(getProject());
    ideComponents.replaceProjectService(SyncViewManager.class, mySyncViewManager);
    myListener = new SyncExecutor.GradleSyncNotificationListener(myTaskId, myIndicator, myOutputReader);
    myEventCaptor = ArgumentCaptor.forClass(BuildEvent.class);
    when(myTaskId.findProject()).thenReturn(getProject());
  }

  /**
   * Verify that output is processed by output reader
   */
  public void testOnTaskOutput() {
    String stdoutText = "Output text with stdout";
    String noStdoutText = "Output text with NO stdout";
    myListener.onTaskOutput(myTaskId, stdoutText, true);
    myListener.onTaskOutput(myTaskId, noStdoutText, false);

    // Check that the output was passed to reader
    verify(myOutputReader).append(stdoutText);
    verify(myOutputReader).append(noStdoutText);

    // Sync view also processed the event
    verify(mySyncViewManager, times(2)).onEvent(myEventCaptor.capture());
    List<BuildEvent> capturedEvents = myEventCaptor.getAllValues();
    assertThat(capturedEvents).hasSize(2);
    BuildEvent firstEvent = capturedEvents.get(0);

    assertThat(firstEvent).isInstanceOf(OutputBuildEvent.class);
    OutputBuildEvent stdoutEvent = (OutputBuildEvent)firstEvent;
    assertThat(stdoutEvent.isStdOut()).isTrue();
    assertThat(stdoutEvent.getMessage()).isEqualTo(stdoutText);

    BuildEvent secondEvent = capturedEvents.get(1);
    OutputBuildEvent noStdoutEvent = (OutputBuildEvent)secondEvent;
    assertThat(noStdoutEvent.isStdOut()).isFalse();
    assertThat(noStdoutEvent.getMessage()).isEqualTo(noStdoutText);
  }

  /**
   * Verify that events appear correctly on indicator and sync view when onStatusChange is called
   */
  public void testOnStatusChange() {
    myListener.onStatusChange(createNotification("Notification 1"));
    myListener.onStatusChange(createExecution("Execution 1"));
    myListener.onStatusChange(createExecution("Execution 2"));
    myListener.onStatusChange(createNotification("Notification 2"));

    // Check that the indicator text changes bar message changes
    verify(myIndicator).setText("Gradle Sync: Notification 1");
    verify(myIndicator).setText("Gradle Sync: Notification 2");
    verify(myIndicator).setText("Gradle Sync: Execution 1");
    verify(myIndicator).setText("Gradle Sync: Execution 2");

    // Sync view only shows execution event
    verify(mySyncViewManager, times(2)).onEvent(myEventCaptor.capture());
    List<BuildEvent> capturedEvents = myEventCaptor.getAllValues();
    assertThat(capturedEvents).hasSize(2);
    assertThat(capturedEvents.get(0).getMessage()).isEqualTo("Execution 1");
    assertThat(capturedEvents.get(1).getMessage()).isEqualTo("Execution 2");
  }

  /**
   * Verify that the reader is closed when onEnd is called
   */
  public void testOnEnd() {
    myListener.onEnd(myTaskId);
    verify(myOutputReader).close();
  }

  /**
   * Verify that the reader is closed and a finish event is created
   */
  public void testOnCancel() {
    myListener.onCancel(myTaskId);
    verify(myOutputReader).close();

    verify(mySyncViewManager).onEvent(myEventCaptor.capture());
    List<BuildEvent> capturedEvents = myEventCaptor.getAllValues();
    assertThat(capturedEvents).hasSize(1);
    assertThat(capturedEvents.get(0)).isInstanceOf(FinishBuildEvent.class);
    assertThat(capturedEvents.get(0).getMessage()).isEqualTo("cancelled");
  }

  @NotNull
  private ExternalSystemTaskNotificationEvent createNotification(@NotNull String description) {
    return new ExternalSystemTaskNotificationEvent(myTaskId, description);
  }

  @NotNull
  private ExternalSystemTaskExecutionEvent createExecution(@NotNull String description) {
    ExternalSystemProgressEvent progressEvent = new ExternalSystemProgressEventUnsupportedImpl(description);
    return new ExternalSystemTaskExecutionEvent(myTaskId, progressEvent);
  }
}