package com.virtuslab.pulumikotlin.codegen.step3_codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.virtuslab.pulumikotlin.codegen.moveSdk
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute

interface WriteableFile {
    fun writeTo(destination: String)
}

/**
 * Example: pathDifference("/a/b/c", "/a/b/c/d/e") == "d/e"
 */
fun pathDifference(shorterPath: Path, longerPath: Path): Path {
    val shorterList = shorterPath.toList()
    val longerList = longerPath.toList()
    require(shorterList.size < longerList.size)

    val differenceSize = longerList.size - shorterList.size
    val shouldBeShorterList = longerList.dropLast(differenceSize)
    require(shouldBeShorterList == shorterList)

    return longerList.takeLast(differenceSize).fold(Path.of("/")) { path1, path2 -> path1.resolve(path2) }
}

class ExistingFile(private val basePath: String, private val path: String): WriteableFile {
    override fun writeTo(destination: String) {
        val pathDiff = pathDifference(Path.of(basePath), Path.of(path))
        val realDestination = Path.of(destination).resolve(pathDiff)

        File(path).copyRecursively(realDestination.toFile(), overwrite = true)
    }
}

class InMemoryGeneratedFile(private val funSpec: FileSpec): WriteableFile {
    override fun writeTo(destination: String) {
        funSpec.writeTo(File(destination))
    }

}