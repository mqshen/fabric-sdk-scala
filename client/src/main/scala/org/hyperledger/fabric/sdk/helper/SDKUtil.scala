package org.hyperledger.fabric.sdk.helper

import java.io._
import java.nio.file.{ FileVisitOption, Files, Paths }
import java.util.{ Comparator, Properties, UUID }
import java.util.regex.Pattern

import com.google.common.io.ByteStreams
import io.netty.util.internal.StringUtil
import org.apache.commons.compress.archivers.tar.{ TarArchiveEntry, TarArchiveOutputStream }
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.io.{ FileUtils, FilenameUtils, IOUtils }
import org.bouncycastle.crypto.Digest

import scala.collection.JavaConversions._
import scala.util.Random

/**
 * Created by goldratio on 17/02/2017.
 */
object SDKUtil {
  val NonceSize = 24

  def checkGrpcUrl(url: String) = {
    try {
      parseGrpcUrl(url)
      true
    } catch {
      case e: Exception =>
        false
    }
  }

  def parseGrpcUrl(url: String) = {
    if (StringUtil.isNullOrEmpty(url)) throw new RuntimeException("URL cannot be null or empty")
    val props = new Properties
    val p = Pattern.compile("([^:]+)[:]//([^:]+)[:]([0-9]+)", Pattern.CASE_INSENSITIVE)
    val m = p.matcher(url)
    if (m.matches) {
      props.setProperty("protocol", m.group(1))
      props.setProperty("host", m.group(2))
      props.setProperty("port", m.group(3))
    } else throw new RuntimeException("URL must be of the format protocol://host:port")
    // TODO: allow all possible formats of the URL
    props
  }

  def generateUUID = UUID.randomUUID.toString

  def generateNonce = {
    //Arra0wy.fill(NonceSize)((scala.util.Random.nextInt(256) - 128).toByte)
    val arr = new Array[Byte](NonceSize)
    val nonce = Random.nextBytes(arr)
    arr
  }

  def combinePaths(first: String, other: String*) = {
    Paths.get(first, other: _*).toString
  }

  def readFileFromClasspath(fileName: String) = {
    val is = getClass.getClassLoader.getResourceAsStream(fileName)
    try {
      ByteStreams.toByteArray(is)
    } catch {
      case ex: IOException =>
        throw ex
    } finally {
      is.close()
    }
  }

  def generateTarGz(src: String, target: String) = {
    val sourceDirectory = new File(src)
    val destinationArchive = new File(target)
    val sourcePath = sourceDirectory.getAbsolutePath
    val destinationOutputStream = new FileOutputStream(destinationArchive)
    val archiveOutputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(new BufferedOutputStream(destinationOutputStream)))
    archiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
    try {
      val childrenFiles = org.apache.commons.io.FileUtils.listFiles(sourceDirectory, null, true)
      childrenFiles.remove(destinationArchive)

      childrenFiles.filter(!_.getName.endsWith("DS_Store")).foreach { childFile =>
        val childPath = childFile.getAbsolutePath
        val relativePath = FilenameUtils.separatorsToUnix(childPath.substring(sourcePath.length + 1, childPath.length))
        val archiveEntry = new TarArchiveEntry(childFile, relativePath)
        val fileInputStream = new FileInputStream(childFile)
        archiveOutputStream.putArchiveEntry(archiveEntry)
        try
          IOUtils.copy(fileInputStream, archiveOutputStream)
        finally {
          IOUtils.closeQuietly(fileInputStream)
          archiveOutputStream.closeArchiveEntry()
        }
      }
    } finally IOUtils.closeQuietly(archiveOutputStream)
  }

  def readFile(input: File) = {
    Files.readAllBytes(Paths.get(input.getAbsolutePath))
  }

  def deleteFileOrDirectory(file: File) {
    if (file.exists) {
      if (file.isDirectory) {
        FileUtils.deleteDirectory(file)
      } else file.delete
    } else throw new RuntimeException("File or directory does not exist")
  }

  def hash(input: Array[Byte], digest: Digest): Array[Byte] = {
    val retValue = new Array[Byte](digest.getDigestSize)
    digest.update(input, 0, input.length)
    digest.doFinal(retValue, 0)
    retValue
  }
}
