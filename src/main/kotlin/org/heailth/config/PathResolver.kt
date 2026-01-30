package org.heailth.config

import java.nio.file.Path
import java.nio.file.Paths

class PathResolver(private val basePath: String?) {
    fun resolve(path: String?): String? {
        if (path == null) return null
        if (basePath == null) return path
        val p = Paths.get(path)
        if (p.isAbsolute) return path
        return Paths.get(basePath).resolve(p).toString()
    }
}
