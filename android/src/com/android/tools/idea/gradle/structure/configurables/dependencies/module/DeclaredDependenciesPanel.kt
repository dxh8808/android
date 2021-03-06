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
package com.android.tools.idea.gradle.structure.configurables.dependencies.module

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.ModuleDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.SingleDeclaredLibraryDependencyDetails
import com.android.tools.idea.gradle.structure.configurables.dependencies.treeview.DependencySelection
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesViewer
import com.android.tools.idea.gradle.structure.configurables.issues.SingleModuleIssuesRenderer
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.AbstractDependenciesPanel
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.DeclaredDependenciesTableView
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.google.common.collect.Lists
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.isEmpty
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Panel that displays the table of "editable" dependencies.
 */
internal class DeclaredDependenciesPanel(
  val module: PsModule, context: PsContext
) : AbstractDependenciesPanel("Declared Dependencies", context, module), DependencySelection {

  private val dependenciesTableModel: DeclaredDependenciesTableModel
  private val dependenciesTable: DeclaredDependenciesTableView<PsBaseDependency>
  private val placeName: String

  private val eventDispatcher = SelectionChangeEventDispatcher<PsBaseDependency>()

  private var skipSelectionChangeNotification: Boolean = false

  init {
    context.analyzerDaemon.add(
      { model ->
        if (model === module) {
          invokeLaterIfNeeded { this.updateDetailsAndIssues() }
        }
      }, this)

    placeName = createPlaceName(module.name)

    contentsPanel.add(createActionsPanel(), BorderLayout.NORTH)
    initializeDependencyDetails()

    setIssuesViewer(IssuesViewer(context, SingleModuleIssuesRenderer(context)))

    dependenciesTableModel = DeclaredDependenciesTableModel(
      module, context)
    dependenciesTable = DeclaredDependenciesTableView(dependenciesTableModel, context)

    module.addDependencyChangedListener(this) { event ->
      val oldSelection = dependenciesTable.selection
      dependenciesTableModel.reset()
      var toSelect: PsBaseDependency? = null
      if (event is PsModule.LibraryDependencyAddedEvent) {
        dependenciesTable.clearSelection()
        toSelect = dependenciesTableModel.findDependency(event.spec)
      }
      else if (event is PsModule.DependencyModifiedEvent) {
        toSelect = event.dependency
      }
      dependenciesTable.selection = toSelect?.let { listOf(it) } ?: oldSelection
    }

    dependenciesTable.selectionModel.addListSelectionListener { updateDetailsAndIssues() }
    dependenciesTable.selectFirstRow()

    val scrollPane = createScrollPane(dependenciesTable)
    scrollPane.border = JBUI.Borders.empty()
    contentsPanel.add(scrollPane, BorderLayout.CENTER)

    updateTableColumnSizes()
  }

  private fun createPlaceName(moduleName: String): String = "dependencies.$moduleName.place"

  private fun initializeDependencyDetails() {
    addDetails(SingleDeclaredLibraryDependencyDetails(context))
    addDetails(ModuleDependencyDetails(context, true))
  }

  override fun getPreferredFocusedComponent(): JComponent = dependenciesTable

  public override fun getPlaceName(): String = placeName

  override fun getExtraToolbarActions(): List<AnAction> {
    val actions = Lists.newArrayList<AnAction>()
    actions.add(RemoveDependencyAction())
    return actions
  }

  fun updateTableColumnSizes() {
    dependenciesTable.updateColumnSizes()
  }

  override fun dispose() {
    Disposer.dispose(dependenciesTable)
  }

  fun add(listener: SelectionChangeListener<PsBaseDependency>) {
    eventDispatcher.addListener(listener, this)
    notifySelectionChanged()
  }

  override fun getSelection(): PsBaseDependency? = dependenciesTable.selectionIfSingle

  override fun setSelection(selection: PsBaseDependency?): ActionCallback {
    skipSelectionChangeNotification = true
    if (selection == null) {
      dependenciesTable.clearSelection()
    }
    else {
      dependenciesTable.setSelection(setOf(selection))
    }
    updateDetailsAndIssues()
    skipSelectionChangeNotification = false
    return ActionCallback.DONE
  }

  private fun updateDetailsAndIssues() {
    if (!skipSelectionChangeNotification) {
      notifySelectionChanged()
    }

    val selected = selection
    super.updateDetails(selected)
    updateIssues(selected)

    val history = history
    history?.pushQueryPlace()
  }

  private fun notifySelectionChanged() {
    val selected = selection
    if (selected != null) {
      eventDispatcher.selectionChanged(selected)
    }
  }

  private fun updateIssues(selected: PsBaseDependency?) {
    displayIssues(context.analyzerDaemon.issues.findIssues(selected?.path, null), selected?.path)
  }

  fun selectDependency(dependency: String?) {
    if (isEmpty(dependency)) {
      dependenciesTable.clearSelection()
      return
    }
    doSelectDependency(dependency!!)
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback {
    if (place != null) {
      val path = place.getPath(placeName)
      if (path is String) {
        val pathText = path as String?
        if (!pathText!!.isEmpty()) {
          dependenciesTable.requestFocusInWindow()
          doSelectDependency(pathText)
        }
      }
    }
    return ActionCallback.DONE
  }

  private fun doSelectDependency(toSelect: String) {
    dependenciesTable.selectDependency(toSelect)
  }

  private inner class RemoveDependencyAction internal constructor() : DumbAwareAction("Remove Dependency...", "", AllIcons.Actions.Delete) {
    init {
      registerCustomShortcutSet(CommonShortcuts.getDelete(), dependenciesTable)
    }

    override fun update(e: AnActionEvent) {
      val details = currentDependencyDetails
      e.presentation.isEnabled = details != null
    }

    override fun actionPerformed(e: AnActionEvent) {
      val dependency = selection
      if (dependency != null) {
        if (Messages.showYesNoDialog(
            e.project,
            "Remove dependency '${dependency.joinedConfigurationNames} ${dependency.name}'?",
            "Remove Dependency",
            Messages.getQuestionIcon()
          ) == Messages.YES) {
          module.removeDependency(dependency as PsDeclaredDependency)
          dependenciesTable.selectFirstRow()
        }
      }
    }
  }
}
