// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.editor

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper

abstract class ComplexPathVirtualFileSystem<P : ComplexPathVirtualFileSystem.Path> : DeprecatedVirtualFileSystem() {
  protected abstract val pathClass: Class<P>
  protected open val gson = Gson()

  protected abstract fun findFile(project: Project, path: P): VirtualFile?
  protected fun serializePath(path: P): String = gson.toJson(path)
  protected fun deserializePath(path: String): P = gson.fromJson(path, pathClass)

  interface Path {
    /**
     * [sessionId] is required to differentiate files between launches.
     * This is necessary to make the files appear in "Recent Files" correctly.
     * Without this field files are saved in [com.intellij.openapi.fileEditor.impl.EditorHistoryManager] via pointers and urls are saved to disk
     * After reopening the project manager will try to restore the files and will not find them since necessary components are not ready yet
     * and despite this history entry will still be created using a url-only [com.intellij.openapi.vfs.impl.IdentityVirtualFilePointer] via
     * [com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl.getOrCreateIdentity] where pointers are cached.
     * As a result all previously opened files will be seen by history manager as non-existent.
     * Including this [sessionId] helps distinguish files between launches.
     */
    val sessionId: String
    val projectHash: String
  }

  fun serializePathSafe(path: P): String? {
    try {
      return serializePath(path)
    }
    catch (e: Exception) {
      LOG.warn("Cannot serialize $path", e)
      return null
    }
  }

  fun deserializePathSafe(path: String): P? {
    try {
      return deserializePath(path)
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize $path", e)
      return null
    }
  }

  override fun findFileByPath(path: String): VirtualFile? {
    val parsedPath = deserializePathSafe(path) ?: return null
    val project = ProjectManagerEx.getInstanceEx().findOpenProjectByHash(parsedPath.projectHash) ?: return null
    return findFile(project, parsedPath)
  }

  override fun refreshAndFindFileByPath(path: String) = findFileByPath(path)

  override fun extractPresentableUrl(path: String) = (findFileByPath(path) as? VirtualFilePathWrapper)?.presentablePath ?: path

  override fun refresh(asynchronous: Boolean) {}

  companion object {
    private val LOG = logger<ComplexPathVirtualFileSystem<Path>>()
  }
}
