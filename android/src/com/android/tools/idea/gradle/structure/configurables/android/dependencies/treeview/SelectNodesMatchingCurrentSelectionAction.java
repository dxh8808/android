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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public abstract class SelectNodesMatchingCurrentSelectionAction extends DumbAwareAction {
  protected SelectNodesMatchingCurrentSelectionAction() {
    super("Select All Matching Nodes", "", AllIcons.General.Locate);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    AbstractPsModelNode<?> node = getTreeBuilder().getSelectedNode();
    e.getPresentation().setEnabled(node != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    getTreeBuilder().selectNodesMatchingCurrentSelection();
  }

  @NotNull
  protected abstract AbstractPsNodeTreeBuilder getTreeBuilder();
}