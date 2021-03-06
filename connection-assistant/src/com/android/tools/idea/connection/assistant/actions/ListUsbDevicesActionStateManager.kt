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
package com.android.tools.idea.connection.assistant.actions

import com.android.annotations.VisibleForTesting
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.datamodel.DefaultActionState
import com.android.tools.idea.assistant.view.StatefulButtonMessage
import com.android.tools.idea.assistant.view.UIUtils
import com.android.tools.idea.stats.withProjectId
import com.android.tools.usb.Platform
import com.android.tools.usb.UsbDevice
import com.android.tools.usb.UsbDeviceCollector
import com.android.tools.usb.UsbDeviceCollectorImpl
import com.android.utils.HtmlBuilder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ConnectionAssistantEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.util.AndroidBundle
import java.util.*
import java.util.concurrent.CompletableFuture

val Logger = com.intellij.openapi.diagnostic.Logger.getInstance(ListUsbDevicesActionStateManager::class.java)

/**
 * StateManager for {@link ListUsbDevicesAction}, displays if there are any connected USB devices to the user
 * through state message.
 */
class ListUsbDevicesActionStateManager : AssistActionStateManager(), Disposable {
  private lateinit var usbDeviceCollector: UsbDeviceCollector
  private lateinit var myProject: Project
  private lateinit var myDevicesFuture: CompletableFuture<List<UsbDevice>>

  companion object {
    private lateinit var myInstance: ListUsbDevicesActionStateManager

    fun getInstance(): ListUsbDevicesActionStateManager = myInstance
  }

  override fun init(project: Project, actionData: ActionData) {
    init(project, actionData, UsbDeviceCollectorImpl())
  }

  @VisibleForTesting
  fun init(project: Project, actionData: ActionData, deviceCollector: UsbDeviceCollector) {
    myProject = project
    usbDeviceCollector = deviceCollector
    myInstance = this
    refresh()
    Disposer.register(project, this)
  }

  private fun getDevices(): CompletableFuture<List<UsbDevice>> {
    return myDevicesFuture.exceptionally(
      {
        Logger.warn(it)
        Collections.emptyList()
      }
    )
  }

  fun refresh() {
    myDevicesFuture = usbDeviceCollector.listUsbDevices()
    getDevices().thenAccept {
      if (!myProject.isDisposed) {
        UsageTracker.log(
          AndroidStudioEvent.newBuilder()
            .setKind(AndroidStudioEvent.EventKind.CONNECTION_ASSISTANT_EVENT)
            .setConnectionAssistantEvent(
              ConnectionAssistantEvent.newBuilder()
                .setType(ConnectionAssistantEvent.ConnectionAssistantEventType.USB_DEVICES_DETECTED)
                .setUsbDevicesDetected(it.size)
            )
            .withProjectId(myProject)
        )
        refreshDependencyState(myProject)
      }
    }
    refreshDependencyState(myProject)
  }

  override fun dispose() {
    myDevicesFuture.cancel(true)
  }

  override fun getState(project: Project, actionData: ActionData): AssistActionState {
    if (!myDevicesFuture.isDone) return DefaultActionState.IN_PROGRESS

    return if (getDevices().get().isEmpty()) DefaultActionState.ERROR_RETRY else CustomSuccessState
  }

  override fun getStateDisplay(project: Project, actionData: ActionData, message: String?): StatefulButtonMessage? {
    val (title, body) = generateMessage()
    return StatefulButtonMessage(title, getState(project, actionData), body)
  }

  override fun getId(): String = ListUsbDevicesAction.ACTION_ID

  private fun generateMessage(): ButtonMessage {
    if (!myDevicesFuture.isDone) return ButtonMessage("Loading...")
    val devices = myDevicesFuture.get().sortedBy { it.name }

    val titleHtmlBuilder = HtmlBuilder().openHtmlBody()
      if (devices.isNotEmpty()) {
        titleHtmlBuilder
          .addHtml("<span style=\"color: ${UIUtils.getCssColor(
            UIUtils.getSuccessColor())};\">Android Studio detected the following ${devices.size} USB device(s):</span>")
      } else {
        titleHtmlBuilder
          .addHtml("<span style=\"color: ${UIUtils.getCssColor(
            UIUtils.getFailureColor())};\">${AndroidBundle.message("connection.assistant.usb.no_devices.title")}</span>")
      }

    val bodyHtmlBuilder = HtmlBuilder().openHtmlBody()
    if (devices.isNotEmpty()) {
      devices.forEach { (name, _, productId) ->
        bodyHtmlBuilder.addHtml("<p>")
          .addHtml("<b>$name</b> ($productId)")
          .newlineIfNecessary().addHtml("</p>")
      }
    } else {
      bodyHtmlBuilder.addHtml("<p>${AndroidBundle.message("connection.assistant.usb.no_devices.body")}</p>")
        .newlineIfNecessary()
    }

    if (usbDeviceCollector.getPlatform() == Platform.Windows) {
      bodyHtmlBuilder.addHtml(
        "<p><b>Install device drivers.</b> If you want to connect a device for testing, " +
        "then you need to install the appropriate USB drivers. For more information, read the " +
        "<a href=\"https://developer.android.com/studio/run/oem-usb.html\">online documentation</a>.</p>"
      )
    }

    return ButtonMessage(titleHtmlBuilder.closeHtmlBody().html, bodyHtmlBuilder.closeHtmlBody().html)
  }

}
