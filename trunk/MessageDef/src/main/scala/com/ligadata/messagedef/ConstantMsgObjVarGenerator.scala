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

package com.ligadata.messagedef

import com.ligadata.kamanja.metadata.MdMgr
import scala.collection.mutable.ArrayBuffer
import org.apache.logging.log4j.{ Logger, LogManager }
import com.ligadata.Exceptions.StackTrace

class ConstantMsgObjVarGenerator {

  var rddHandler = new RDDHandler
  val logger = this.getClass.getName
  lazy val LOG = LogManager.getLogger(logger)

  def partitionkeyStrObj(message: Message, partitionPos: Array[Int]): String = {
    val pad1 = "\t"
    var partitionStr = new StringBuilder
    partitionStr.append("Array(")
    for (p <- partitionPos) {
      partitionStr.append(p + ",")
    }
    var partitionString = partitionStr.substring(0, partitionStr.length - 1)
    partitionString = partitionString + ")"

    val partitionKeys = if (message.PartitionKey != null && message.PartitionKey.size > 0) ("Array(\"" + message.PartitionKey.map(p => p.toLowerCase).mkString("\", \"") + "\")") else ""

    if (partitionKeys != null && partitionKeys.trim() != "")
      "\n	val partitionKeys : Array[String] = " + partitionKeys + "\n    val partKeyPos = " + partitionString.toString + getPartitionKeyDef
    else
      "\n   val partitionKeys : Array[String] =  Array[String](); \n   override def PartitionKeyData(inputdata:InputData): Array[String] = Array[String]()"
  }

  def primarykeyStrObj(message: Message, primaryPos: Array[Int]): String = {
    val prmryKeys = if (message.PrimaryKeys != null && message.PrimaryKeys.size > 0) ("Array(\"" + message.PrimaryKeys.map(p => p.toLowerCase).mkString("\", \"") + "\")") else ""
    val pad1 = "\t"
    var primaryStr = new StringBuilder
    LOG.debug("primaryPos " + primaryPos.length)
    primaryStr.append("Array(")
    for (p <- primaryPos) {
      primaryStr.append(p + ",")
    }
    var primaryString = primaryStr.substring(0, primaryStr.length - 1)
    primaryString = primaryString + ")"

    if (prmryKeys != null && prmryKeys.trim() != "")
      "\n	val primaryKeys : Array[String] = " + prmryKeys + "\n    val prmryKeyPos = " + primaryString + getPrimaryKeyDef
    else
      "\n	val primaryKeys : Array[String] = Array[String](); \n    override def PrimaryKeyData(inputdata:InputData): Array[String] = Array[String]()"
  }

  def getPrimaryKeyDef(): String = {

    var getPrimaryKeyData = new StringBuilder(8 * 1024)

    getPrimaryKeyData = getPrimaryKeyData.append("""
	  
    override def PrimaryKeyData(inputdata:InputData): Array[String] = {
    	if (prmryKeyPos.size == 0 || primaryKeys.size == 0)
    		return Array[String]()
    	if (inputdata.isInstanceOf[DelimitedData]) {
    		val csvData = inputdata.asInstanceOf[DelimitedData]
    		if (csvData.tokens == null) {
            	return prmryKeyPos.map(pos => "")
            }
    		return prmryKeyPos.map(pos => csvData.tokens(pos))
    	} else if (inputdata.isInstanceOf[JsonData]) {
    		val jsonData = inputdata.asInstanceOf[JsonData]
    		val mapOriginal = jsonData.cur_json.get.asInstanceOf[Map[String, Any]]
    		if (mapOriginal == null) {
    			return prmryKeyPos.map(pos => "")
    		}
    		val map: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
    		mapOriginal.foreach(kv => { map(kv._1.toLowerCase()) = kv._2 })

    		return primaryKeys.map(key => map.getOrElse(key, "").toString)
    	}  else if (inputdata.isInstanceOf[KvData]) {
    		val kvData = inputdata.asInstanceOf[KvData]
    		if (kvData == null) {
    			return prmryKeyPos.map(pos => "")
    		}
    		val map: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
    		kvData.dataMap.foreach(kv => { map(kv._1.toLowerCase()) = kv._2 })

    		return primaryKeys.map(key => map.getOrElse(key, "").toString)

    	} else if (inputdata.isInstanceOf[XmlData]) {
    		val xmlData = inputdata.asInstanceOf[XmlData]
                    // Fix this
    	} else throw new Exception("Invalid input data")
    	return Array[String]()
    }
    """)

    getPrimaryKeyData.toString
  }

  /**
   * getPartition Key Data for Message Defitnition Object   *
   *
   */

  def getPartitionKeyDef(): String = {

    var getParitionData = new StringBuilder(8 * 1024)

    getParitionData = getParitionData.append("""
        
    override def PartitionKeyData(inputdata:InputData): Array[String] = {
    	if (partKeyPos.size == 0 || partitionKeys.size == 0)
    		return Array[String]()
    	if (inputdata.isInstanceOf[DelimitedData]) {
    		val csvData = inputdata.asInstanceOf[DelimitedData]
    			if (csvData.tokens == null) {
            		return partKeyPos.map(pos => "")
            	}
    		return partKeyPos.map(pos => csvData.tokens(pos))
    	} else if (inputdata.isInstanceOf[JsonData]) {
    		val jsonData = inputdata.asInstanceOf[JsonData]
    		val mapOriginal = jsonData.cur_json.get.asInstanceOf[Map[String, Any]]
    		if (mapOriginal == null) {
    			return partKeyPos.map(pos => "")
    		}
    		val map: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
    		mapOriginal.foreach(kv => { map(kv._1.toLowerCase()) = kv._2 })
    		return partitionKeys.map(key => map.getOrElse(key, "").toString)
    	} else if (inputdata.isInstanceOf[KvData]) {
    		val kvData = inputdata.asInstanceOf[KvData]
    		if (kvData == null) {
    			return partKeyPos.map(pos => "")
    		}
    		val map: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
    		kvData.dataMap.foreach(kv => { map(kv._1.toLowerCase()) = kv._2 })
    		return partitionKeys.map(key => map.getOrElse(key, "").toString)
    	}else if (inputdata.isInstanceOf[XmlData]) {
    		val xmlData = inputdata.asInstanceOf[XmlData]
                    // Fix this
    	} else throw new Exception("Invalid input data")
    	return Array[String]()
    }
    """)

    getParitionData.toString
  }

  def getTimePartitionInfo = {

    """
   def getTimePartitionKeyData(inputdata: InputData): String = {

    if (timeParitionKey == null || timeParitionKey.trim == "" || timeParitionKeyPos < 0)
      return ""

    if (inputdata.isInstanceOf[DelimitedData]) {
      val csvData = inputdata.asInstanceOf[DelimitedData]
      if (csvData.tokens == null) {
        return ""
      }
      return csvData.tokens(timeParitionKeyPos)

    } else if (inputdata.isInstanceOf[JsonData]) {
      val jsonData = inputdata.asInstanceOf[JsonData]
      val mapOriginal = jsonData.cur_json.get.asInstanceOf[Map[String, Any]]
      if (mapOriginal == null) {
        return ""
      }
      val map: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
      mapOriginal.foreach(kv => { map(kv._1.toLowerCase()) = kv._2 })

      return map.getOrElse(timeParitionKey, "").toString
    } else if (inputdata.isInstanceOf[KvData]) {
      val kvData = inputdata.asInstanceOf[KvData]
      if (kvData == null) {
        return ""
      }
      val map: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()
      kvData.dataMap.foreach(kv => { map(kv._1.toLowerCase()) = kv._2 })

      return map.getOrElse(timeParitionKey, "").toString

    } else if (inputdata.isInstanceOf[XmlData]) {
      val xmlData = inputdata.asInstanceOf[XmlData]
      // Fix this
    } else throw new Exception("Invalid input data")
    return ""
  }

  def TimePartitionData(inputdata: InputData): Long = {
    val tmPartInfo = getTimePartitionInfo
    if (tmPartInfo == null) return 0;

    // Get column data and pass it
    ComputeTimePartitionData(com.ligadata.BaseTypes.StringImpl.Input(getTimePartitionKeyData(inputdata)), tmPartInfo._2, tmPartInfo._3)
  }
     """

  }

  def addMessage(addMsg: String, msg: Message): String = {
    var addMessageFunc: String = ""
    var msgType: String = ""

    try {

      if ((addMsg != null) && (addMsg.trim() != "")) {
        addMessageFunc = """
    override def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit = {
	    if (childPath == null || childPath.size == 0) { // Invalid case
		  return
	    }
	   val curVal = childPath(0)
	   if (childPath.size == 1) { // If this is last level
	 """ + addMsg + """
	  	} else { // Yet to handle it. Make sure we add the message to one of the value given
	  		throw new Exception("Not yet handled messages more than one level")
	 	}
	}
	 
     """
      } else {

        addMessageFunc = """
    override def AddMessage(childPath: Array[(String, String)], msg: BaseMsg): Unit = { }
     
     """
      }

    } catch {
      case e: Exception => {
        val stackTrace = StackTrace.ThrowableTraceString(e)
        LOG.debug("StackTrace:" + stackTrace)
        throw e
      }
    }
    addMessageFunc
  }

  def getMessage(getMsg: String): String = {
    var getMessageFunc: String = ""

    if (getMsg != null && getMsg.trim() != "") {

      getMessageFunc = """
    override def GetMessage(childPath: Array[(String, String)], primaryKey:Array[String]): com.ligadata.KamanjaBase.BaseMsg = {
	    if (childPath == null || childPath.size == 0 || primaryKey == null || primaryKey.size == 0) { // Invalid case
    		return null
	    }
	   val curVal = childPath(0)
       if (childPath.size == 1) { // If this is last level
	 """ + getMsg + """
	  	} else { // Yet to handle it. Make sure we add the message to one of the value given
	  		throw new Exception("Not yet handled messages more than one level")
	 	}
	 	return null
	}
	 
     """
    } else {

      getMessageFunc = """
    override def GetMessage(childPath: Array[(String, String)], primaryKey:Array[String]): com.ligadata.KamanjaBase.BaseMsg = {
       return null
    } 
     
     """
    }
    getMessageFunc
  }

  def createObj(msg: Message, partitionPos: Array[Int], primaryPos: Array[Int], clsname: String, timePartitionKey: String, timePartitionPos: String): (StringBuilder) = {
    var cobj: StringBuilder = new StringBuilder
    var tdataexists: String = ""
    var tattribs: String = ""
    val newline = "\n"
    val cbrace = "}"
    val isFixed = getIsFixed(msg)
    val canPersist = getCanPersist(msg)
    var primaryKeyDef: String = ""
    var partitionKeyDef: String = ""

    val pratitionKeys = partitionkeyStrObj(msg, partitionPos)
    val primaryKeys = primarykeyStrObj(msg, primaryPos)

    /*if (pratitionKeys != null && pratitionKeys.trim() != "") {
      partitionKeyDef = getPartitionKeyDef
    }
    if (primaryKeys != null && primaryKeys.trim() != "") {
      primaryKeyDef = getPrimaryKeyDef
    }
*/

    if (msg.msgtype.equals("Message")) {
      if (msg.TDataExists) {
        tdataexists = gettdataexists + msg.TDataExists.toString
        tattribs = tdataattribs(msg)
      } else {
        tdataexists = gettdataexists + msg.TDataExists.toString
        tattribs = notdataattribs
      }
      // cobj.append(tattribs + newline + tdataexists + newline + getMessageName(msg) + newline + getName(msg) + newline + getVersion(msg) + newline + createNewMessage(msg) + newline + isFixed + cbrace + newline)

      //cobj.append(tattribs + newline + tdataexists + newline + getName(msg) + newline + getVersion(msg) + newline + createNewMessage(msg) + newline + isFixed + pratitionKeys + primaryKeys + newline + primaryKeyDef + partitionKeyDef + cbrace + newline)
      cobj.append(tattribs + newline + tdataexists + newline + getName(msg) + newline + getVersion(msg) + newline + createNewMessage(msg, clsname) + newline + isFixed + canPersist + rddHandler.HandleRDD(msg.Name) + newline + pratitionKeys + primaryKeys + newline + getTimePartitioninfo(msg.timePartition) + timePartitionKeyDataObj(timePartitionKey, timePartitionPos) + newline + hasObjPrimaryPartitionTimePartitionKeys + newline + getFullName + newline + cbrace + newline)

    } else if (msg.msgtype.equals("Container")) {
      // cobj.append(getMessageName(msg) + newline + getName(msg) + newline + getVersion(msg) + newline + createNewContainer(msg) + newline + isFixed + cbrace + newline)

      //  cobj.append(getName(msg) + newline + getVersion(msg) + newline + createNewContainer(msg) + newline + isFixed + pratitionKeys + primaryKeys + newline + primaryKeyDef + partitionKeyDef + cbrace + newline)
      cobj.append(getName(msg) + newline + getVersion(msg) + newline + createNewContainer(msg, clsname) + newline + isFixed + canPersist + rddHandler.HandleRDD(msg.Name) + newline + pratitionKeys + primaryKeys + getTimePartitioninfo(msg.timePartition) + newline + timePartitionKeyDataObj(timePartitionKey, timePartitionPos) + newline + getFullName + newline + hasObjPrimaryPartitionTimePartitionKeys + newline + cbrace + newline)

    }
    cobj
  }

  //DatePartitionInfo method in message Object

  private def getTimePartitioninfo(timePatition: TimePartition): String = {
    var timeParitionInfoStr = new StringBuilder(8 * 1024)

    if (timePatition == null) {
      timeParitionInfoStr = timeParitionInfoStr.append("""     
    def getTimePartitionInfo: (String, String, String) = { // Column, Format & Types	
		return(null, null, null)	
	}
    override def TimePartitionData(inputdata: InputData): Long = 0
      
      """)

    } else {

      timeParitionInfoStr = timeParitionInfoStr.append("""     
    def getTimePartitionInfo: (String, String, String) = { // Column, Format & Types	
		return("""" + timePatition.Key + """" , """" + timePatition.Format + """", """" + timePatition.DType + """")		
	}""")

    }

    return timeParitionInfoStr.toString
  }

  def getName(msg: Message) = {
    "\toverride def FullName: String = " + "\"" + msg.NameSpace + "." + msg.Name + "\"" + "\n" +
      "\toverride def NameSpace: String = " + "\"" + msg.NameSpace + "\"" + "\n" +
      "\toverride def Name: String = " + "\"" + msg.Name + "\""
  }

  def getMessageName(msg: Message) = {
    if (msg.msgtype.equals("Message"))
      "\toverride def getMessageName: String = " + "\"" + msg.NameSpace + "." + msg.Name + "\""
    else if (msg.msgtype.equals("Container"))
      "\toverride def getContainerName: String = " + "\"" + msg.NameSpace + "." + msg.Name + "\""

  }

  def getVersion(msg: Message) = {
    "\toverride def Version: String = " + "\"" + msg.Version + "\""

  }
  def createNewMessage(msg: Message, classname: String) = {
    "\toverride def CreateNewMessage: BaseMsg  = new " + classname + "()"
  }

  def createNewContainer(msg: Message, classname: String) = {
    "\toverride def CreateNewContainer: BaseContainer  = new " + classname + "()"
  }

  def gettdataexists = {
    "\toverride def NeedToTransformData: Boolean = "
  }
  def notdataattribs = {
    "\toverride def TransformDataAttributes: TransformMessage = null"
  }

  def tdataattribs(msg: Message) = {
    """
	def TransformDataAttributes: TransformMessage = {
	    val rearr = new TransformMessage()
	    rearr.messageType = """ + "\"" + msg.Name + "\"" +
      """
	    rearr.inputFields = """ + gettdataattribs(msg.TrfrmData.input) +
      """
	    rearr.outputFields = """ + gettdataattribs(msg.TrfrmData.output) +
      """
	    rearr.outputKeys = """ + gettdataattribs(msg.TrfrmData.keys) +
      """
	    rearr
	}
    """
  }

  def gettdataattribs(a: Array[String]): String = {
    var str: String = "Array ("
    var i: Int = 0
    for (s <- a) {
      if (i == a.size - 1) {
        str = str + "\"" + s + "\" "
      } else
        str = str + "\"" + s + "\", "
      i = i + 1
    }
    str + ")"
  }

  def getBaseTrait(message: Message): (String, String, String) = {
    var btrait: String = ""
    var strait: String = ""
    var csetters: String = ""
    if (message.msgtype.equals("Message")) {
      btrait = "BaseMsgObj"
      strait = "BaseMsg"
      csetters = ""
    } else if (message.msgtype.equals("Container")) {
      btrait = "BaseContainerObj"
      strait = "BaseContainer"
      csetters = cSetter
    }
    (btrait, strait, csetters)
  }

  def cSetter() = ""

  def getIsFixed(message: Message): String = {
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val newline = "\n"
    val isf: String = "false"
    val isKV: String = "true"
    var isfixed = new StringBuilder(8 * 1024)
    if (message.Fixed.equals("true"))
      isfixed = isfixed.append("%soverride def IsFixed:Boolean = %s;%s%soverride def IsKv:Boolean = %s;%s".format(pad1, message.Fixed, newline, pad1, isf, newline))
    else
      isfixed = isfixed.append("%soverride def IsFixed:Boolean = %s;%s%soverride def IsKv:Boolean = %s;%s".format(pad1, isf, newline, pad1, isKV, newline))

    isfixed.toString
  }

  def getIsFixedCls(message: Message): String = {
    val pad1 = "\t"
    val newline = "\n"
    var isfixed = new StringBuilder(8 * 1024)
    isfixed = isfixed.append("%soverride def IsFixed : Boolean = %s.IsFixed;%s%soverride def IsKv : Boolean = %s.IsKv;%s".format(pad1, message.Name, newline, pad1, message.Name, newline))
    isfixed.toString
  }

  def getCanPersist(message: Message): String = {
    val pad1 = "\t"
    val newline = "\n"

    var CanPersist = new StringBuilder(8 * 1024)
    if (message.Persist)
      CanPersist = CanPersist.append("%s override def CanPersist: Boolean = %s;%s".format(pad1, "true", newline))
    else
      CanPersist = CanPersist.append("%s override def CanPersist: Boolean = %s;%s".format(pad1, "false", newline))

    CanPersist.toString
  }
  def getCanPersistCls(message: Message): String = {
    val pad1 = "\t"
    val newline = "\n"
    var CanPersist = new StringBuilder(8 * 1024)
    CanPersist = CanPersist.append("%s override def CanPersist: Boolean = %s.CanPersist;%s".format(pad1, message.Name, newline))
    CanPersist.toString
  }

  def getNameVerCls(msg: Message): String = {

    "\toverride def FullName: String = " + msg.Name.trim() + ".FullName" + "\n" +
      "\toverride def NameSpace: String = " + msg.Name.trim() + ".NameSpace" + "\n" +
      "\toverride def Name: String = " + msg.Name.trim() + ".Name" + "\n" +
      "\toverride def Version: String = " + msg.Name.trim() + ".Version" + "\n"
    //  "\tdef this(from: " + msg.Name.trim() + ") = { this}" + "\n"

  }

  def importStmts(msg: Message): (String, String, String, String) = {
    var imprt: String = ""
    if (msg.msgtype.equals("Message"))
      imprt = "import com.ligadata.KamanjaBase.{BaseMsg, BaseMsgObj, TransformMessage, BaseContainer, MdBaseResolveInfo, MessageContainerBase, RDDObject, RDD, JavaRDDObject}"
    else if (msg.msgtype.equals("Container"))
      imprt = "import com.ligadata.KamanjaBase.{BaseMsg, BaseContainer, BaseContainerObj, MdBaseResolveInfo, MessageContainerBase, RDDObject, RDD, JavaRDDObject}"
    var nonVerPkg = "package " + msg.pkg + ";\n"
    var verPkg = "package " + msg.pkg + ".V" + MdMgr.ConvertVersionToLong(msg.Version).toString + ";\n"

    var otherImprts = """

  
import org.json4s.jackson.JsonMethods._
import org.json4s.DefaultFormats
import org.json4s.Formats
import scala.xml.XML
import scala.xml.Elem
import com.ligadata.KamanjaBase.{InputData, DelimitedData, JsonData, XmlData, KvData}
import com.ligadata.BaseTypes._
import com.ligadata.KamanjaBase.SerializeDeserialize
import java.io.{ DataInputStream, DataOutputStream , ByteArrayOutputStream}
import com.ligadata.Exceptions.StackTrace
import org.apache.logging.log4j.{ Logger, LogManager }
import java.util.Date
"""
    val versionPkgImport = verPkg + otherImprts + imprt
    val nonVerPkgImport = nonVerPkg + otherImprts + imprt
    (versionPkgImport, nonVerPkgImport, verPkg, nonVerPkg)
  }

  def classname(msg: Message, recompile: Boolean): (StringBuilder, StringBuilder, String) = {
    var sname: String = ""
    var oname: String = ""
    var clssb: StringBuilder = new StringBuilder()
    var objsb: StringBuilder = new StringBuilder()
    val ver = MdMgr.ConvertVersionToLong(msg.Version).toString
    val xtends: String = "extends"
    val withStr = "with"
    val rddObj = "RDDObject[" + msg.Name + "]"
    val space = " "
    val uscore = "_"
    val cls = "class"
    val obj = "object"
    if (msg.msgtype.equals("Message")) {
      oname = "BaseMsgObj {"
      sname = "BaseMsg {"
    } else if (msg.msgtype.equals("Container")) {
      oname = "BaseContainerObj {"
      sname = "BaseContainer {"
    }
    val transactionIdParam = "(var transactionId: Long, other: " + msg.Name + ")"
    //val clsname = msg.NameSpace + uscore + msg.Name + uscore + ver + uscore + msg.ClsNbr
    //val clsstr = cls + space + msg.NameSpace + uscore + msg.Name + uscore + ver + uscore + msg.ClsNbr + space + xtends + space + sname
    //val objstr = obj + space + msg.NameSpace + uscore + msg.Name + uscore + ver + uscore + msg.ClsNbr + space + xtends + space + oname
    val clsname = msg.Name
    val clsstr = cls + space + msg.Name + transactionIdParam + space + xtends + space + sname
    val objstr = obj + space + msg.Name + space + xtends + space + rddObj + space + withStr + space + oname

    (clssb.append(clsstr), objsb.append(objstr), clsname)
  }

  //trait - BaseMsg	
  def traitBaseMsg = {
    """
trait BaseMsg {
	def Fixed:Boolean;
}
	  """
  }

  private def traitBaseContainer = {
    """
trait BaseContainer {
  def Fixed: Boolean
  def populate(inputdata: InputData): Unit
  def get(key: String): Any
  def getOrElse(key: String, default: Any): Any
  def set(key: String, value: Any): Unit
}
	  """
  }

  //this method is particulraly for Mapped Messages - a variable in mapped message with key name match and types not match 
  def getMappedMsgPrevVerTypeNotMatchKeys(typeNotMatchKeys: String): String = {

    if (typeNotMatchKeys != null && typeNotMatchKeys.trim != "")
      " val prevVerTypesNotMatch = Array(" + typeNotMatchKeys.toString.substring(0, typeNotMatchKeys.toString.length - 1) + ") \n "
    else
      " val prevVerTypesNotMatch = Array(\"\") \n "
  }

  //this method is particulraly for Mapped Messages - a variable in mapped message with key name and types also match 

  def getMappedMsgPrevVerMatchKeys(matchKeys: String): String = {

    if (matchKeys != null && matchKeys.trim != "")
      " val prevVerMatchKeys = Array(" + matchKeys.toString.substring(0, matchKeys.toString.length - 1) + ") \n "
    else
      " val prevVerMatchKeys = Array(\"\") \n "
  }

  def getKeysStr(keysStr: String) = {
    if (keysStr != null && keysStr.trim() != "") {

      """ 
      var keys = Map(""" + keysStr.toString.substring(0, keysStr.toString.length - 1) + ") \n " +
        """
      var fields: scala.collection.mutable.Map[String, (Int, Any)] = new scala.collection.mutable.HashMap[String, (Int, Any)];
 	"""
    } else {
      """ 
	    var keys = Map[String, Int]()
	    var fields: scala.collection.mutable.Map[String, (Int, Any)] = new scala.collection.mutable.HashMap[String, (Int, Any)];
	  
	  """
    }
  }

  def getTransactionIdMapped = {
    """
        fields("transactionId") =( (typsStr.indexOf("com.ligadata.BaseTypes.LongImpl")), transactionId)
	  	fields("timePartitionData") =( -1, timePartitionData)
    
    """

  }

  //Messages and containers in Set for mapped messages to poplulate the data

  def getMsgAndCntnrs(msgsAndCntnrs: String): String = {
    var str = ""
    if (msgsAndCntnrs != null && msgsAndCntnrs.toString.trim != "")
      str = "val messagesAndContainers = Set(" + msgsAndCntnrs.toString.substring(0, msgsAndCntnrs.toString.length - 1) + ") \n "
    else
      str = "val messagesAndContainers = Set(\"\") \n "

    str
  }

  //Messages and containers in Set for mapped messages to poplulate the data

  def getCollectionsMapped(collections: String): String = {
    var str = ""
    if (collections != null && collections.toString.trim != "")
      str = "val collectionTypes = Set(" + collections.toString.substring(0, collections.toString.length - 1) + ") \n "
    else
      str = "val collectionTypes = Set(\"\") \n "

    str
  }

  def inputData = {
    """ 
trait InputData {
	var dataInput: String
}

class DelimitedData(var dataInput: String, var dataDelim: String) extends InputData(){ }
class JsonData(var dataInput: String) extends InputData(){ }
class XmlData(var dataInput: String) extends InputData(){ }
	  """
  }

  def getsetMethods(msg: Message, fixedMsgGetKeyStr: String): String = {

    val uscore = "_"
    val ver = MdMgr.ConvertVersionToLong(msg.Version).toString
    var getsetters = new StringBuilder(8 * 1024)
    //val clsname = msg.NameSpace + uscore + msg.Name + uscore + ver + uscore + msg.ClsNbr
    val clsname = msg.Name
    if (msg.Fixed.toLowerCase().equals("true")) {
      getsetters = getsetters.append("""
    override def set(key: String, value: Any): Unit = { throw new Exception("set function is not yet implemented") }
   
    override def get(key: String): Any = {
    	try {
    		  // Try with reflection
    		  return getWithReflection(key)
    	} catch {
    		  case e: Exception => {
          val stackTrace = StackTrace.ThrowableTraceString(e)
          LOG.debug("StackTrace:"+stackTrace)
    		  // Call By Name
             return getByName(key)
    		  }
    	}
    }
    override def getOrElse(key: String, default: Any): Any = { throw new Exception("getOrElse function is not yet implemented") }
    
    private def getByName(key: String): Any = {
    	try {
	    """ + fixedMsgGetKeyStr +
        """
		      if (key.equals("timePartitionData")) return timePartitionData;
		      return null;
		    } catch {
		      case e: Exception => {
		        val stackTrace = StackTrace.ThrowableTraceString(e)
            LOG.debug("StackTrace:"+stackTrace)
		        throw e
		      }
		    }
		  }

		private def getWithReflection(key: String): Any = {
		  val ru = scala.reflect.runtime.universe
		  val m = ru.runtimeMirror(getClass.getClassLoader)
		  val im = m.reflect(this)
		  val fieldX = ru.typeOf[""" + clsname + """].declaration(ru.newTermName(key)).asTerm.accessed.asTerm
	  val fmX = im.reflectField(fieldX)
	  fmX.get
	}
    """)
    } else if (msg.Fixed.toLowerCase().equals("false")) {

      getsetters = getsetters.append("""
    override def set(key: String, value: Any): Unit = {
	  if (key == null) throw new Exception(" key should not be null in set method")
	  fields.put(key, (-1, value))
    }
    override def get(key: String): Any = {
      fields.get(key) match {
    	 case Some(f) => {
    		  //println("Key : "+ key + " Idx " + f._1 +" Value" + f._2 )
    		  return f._2
    	}
    	case None => {
    		  //println("Key - value null : "+ key )
    		  return null
    	  }
    	}
     // fields.get(key).get._2
    }

    override def getOrElse(key: String, default: Any): Any = {
      fields.getOrElse(key, (-1, default))._2
    }
    """)

    }
    getsetters.toString
  }

  def getMappedBaseTypesArray(arrayBuf: ArrayBuffer[String]): String = {
    var string = arrayBuf.toString
    var mappedBaseTypes = new StringBuilder(8 * 1024)
    var mappedBaseTypes1 = new StringBuilder(8 * 1024)

    if (arrayBuf.size > 0) {
      arrayBuf.foreach(t => {
        mappedBaseTypes = mappedBaseTypes.append(t + ",")
        mappedBaseTypes1 = mappedBaseTypes1.append("\"" + t + "\",")
      })

      return "val typs = Array(" + mappedBaseTypes.toString.substring(0, mappedBaseTypes.toString.length() - 1) + "); \n\t val typsStr = Array(" + mappedBaseTypes1.toString.substring(0, mappedBaseTypes1.toString.length() - 1) + "); "

    } else {
      return ""
    }
  }

  def partitionkeyStr(paritionKeys: String): String = {
    if (paritionKeys != null && paritionKeys.trim() != "")
      "\n	override def PartitionKeyData: Array[String] = " + paritionKeys
    else
      "\n	override def PartitionKeyData: Array[String] = Array[String]()"
  }

  def primarykeyStr(primaryKeys: String): String = {
    if (primaryKeys != null && primaryKeys.trim() != "")
      "\n	override def PrimaryKeyData: Array[String] = " + primaryKeys
    else
      "\n	override def PrimaryKeyData: Array[String] = Array[String]()"
  }

  def partitionkeyStrMapped(message: Message, partitionPos: Array[Int]): String = {
    val pad1 = "\t"
    var partitionStr = new StringBuilder
    partitionStr.append("Array(")
    for (p <- partitionPos) {
      partitionStr.append(p + ",")
    }
    var partitionString = partitionStr.substring(0, partitionStr.length - 1)
    partitionString = partitionString + ")"

    val partitionKeys = if (message.PartitionKey != null && message.PartitionKey.size > 0) ("Array(\"" + message.PartitionKey.map(p => p.toLowerCase).mkString("\", \"") + "\")") else ""

    if (partitionKeys != null && partitionKeys.trim() != "")
      "\n	val partitionKeys : Array[String] = " + partitionKeys + "\n    val partKeyPos = " + partitionString.toString + "\n " + pad1 + "override def PartitionKeyData: Array[String] =  partitionKeys.map(p => { fields.getOrElse(p, \"\").toString})  "
    else
      "\n   override def PartitionKeyData(inputdata:InputData): Array[String] = Array[String]()"

  }

  def primarykeyStrMapped(message: Message, primaryPos: Array[Int]): String = {
    val prmryKeys = if (message.PrimaryKeys != null && message.PrimaryKeys.size > 0) ("Array(\"" + message.PrimaryKeys.map(p => p.toLowerCase).mkString("\", \"") + "\")") else ""
    val pad1 = "\t"
    var primaryStr = new StringBuilder
    LOG.debug("primaryPos " + primaryPos.length)
    primaryStr.append("Array(")
    for (p <- primaryPos) {
      primaryStr.append(p + ",")
    }
    var primaryString = primaryStr.substring(0, primaryStr.length - 1)
    primaryString = primaryString + ")"

    if (prmryKeys != null && prmryKeys.trim() != "")
      "\n	val primaryKeys : Array[String] = " + prmryKeys + "\n    val prmryKeyPos = " + primaryString + "\n" + pad1 + "\n	override def PrimaryKeyData: Array[String] =  primaryKeys.map(p => { fields.getOrElse(p, \"\").toString})  "
    else
      "\n    override def PrimaryKeyData(inputdata:InputData): Array[String] = Array[String]()"

  }

  private def timePartitionKeyDataObj(timePartitionFld: String, partitionPos: String): String = {
    val pad1 = "\t"
    var partitionStr = new StringBuilder
    if (timePartitionFld != null && timePartitionFld.trim() != "" && partitionPos != null && partitionPos.trim() != "")
      "\n	val timeParitionKeyPos : Int = " + partitionPos + "; \n   val timeParitionKey : String = \"" + timePartitionFld + "\"; \n " + getTimePartitionInfo + "\n"
    else
      ""

  }

  //input function conversion
  def getDefVal(valType: String): String = {
    valType match {
      case "int" => "0"
      case "float" => "0"
      case "boolean" => "false"
      case "double" => "0.0"
      case "long" => "0"
      case "char" => "\' \'"
      case "string" => "\"\""
      case _ => ""
    }
  }

  def getMemberType(valType: String): String = {
    valType match {
      case "system.int" => "Int"
      case "system.float" => "Float"
      case "system.boolean" => "Boolean"
      case "system.double" => "Double"
      case "system.long" => "Long"
      case "system.char" => "OptChar"
      case "system.string" => "String"
      case _ => ""
    }
  }

  def saveObject(msg: Message): String = {

    """
    override def Save: Unit = {
		 """ + msg.Name + """.saveOne(this)
	}
 	 """
  }

  def fromFuncOfMappedMsg(msg: Message): String = {
    """ 
     private def fromFunc(other: """ + msg.Name + """): """ + msg.Name + """ = {
		 other.fields.foreach(field => {
		 if(field._2._1 >= 0)       
		  	fields(field._1) = (field._2._1, field._2._2);
	    })
     	return this
    }
    """
  }

  def fromFuncOfFixedMsgs(msg: Message, fromFunc: String, fromFuncBaeTypes: String): String = {
    var fromFnc: String = ""
    if (fromFunc != null) fromFnc = fromFunc
    """ 
     private def fromFunc(other: """ + msg.Name + """): """ + msg.Name + """ = {
     """ + fromFuncBaeTypes + """
	""" + fromFnc + """
	timePartitionData = com.ligadata.BaseTypes.LongImpl.Clone(other.timePartitionData);
    return this
    }
    """

  }

  def fromFuncOfMappedMsgs(msg: Message, fromFunc: String, fromFuncBaseTypesStr: String): String = {
    var fromFnc: String = ""
    if (fromFunc != null) fromFnc = fromFunc
    """ 
     private def fromFunc(other: """ + msg.Name + """): """ + msg.Name + """ = {
     
     other.fields.foreach(ofield => {
     
        if (ofield._2._1 >= 0) {
          val key = ofield._1.toLowerCase
          if(key != "transactionid"){
          ofield._2._1 match {
           """ + fromFuncBaseTypesStr +
      """
            case _ => {} // could be -1
          }
        }
       }
      })
     
	""" + fromFnc + """
	timePartitionData = com.ligadata.BaseTypes.LongImpl.Clone(other.fields("timePartitionData")._2.asInstanceOf[Long])
    fields("timePartitionData") = (-1, timePartitionData)

     	return this
    }
    """

  }

  def getFullName: String = {
    """
    override def getFullName = FullName    
    """
  }

  def computeTimePartitionDate(message: Message): String = {

    var timePartitionKeyData: String = ""
    var keyDataCheckStr: String = ""
    var computeTmPartition: String = ""
    if (message.timePartition != null) {
      if (message.Fixed.equalsIgnoreCase("true")) {
        timePartitionKeyData = message.timePartition.Key + ".toString"
        keyDataCheckStr = "if(" + message.timePartition.Key + " == null) return 0;"
      } else if (message.Fixed.equalsIgnoreCase("false")) {
        timePartitionKeyData = "fields(\"" + message.timePartition.Key + "\")._2.toString"
        keyDataCheckStr = "if(fields(\"" + message.timePartition.Key + "\")._2 " + "== null) return 0;"

      }

      computeTmPartition = """
      def ComputeTimePartitionData: Long = {
		val tmPartInfo = """ + message.Name + """.getTimePartitionInfo
		if (tmPartInfo == null) return 0;
		""" + keyDataCheckStr + """
		""" + message.Name + """.ComputeTimePartitionData(""" + timePartitionKeyData + """, tmPartInfo._2, tmPartInfo._3)
	 } 
  
  """
    } else {
      timePartitionKeyData = "\" \""
      computeTmPartition = """
      def ComputeTimePartitionData: Long = {
		val tmPartInfo = """ + message.Name + """.getTimePartitionInfo
		if (tmPartInfo == null) return 0;
		""" + message.Name + """.ComputeTimePartitionData(""" + timePartitionKeyData + """, tmPartInfo._2, tmPartInfo._3)
	 } 
  """
    }
    computeTmPartition
  }
  
  def getTimePartition(msg: Message) : String = {
    """
    def getTimePartition() : """+msg.Name +""" = {
		timePartitionData = ComputeTimePartitionData
		 return this;
	}
    
    """    
  }

  def transactionIdFuncs(message: Message): String = {

    """
     
  if (other != null && other != this) {
    // call copying fields from other to local variables
    fromFunc(other)
  }

  def this(txnId: Long) = {
    this(txnId, null)
  }
  def this(other: """ + message.Name + """) = {
    this(0, other)
  }
  def this() = {
    this(0, null)
  }
  override def Clone(): MessageContainerBase = {
    """ + message.Name + """.build(this)
  }
  
  """
  }

  def logStackTrace = {
    """
    private val LOG = LogManager.getLogger(getClass)
    """

  }

  def hasObjPrimaryPartitionTimePartitionKeys(): String = {

    """
    override def hasPrimaryKey(): Boolean = {
		 if(primaryKeys == null) return false;
		 (primaryKeys.size > 0);
    }

    override def hasPartitionKey(): Boolean = {
		 if(partitionKeys == null) return false;
    	(partitionKeys.size > 0);
    }

    override def hasTimeParitionInfo(): Boolean = {
    	val tmPartInfo = getTimePartitionInfo
    	(tmPartInfo != null && tmPartInfo._1 != null && tmPartInfo._2 != null && tmPartInfo._3 != null);
    }
     
    """

  }

  def hasClsPrimaryPartitionTimePartitionKeys(message: Message): String = {

    """
    override def hasPrimaryKey(): Boolean = {
    	""" + message.Name + """.hasPrimaryKey;
    }

  override def hasPartitionKey(): Boolean = {
    """ + message.Name + """.hasPartitionKey;
  }

  override def hasTimeParitionInfo(): Boolean = {
    """ + message.Name + """.hasTimeParitionInfo;
  }
 """

  }

}