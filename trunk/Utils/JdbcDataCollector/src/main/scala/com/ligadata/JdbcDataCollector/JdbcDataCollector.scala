/*
 * Copyright 2015 ligaDATA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import scala.reflect.runtime.universe
import scala.collection.mutable.ArrayBuffer
import java.util.Properties
import java.sql.{ Connection, Statement, ResultSet, DriverManager }
import org.apache.logging.log4j.{ Logger, LogManager }
import java.io.{ OutputStream, FileOutputStream, File, BufferedWriter, Writer, PrintWriter }
import java.util.zip.GZIPOutputStream
import java.nio.file.{ Paths, Files }
import scala.reflect.runtime.{ universe => ru }
import java.net.{ URL, URLClassLoader }
import scala.collection.mutable.TreeSet
import java.sql.{ Driver, DriverPropertyInfo }
import com.ligadata.Exceptions.StackTrace
// ClassLoader
class JdbcClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {
  override def addURL(url: URL) {
    super.addURL(url)
  }
}

class DriverShim(d: Driver) extends Driver {
  private var driver: Driver = d

  def connect(u: String, p: Properties): Connection = this.driver.connect(u, p)

  def acceptsURL(u: String): Boolean = this.driver.acceptsURL(u)

  def getPropertyInfo(u: String, p: Properties): Array[DriverPropertyInfo] = this.driver.getPropertyInfo(u, p)

  def getMajorVersion(): Int = this.driver.getMajorVersion

  def getMinorVersion(): Int = this.driver.getMinorVersion

  def jdbcCompliant(): Boolean = this.driver.jdbcCompliant()

  def getParentLogger(): java.util.logging.Logger = this.driver.getParentLogger()
}

object RunJdbcCollector {
  private val LOG = LogManager.getLogger(getClass);
  private val clsLoader = new JdbcClassLoader(ClassLoader.getSystemClassLoader().asInstanceOf[URLClassLoader].getURLs(), getClass().getClassLoader())
  // private val clsMirror = ru.runtimeMirror(clsLoader)
  private val loadedJars: TreeSet[String] = new TreeSet[String];
  private var collectedRows: Long = 0

  private type OptionMap = Map[Symbol, Any]

  private def PrintUsage(): Unit = {
    LOG.warn("Available commands:")
    LOG.warn("    --config <configfilename>")
  }

  private def LoadJars(jars: Array[String]): Unit = {
    // Loading all jars
    for (j <- jars) {
      val jarNm = j.trim
      LOG.debug("%s:Processing Jar: %s".format(GetCurDtTmStr, jarNm))
      val fl = new File(jarNm)
      if (fl.exists) {
        try {
          if (loadedJars(fl.getPath())) {
            LOG.debug("%s:Jar %s already loaded to class path.".format(GetCurDtTmStr, jarNm))
          } else {
            clsLoader.addURL(fl.toURI().toURL())
            LOG.debug("%s:Jar %s added to class path.".format(GetCurDtTmStr, jarNm))
            loadedJars += fl.getPath()
          }
        } catch {
          case e: Exception => {
            val errMsg = "Jar " + jarNm + " failed added to class path. Reason:%s Message:%s".format(e.getCause, e.getMessage)
            LOG.error("Error:" + errMsg)
            throw new Exception(errMsg)
          }
        }
      } else {
        val errMsg = "Jar " + jarNm + " not found"
        throw new Exception(errMsg)
      }
    }
  }

  private def connect(urlstr: String, userId: String, passwd: String, ssl: String, timeoutInSec: Int): Connection = {
    val props = new Properties();
    props.setProperty("user", userId);
    props.setProperty("password", passwd);
    props.setProperty("ssl", ssl);
    var conn: Connection = null
    try {
      conn = DriverManager.getConnection(urlstr, props);
      // conn.setNetworkTimeout(Executor executor, timeoutInSec)
    } catch {
      case e: Exception => {
        LOG.error("%s:Failed to establish connection. URL:%s, User:%s, Passwd:%s. Message:%s, Reason:%s".format(GetCurDtTmStr, urlstr, userId, passwd, e.getMessage, e.getCause))
      }
    }
    return conn;
  }

  private def executeSelectQry(db: Connection, selectQry: String, timeoutInSec: Int, writeHeaders: Boolean, fieldDelimiter: String, rowDelimiter: String, compressionString: String, sFileName: String): Boolean = {
    var st: Statement = null
    var res: ResultSet = null
    val columnNames = ArrayBuffer[String]()
    var os: OutputStream = null
    var retVal = false

    try {
      try {
        st = db.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        st.setQueryTimeout(timeoutInSec)
      } catch {
        case e: Exception => {
          LOG.error("%s:Failed to create statement. Message:%s, Reason:%s".format(GetCurDtTmStr, e.getMessage, e.getCause))
          return false
        }
      }
      try {
        res = st.executeQuery(selectQry)
      } catch {
        case e: Exception => {
          LOG.error("%s:Failed to exeucte query:%s. Message:%s, Reason:%s".format(GetCurDtTmStr, selectQry, e.getMessage, e.getCause))
          res.close
          return false
        }
      }

      val compString = if (compressionString == null) null else compressionString.trim

      if (compString == null || compString.size == 0) {
        os = new FileOutputStream(sFileName);
      } else if (compString.compareToIgnoreCase("gz") == 0) {
        os = new GZIPOutputStream(new FileOutputStream(sFileName))
      } else {
        LOG.error("%s:Not yet handled other than text & GZ files".format(GetCurDtTmStr))
        return false
      }

      val fldDelim = fieldDelimiter.getBytes("UTF8")
      val rowDelim = rowDelimiter.getBytes("UTF8")

      val columns = res.getMetaData
      var i = 0
      while (i < columns.getColumnCount) {
        i += 1
        val colNm = columns.getColumnName(i)
        columnNames += colNm
        // columnNames += columns.getColumnLabel(i)
        if (writeHeaders) {
          if (i > 1) // i starts from 1, that's why taking > 1
            os.write(fldDelim)
          os.write(colNm.getBytes("UTF8"));
        }
      }

      if (writeHeaders)
        os.write(rowDelim)

      while (res.next) {
        i = 0
        while (i < columnNames.size) {
          val fld = res.getString(columnNames(i))
          var fldVal: String = "" // Default value if value is null/empty
          if (fld != null && !fld.isEmpty)
            fldVal = fld.toString
          if (i > 0) // i starts from 0, that's why taking > 0
            os.write(fldDelim)
          os.write(fldVal.getBytes("UTF8"));
          i += 1
        }
        os.write(rowDelim)
        collectedRows += 1
      }
      retVal = true
    } catch {
      case e: Exception => {
        LOG.error("%s:Exception. Message:%s, Reason:%s".format(GetCurDtTmStr, e.getMessage, e.getCause))
        retVal = false
      }
    } finally {
      try {
        if (os != null)
          os.close
        if (res != null)
          res.close
        if (st != null)
          st.close()
      } catch {
        case e: Exception => {
          LOG.error("%s:Exception. Message:%s, Reason:%s".format(GetCurDtTmStr, e.getMessage, e.getCause))
          retVal = false
        }
      }
    }
    return retVal
  }

  private def nextOption(map: OptionMap, list: List[String]): OptionMap = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => map
      case "--config" :: value :: tail =>
        nextOption(map ++ Map('config -> value), tail)
      case option :: tail => {
        LOG.error("%s:Unknown option:%s".format(GetCurDtTmStr, option))
        sys.exit(1)
      }
    }
  }

  private def GetCurDtTmStr: String = {
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis))
  }

  private def ValueString(v: Any): String = {
    if (v == null) return ""
    if (v.isInstanceOf[Set[_]]) {
      return v.asInstanceOf[Set[_]].mkString(",")
    }
    if (v.isInstanceOf[List[_]]) {
      return v.asInstanceOf[List[_]].mkString(",")
    }
    if (v.isInstanceOf[Array[_]]) {
      return v.asInstanceOf[Array[_]].mkString(",")
    }
    v.toString
  }

  def main(args: Array[String]): Unit = {
    if (args.length == 0) {
      PrintUsage()
      System.exit(1)
    }

    LOG.debug("%s:Parsing options".format(GetCurDtTmStr))
    val options = nextOption(Map(), args.toList)
    val cfgfile = options.getOrElse('config, "").toString.trim
    if (cfgfile.size == 0) {
      LOG.error("%s:Configuration file missing".format(GetCurDtTmStr))
      sys.exit(1)
    }

    val tmpfl = new File(cfgfile)
    if (tmpfl.exists == false) {
      LOG.error("%s:Configuration file %s is not valid".format(GetCurDtTmStr, cfgfile))
      sys.exit(1)
    }

    val configs = new StringBuilder

    var map = scala.collection.mutable.Map[String, Any]()
    try {
      val configJson = scala.io.Source.fromFile(cfgfile).mkString
      implicit val jsonFormats = org.json4s.DefaultFormats
      val json = org.json4s.jackson.JsonMethods.parse(configJson)
      if (json == null) {
        LOG.error("%s:Configuration file %s is not valid".format(GetCurDtTmStr, cfgfile))
        sys.exit(1)
      }
      val tmpmap = json.values.asInstanceOf[Map[String, Any]]

      // Convert all keys to lower case to access the value by name
      tmpmap.foreach(kv => {
        configs.append("\t%s => %s\n".format(kv._1.toString, ValueString(kv._2)))
        map(kv._1.toLowerCase) = kv._2
      })
    } catch {
      case e: Exception => {
        LOG.error("%s:Failed with exception. Message:%s, Reason:%s ".format(GetCurDtTmStr, e.getMessage, e.getCause))
        sys.exit(1)
      }
    }

    var db: Connection = null
    var exitCode = 0

    try {
      val startTime = System.nanoTime
      // get configuration
      val driverType = map.getOrElse("drivername", "").toString.trim
      val urlstr = map.getOrElse("urlstring", "").toString.trim
      val userId = map.getOrElse("userid", "").toString.trim
      val passwd = map.getOrElse("password", "").toString.trim
      var ssl = map.getOrElse("ssl", "false").toString.trim.toLowerCase
      val timeoutInSec = map.getOrElse("timeout", "86400").toString.trim.toInt
      val sqlQry = map.getOrElse("query", "").toString.trim
      val writeHeaders = map.getOrElse("writeheader", "false").toString.trim.toBoolean
      val fieldDelimiter = map.getOrElse("fielddelimiter", "").toString // Don't want to trim here. Because we can get space or anything as delimiter
      val rowDelimiter = map.getOrElse("rowdelimiter", "").toString // Don't want to trim here. Because we can get space or anything as delimiter
      val compressionString = map.getOrElse("compressionstring", "").toString.trim
      val sFileName = map.getOrElse("filename", "").toString.trim
      val depJars = if (map.contains("driverjars")) {
        val lst = map.get("driverjars").get
        if (lst.isInstanceOf[Array[_]])
          lst.asInstanceOf[Array[String]]
        else if (lst.isInstanceOf[List[_]])
          lst.asInstanceOf[List[String]].toArray
        else {
          LOG.error("%s:Expecting Dependency jars are List[String] or Array[String]".format(GetCurDtTmStr))
          sys.exit(1)
        }
      } else {
        null
      }

      // Validate Configuration
      val errs = new StringBuilder
      if (driverType.size == 0)
        errs.append("Error: DriverName must present\n")
      if (urlstr.size == 0)
        errs.append("Error: URLString must present\n")
      if (timeoutInSec <= 0)
        errs.append("Error: Timeout must be positive value\n")
      if (sqlQry.size == 0)
        errs.append("Error: Query must present\n")
      if (sFileName.size == 0)
        errs.append("Error: FileName must present\n")
      if (ssl.size == 0) {
        ssl = "false"
      }
      if (ssl.compareTo("true") != 0 && ssl.compareTo("false") != 0)
        errs.append("Error: ssl value should be true/false\n")

      if (errs.size > 0) {
        LOG.error("%s:%s".format(GetCurDtTmStr, errs.toString))
        sys.exit(1)
      }

      LOG.debug("%s:\nConfigurations:\n%s".format(GetCurDtTmStr, configs.toString))

      // Load dependency jars
      LOG.debug("%s:Loading Driver Jars".format(GetCurDtTmStr))
      LoadJars(depJars)
      LOG.debug("%s:Loading Driver".format(GetCurDtTmStr))

      try {
        Class.forName(driverType, true, clsLoader)
      } catch {
        case e: Exception => {
          LOG.error("Failed to load Driver class %s with Reason:%s Message:%s".format(driverType, e.getCause, e.getMessage))
          sys.exit(1)
        }
      }

      val d = Class.forName(driverType, true, clsLoader).newInstance.asInstanceOf[Driver]
      LOG.debug("%s:Registering Driver".format(GetCurDtTmStr))
      DriverManager.registerDriver(new DriverShim(d));

      LOG.debug("%s:Connecting with URLString:%s and UserId:%s".format(GetCurDtTmStr, urlstr, userId))
      db = connect(urlstr, userId, passwd, ssl, timeoutInSec)
      if (db != null) {
        LOG.debug("%s:Executing Query:%s and collecting output into file:%s".format(GetCurDtTmStr, sqlQry, sFileName))
        val execStatus = executeSelectQry(db, sqlQry, timeoutInSec, writeHeaders, fieldDelimiter, rowDelimiter, compressionString, sFileName)
        if (execStatus) {
          val endTime = System.nanoTime
          LOG.debug("%s:Successfully collected %d rows in %dms".format(GetCurDtTmStr, collectedRows, (endTime - startTime) / 1000000))
          exitCode = 0
        } else {
          exitCode = 1
        }
      } else {
        exitCode = 1
      }
    } catch {
      case e: Exception => {
        LOG.error("%s:Exception:%s. Message:%s, Reason:%s".format(GetCurDtTmStr, e.toString, e.getMessage, e.getCause))
        exitCode = 1
      }
    } finally {
      try {
        if (db != null)
          db.close
      } catch {
        case e: Exception => {
          LOG.error("%s:Exception:%s. Message:%s, Reason:%s".format(GetCurDtTmStr, e.toString, e.getMessage, e.getCause))
          exitCode = 1
        }
      }
    }

    sys.exit(exitCode)
  }
}

