package com.ligadata.messagedef

import scala.util.parsing.json.JSON
import scala.reflect.runtime.universe
import scala.io.Source
import java.io.File
import java.io.PrintWriter
import java.util.Date
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.ArrayBuffer

import com.ligadata.olep.metadata.MdMgr
import com.ligadata.olep.metadata.EntityType
import com.ligadata.olep.metadata.MessageDef
import com.ligadata.olep.metadata.ContainerDef

trait Attrib {
  var NameSpace: String
  var Name: String
  var Type: String
}

class Message(var msgtype: String, var NameSpace: String, var Name: String, var PhysicalName: String, var Version: String, var Description: String, var Fixed: String, var Elements: List[Element], var TDataExists: Boolean, var TrfrmData: TransformData, var jarset: Set[String], var pkg: String)
class TransformData(var input: Array[String], var output: Array[String], var keys: Array[String])
class Field(var NameSpace: String, var Name: String, var Fieldtype: String)
class Concept(var NameSpace: String, var Name: String, var Type: String)
class Concepts(var Concepts: List[Concept])
class Element(var NameSpace: String, var Name: String, var Ttype: String, var ElemType: String)
case class MessageException(message: String) extends Exception(message)

// The class caster that can throw exceptions... 
class ClassCaster[T] {
  def unapply(a: Any): Option[T] = Some(a.asInstanceOf[T])
}

// concrete instances of the casters for pattern matching
object AsMap extends ClassCaster[Map[String, Any]]
object AsList extends ClassCaster[List[Any]]
object AsString extends ClassCaster[String]

class MessageDefImpl {

  def error[T](prefix: String): Option[T] =
    throw MessageException("%s must be specified".format(prefix))

  //creates the class string
  def createClassStr(message: Message, mdMgr: MdMgr): (String, String, String, List[(String, String)], List[(String, String, String, String, Boolean)]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    val ver = message.Version.replaceAll("[.]", "").toInt.toString
    val newline = "\n"
    val (classstr, csvassignstr, jsonstr, xmlStr, count, list, argsList) = classStr(message, mdMgr)
    try {
      val (btrait, striat, csetters) = getBaseTrait(message)
      val cobj = createObj(message)
      val isFixed = getIsFixed(message)
      val (clsstr, objstr) = classname(message)
      scalaclass = scalaclass.append(importStmts(message.msgtype) + newline + newline + objstr + newline + cobj.toString + newline + clsstr.toString + newline)
      scalaclass = scalaclass.append(classstr + csetters + populate + populatecsv.toString + csvAssign(csvassignstr, count) + populateJson + assignJsonData(jsonstr) + populateXml + assignXmlData(xmlStr) + " \n}")
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    (message.Name, ver.toString, scalaclass.toString, list, argsList)
  }

  def createObj(msg: Message): (StringBuilder) = {
    var cobj: StringBuilder = new StringBuilder
    var tdataexists: String = ""
    var tattribs: String = ""
    val newline = "\n"
    val cbrace = "}"
    val isFixed = getIsFixed(msg)

    if (msg.msgtype.equals("Message")) {
      if (msg.TDataExists) {
        tdataexists = gettdataexists + msg.TDataExists.toString
        tattribs = tdataattribs(msg)
      } else {
        tdataexists = gettdataexists + msg.TDataExists.toString
        tattribs = notdataattribs
      }
      cobj.append(tattribs + newline + tdataexists + newline + getMessageName(msg) + newline + getVersion(msg) + newline + createNewMessage(msg) + newline + isFixed + cbrace + newline)

    } else if (msg.msgtype.equals("Container")) {
      cobj.append(getMessageName(msg) + newline + getVersion(msg) + newline + createNewContainer(msg) + newline + isFixed + cbrace + newline)

    }
    cobj
  }
  def getMessageName(msg: Message) = {
    if (msg.msgtype.equals("Message"))
      "\tdef getMessageName: String = " + "\"" + msg.NameSpace + "." + msg.Name + "\""
    else if (msg.msgtype.equals("Container"))
      "\tdef getContainerName: String = " + "\"" + msg.NameSpace + "." + msg.Name + "\""

  }

  def getVersion(msg: Message) = {
    "\tdef getVersion: String = " + "\"" + msg.Version + "\""

  }
  def createNewMessage(msg: Message) = {
    "\tdef CreateNewMessage: BaseMsg  = new " + msg.NameSpace + "_" + msg.Name + "_" + msg.Version.replaceAll("[.]", "").toInt + "()"
  }

  def createNewContainer(msg: Message) = {
    "\tdef CreateNewContainer: BaseContainer  = new " + msg.NameSpace + "_" + msg.Name + "_" + msg.Version.replaceAll("[.]", "").toInt + "()"
  }

  def gettdataexists = {
    "\tdef NeedToTransformData: Boolean = "
  }
  def notdataattribs = {
    "\tdef TransformDataAttributes: TransformMessage = null"
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

  //generates the variables string and assign string
  def classStr(message: Message, mdMgr: MdMgr): (String, String, String, String, Int, List[(String, String)], List[(String, String, String, String, Boolean)]) = {
    var scalaclass = new StringBuilder(8 * 1024)
    var assignCsvdata = new StringBuilder(8 * 1024)
    var assignJsondata = new StringBuilder(8 * 1024)
    var assignXmldata = new StringBuilder(8 * 1024)
    var list = List[(String, String)]()
    var argsList = List[(String, String, String, String, Boolean)]()
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val newline = "\n"
    var jarset: Set[String] = Set();

    var count: Int = 0
    scalaclass = scalaclass.append(getIsFixed(message) + newline + getMessageName(message) + newline + getVersion(message) + newline)
    for (f <- message.Elements) {
      // val typ = MdMgr.GetMdMgr.Type(key, ver, onlyActive)(f.Ttype)
      //val attr = MdMgr.GetMdMgr.Attribute(message.NameSpace, message.Name)

      val typ = MdMgr.GetMdMgr.Type(f.Ttype, message.Version.replaceAll("[.]", "").toInt, true) // message.Version.toInt
      if (typ.isEmpty)
        throw new Exception("Type %s not found in metadata for namespace %s" + f.Ttype)

      // if (!typ.get.physicalName.equals("String")){
      //  argsList = (f.NameSpace, f.Name, f.NameSpace, typ.get.physicalName.substring(6, typ.get.physicalName.length()), false) :: argsList
      //}else
      if (typ.get.physicalName.isEmpty())
        throw new Exception("Physical Name not found in metadata for namespace %s" + f.Ttype)

      argsList = (f.NameSpace, f.Name, f.NameSpace, typ.get.physicalName, false) :: argsList

      if (typ.get.implementationName.isEmpty())
        throw new Exception("Implementation Name not found in metadata for namespace %s" + f.Ttype)

      val fname: String = typ.get.implementationName + ".Input"

      if ((typ.get.dependencyJarNames != null) && (typ.get.JarName != null))
        jarset = jarset + typ.get.JarName ++ typ.get.dependencyJarNames
      else if (typ.get.JarName != null)
        jarset = jarset + typ.get.JarName

      val dval: String = getDefVal(f.Ttype)
      list = (f.Name, f.Ttype) :: list

      scalaclass = scalaclass.append("%svar %s:%s = _ ;%s".format(pad1, f.Name, typ.get.physicalName, newline))
      assignCsvdata.append("%s%s = %s(list(idx));\n%sidx = idx+1\n".format(pad2, f.Name, fname, pad2))
      assignJsondata.append("%s %s = %s(map.getOrElse(\"%s\", %s).toString)%s".format(pad1, f.Name, fname, f.Name, dval, newline))
      assignXmldata.append("%sval _%sval_  = (xml \\\\ \"%s\").text.toString %s%sif (_%sval_  != \"\")%s%s =  %s( _%sval_ ) else %s = %s%s".format(pad3, f.Name, f.Name, newline, pad3, f.Name, pad2, f.Name, fname, f.Name, f.Name, dval, newline))
      count = count + 1
    }
    if (jarset != null)
      message.jarset = jarset

    (scalaclass.toString, assignCsvdata.toString, assignJsondata.toString, assignXmldata.toString, count, list, argsList)
  }

  def getIsFixed(message: Message): String = {
    val pad1 = "\t"
    val pad2 = "\t\t"
    val pad3 = "\t\t\t"
    val newline = "\n"
    val isf: String = "false"
    var isfixed = new StringBuilder(8 * 1024)
    if (message.Fixed.equals("true"))
      isfixed = isfixed.append("%sdef IsFixed:Boolean = %s;%s%sdef IsKv:Boolean = %s;%s".format(pad1, message.Fixed, newline, pad1, isf, newline))
    else
      isfixed = isfixed.append("%sdef IsFixed:Boolean = %s;%s%sdef IsKv:Boolean = %s;%s".format(pad1, isf, newline, pad1, message.Fixed, newline))

    isfixed.toString
  }

  def importStmts(msgtype: String): String = {
    var imprt: String = ""
    if (msgtype.equals("Message"))
      imprt = "import com.ligadata.OnLEPBase.{BaseMsg, BaseMsgObj, TransformMessage}"
    else if (msgtype.equals("Container"))
      imprt = "import com.ligadata.OnLEPBase.{BaseContainer, BaseContainerObj}"

    """
package com.ligadata.messagedef
    
import scala.util.parsing.json.JSON
import scala.xml.XML
import scala.xml.Elem
import com.ligadata.OnLEPBase.{InputData, DelimitedData, JsonData, XmlData}
""" + imprt

  }

  def classname(msg: Message): (StringBuilder, StringBuilder) = {
    var sname: String = ""
    var oname: String = ""
    var clssb: StringBuilder = new StringBuilder()
    var objsb: StringBuilder = new StringBuilder()
    val ver = msg.Version.replaceAll("[.]", "").toInt.toString
    val xtends: String = "extends"
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
    val clsstr = cls + space + msg.NameSpace + uscore + msg.Name + uscore + ver + space + xtends + space + sname
    val objstr = obj + space + msg.NameSpace + uscore + msg.Name + uscore + ver + space + xtends + space + oname

    (clssb.append(clsstr), objsb.append(objstr))
  }

  //trait - BaseMsg	
  def traitBaseMsg = {
    """
trait BaseMsg {
	def Fixed:Boolean;
}
	  """
  }

  def traitBaseContainer = {
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

  //input function conversion
  def getDefVal(valType: String): String = {
    valType match {
      case "System.Int" => "0"
      case "System.Float" => "0"
      case "System.Boolean" => "false"
      case "System.Double" => "0.0"
      case "System.Long" => "0"
      case "System.Char" => "\'\'"
      case "System.String" => "\"\""
      case _ => ""
    }
  }

  def getMemberType(valType: String): String = {
    valType match {
      case "System.Int" => "Int"
      case "System.Float" => "Float"
      case "System.Boolean" => "Boolean"
      case "System.Double" => "Double"
      case "System.Long" => "Long"
      case "System.Char" => "OptChar"
      case "System.String" => "String"
      case _ => ""
    }
  }

  def cSetter() = {
    """
    def get(key: String): Any ={
		null
	}
	def getOrElse(key: String, default: Any): Any ={
	    null
	}
	def set(key: String, value: Any): Unit = {
	    null
	}
    """
  }
  //populate method in msg-TransactionMsg class
  def populate = {
    """
  def populate(inputdata:InputData){
	if (inputdata.isInstanceOf[DelimitedData])	
		populateCSV(inputdata.asInstanceOf[DelimitedData])
	else if (inputdata.isInstanceOf[JsonData])
			populateJson(inputdata.asInstanceOf[JsonData])
	else if (inputdata.isInstanceOf[XmlData])
			populateXml(inputdata.asInstanceOf[XmlData])
	else throw new Exception("Invalid input data")
  }
		"""
  }

  //populateCSV fucntion in meg class
  def populatecsv = {
    """
  def populateCSV(inputdata:DelimitedData) = { 
	val delimiter = inputdata.dataDelim
	val dataStr = inputdata.dataInput
	val list = inputdata.dataInput.split(delimiter)
	val idx:Int = assignCsv(list,0)
  }
	 """
  }

  ////csvAssign fucntion in meg class
  def csvAssign(assignCsvdata: String, count: Int): String = {
    """
  def assignCsv(list:Array[String], startIdx:Int) = {
	var idx = startIdx
	try{
""" + "\t\tif(list.size < " + count + ") throw new Exception(\"Incorrect input data size\")" + """
  """ + assignCsvdata +
      """
 	}catch{
		case e:Exception =>{
			e.printStackTrace()
  			throw e
		}
	}
  	idx
  }
	  """
  }

  def populateJson = {
    """
  def populateJson(json:JsonData) : Unit = {
	try{
    	if(json == null) throw new Exception("Invalid json data")
     	val parsed:Option[Any] = JSON.parseFull(json.dataInput) 
     	assignJsonData(parsed.get.asInstanceOf[Map[String, Any]])
	}catch{
	  case e:Exception =>{
   	    e.printStackTrace()
   	  	throw e	    	
	  }
	}
  }
	  """
  }

  def assignJsonData(assignJsonData: String) = {
    """
  def assignJsonData(map:Map[String,Any]) : Unit =  {
    try{
	  if(map == null)  throw new Exception("Invalid json data")
""" + assignJsonData +
      """
	}catch{
  		case e:Exception =>{
   			e.printStackTrace()
   			throw e	    	
	  	}
	}
  }
	"""
  }

  def populateXml = {
    """
  def populateXml(xmlData:XmlData) : Unit = {	  
	try{
    	val xml = XML.loadString(xmlData.dataInput)
    	assignXml(xml)
	} catch{
		case e:Exception =>{
   			e.printStackTrace()
   	  		throw e	    	
    	}
	}
  }
	  """
  }

  def assignXmlData(xmlData: String) = {
    """
  def assignXml(xml:Elem) : Unit = {
	try{
	  if(xml == null) throw new Exception("Invalid xml data")
""" + xmlData +
      """
	}catch{
	  case e:Exception =>{
	    e.printStackTrace()
		throw e	    	
	  }
   	}
  }
"""
  }

  //creates the message class file
  def createScalaFile(scalaClass: String, version: String, className: String): Unit = {
    try {
      val writer = new PrintWriter(new File("/tmp/" + className + "_" + version + ".scala"))
      writer.write(scalaClass.toString)
      writer.close()
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
  }

  def processMsgDef(json: String, msgDfType: String, mdMgr: MdMgr): (String, ContainerDef) = {
    var classname: String = null
    var ver: String = null
    var classstr_1: String = null
    var containerDef: ContainerDef = null
    try {
      if (mdMgr == null)
        throw new Exception("MdMgr is not found")
      if (msgDfType.equals("JSON")) {
        var message: Message = null
        message = processJson(json, mdMgr).asInstanceOf[Message]
        val (classname, ver, classstr, list, argsList) = createClassStr(message, mdMgr)
        classstr_1 = classstr
        createScalaFile(classstr, ver, classname)
        val cname = message.pkg + "." + message.NameSpace + "_" + message.Name.toString() + "_" + message.Version.replaceAll("[.]", "").toInt.toString

        if (message.msgtype.equals("Message"))
          containerDef = createMsgDef(message, list, mdMgr, argsList)
        else if (message.msgtype.equals("Container"))
          containerDef = createContainerDef(message, list, mdMgr, argsList)

      } else throw new Exception("MsgDef Type JSON is only supported")
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    (classstr_1, containerDef)
  }

  def createContainerDef(msg: Message, list: List[(String, String)], mdMgr: MdMgr, argsList: List[(String, String, String, String, Boolean)]): ContainerDef = {
    var containerDef: ContainerDef = new ContainerDef()
    
    containerDef = mdMgr.MakeFixedContainer(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, msg.Version.replaceAll("[.]", "").toInt, null, msg.jarset.toArray)
    containerDef
  }

  def createMsgDef(msg: Message, list: List[(String, String)], mdMgr: MdMgr, argsList: List[(String, String, String, String, Boolean)]): MessageDef = {
    var msgDef: MessageDef = new MessageDef()
    msgDef = mdMgr.MakeFixedMsg(msg.NameSpace, msg.Name, msg.PhysicalName, argsList, msg.Version.replaceAll("[.]", "").toInt, null, msg.jarset.toArray)
    msgDef
  }

  def processConcept(conceptJson: String, mdMgr: MdMgr): Unit = {
    val list: List[(String, String, String)] = listConceptArgs(parseConceptJson(conceptJson))

  }

  def listConceptArgs(cpts: Concepts): List[(String, String, String)] = {
    var list = List[(String, String, String)]()
    for (c <- cpts.Concepts) {
      list ::= (c.NameSpace, c.Name, c.Type)
    }
    list
  }

  def parseConceptJson(conceptJson: String): Concepts = {
    try {
      val pConcpt = JSON.parseFull(conceptJson)
      if (pConcpt.isEmpty) throw MessageException("Unable to parse JSON.")

      val cptsDef = for {
        Some(AsMap(map)) <- List(pConcpt)
        AsList(concepts) <- map get "Concepts"
      } yield new Concepts(for {
        AsMap(concept) <- concepts
        AsString(name) <- concept get "Name" orElse error("Structure Name in Field does not exist in json")
        AsString(ctype) <- concept get "Type" orElse error("Structure Type in Field does not exist in json")
        AsString(namespace) <- concept get "NameSpace" orElse error("Structure Type in Field does not exist in json")

      } yield new Concept(namespace, name, ctype))
      cptsDef.head
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
  }

  def processJson(json: String, mdMgr: MdMgr): Message = {
    var message: Message = null
    var jtype: String = null
    val parsed = JSON.parseFull(json)

    val map = parsed.get.asInstanceOf[Map[String, Any]]
    var key: String = ""
    try {
      jtype = geJsonType(map)
      message = processJsonMap(jtype, map).asInstanceOf[Message]
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    message
  }

  def geJsonType(map: Map[String, Any]): String = {
    var jtype: String = null
    try {
      if (map.contains("Message"))
        jtype = "Message"
      else if (map.contains("Container"))
        jtype = "Container"
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    jtype
  }

  def processJsonMap(key: String, map: Map[String, Any]): Message = {
    var msg1: Message = null
    type messageMap = Map[String, Any]
    try {
      if (map.contains(key)) {
        if (map.get(key).get.isInstanceOf[messageMap]) {
          val message = map.get(key).get.asInstanceOf[Map[String, Any]]
          if (message.get("Fixed").get.equals("true")) {
            msg1 = getMsgorCntrObj(message, key).asInstanceOf[Message]
          } else throw new Exception("Message in json is not Map")
        }
      } else throw new Exception("Incorrect json")
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    msg1
  }

  def getMsgorCntrObj(message: Map[String, Any], mtype: String): Message = {
    var ele: List[Element] = null
    var tdata: TransformData = null
    var tdataexists: Boolean = false
    var pkg: String = "com.ligadata.messagedef"
    val tkey: String = "TransformData"
    try {
      if (message != null) {
        for (key: String <- message.keys) {
          if (key.equals("Elements") || key.equals("Fields") || key.equals("Concepts"))
            ele = getElementsObj(message, key).asInstanceOf[List[Element]]
          if (mtype.equals("Message") && message.contains(tkey)) {
            if (key.equals(tkey)) {
              tdataexists = true
              tdata = getTransformData(message, key)
            }
          }
        }
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    val physicalName: String = pkg + "." + message.get("NameSpace").get.toString + "_" + message.get("Name").get.toString() + "_" + message.get("Version").get.toString().replaceAll("[.]", "").toInt.toString
    new Message(mtype, message.get("NameSpace").get.toString, message.get("Name").get.toString(), physicalName, message.get("Version").get.toString(), message.get("Description").get.toString(), message.get("Fixed").get.toString(), ele.toList, tdataexists, tdata, null, pkg)
  }

  def getTransformData(message: Map[String, Any], tkey: String): TransformData = {
    var iarr: Array[String] = null
    var oarr: Array[String] = null
    var karr: Array[String] = null
    type tMap = Map[String, Any]

    if (message.get(tkey).get.isInstanceOf[tMap]) {
      val tmap: Map[String, Any] = message.get(tkey).get.asInstanceOf[Map[String, Any]]
      for (key <- tmap.keys) {
        if (key.equals("Input"))
          iarr = gettData(tmap, key)
        if (key.equals("Output"))
          oarr = gettData(tmap, key)
        if (key.equals("Keys"))
          karr = gettData(tmap, key)
      }
    }
    new TransformData(iarr, oarr, karr)
  }

  def gettData(tmap: Map[String, Any], key: String): Array[String] = {
    type tList = List[String]
    var tlist: List[String] = null
    if (tmap.contains(key) && tmap.get(key).get.isInstanceOf[tList])
      tlist = tmap.get(key).get.asInstanceOf[List[String]]
    tlist.toArray
  }

  def getElementsObj(message: Map[String, Any], key: String): List[Element] = {
    var list: List[Element] = Nil

    try {
      if (key.equals("Elements") || key.equals("Fields")) {
        list = getElements(message, key).asInstanceOf[List[Element]]
      } else if (key.equals("Concepts")) {
        list = getConcepts(message, key)
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    list
  }

  def getConcepts(message: Map[String, Any], key: String): List[Element] = {
    var list: List[Element] = Nil
    type messageList = List[Map[String, Any]]
    if (message.get(key).get.isInstanceOf[messageList]) {
      val eList = message.get(key).get.asInstanceOf[List[Map[String, Any]]]
      for (l <- eList) {
        if (l.isInstanceOf[Map[String, Any]]) {
          val eMap: Map[String, Any] = l.asInstanceOf[Map[String, Any]]
          //lbuffer += getElement(eMap)
        }
      }
    }
    list
  }

  def getElements(message: Map[String, Any], key: String): List[Element] = {
    var lbuffer = new ListBuffer[Element]
    type messageList = List[Map[String, Any]]
    type keyMap = Map[String, Any]
    try {

      if (message.get(key).get.isInstanceOf[messageList]) {
        val eList = message.get(key).get.asInstanceOf[List[Map[String, Any]]]
        for (l <- eList) {
          if (l.isInstanceOf[keyMap]) {
            val eMap: Map[String, Any] = l.asInstanceOf[Map[String, Any]]
            if (eMap.contains("Concept")) {
              // lbuffer += getConcept(eMap, "Concept")

            } else if (eMap.contains("Field")) {
              lbuffer += getElement(eMap)
            } else if (key.equals("Fields"))
              lbuffer += getElementData(eMap.asInstanceOf[Map[String, String]], key)
          }
        }
      } else throw new Exception("Elements list do not exist in json")
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    lbuffer.toList
  }

  def getConcept(eMap: Map[String, Any], key: String): List[Element] = {
    var lbuffer = new ListBuffer[Element]
    var concept: Concept = null
    if (eMap == null) throw new Exception("Concept Map is null")
    try {
      for (eKey: String <- eMap.keys) {
        val namestr = eMap.get(eKey).get.toString.split("\\.")
        if ((namestr != null) && (namestr.size == 2)) {
          concept.NameSpace = namestr(0)
          concept.Name = namestr(1)
          //  concept.Type = MdMgr.GetMdMgr.Attribute(namestr(0), namestr(1)).tType.toString()
          // concept.Type = MdMgr.GetMdMgr.Attribute(key, ver, onlyActive)(namestr(0), namestr(1)).tType.toString()
          val e = getcElement(concept, key)
          lbuffer += e
          //   val attr = MdMgr.GetMdMgr.Attribute(namestr(0), namestr(1))
        }
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    lbuffer.toList
  }

  def getcElement(concept: Concept, eType: String): Element = {
    new Element(concept.NameSpace, concept.Name, concept.Type, eType)
  }

  def getElement(eMap: Map[String, Any]): Element = {
    var ele: Element = null
    type keyMap = Map[String, String]
    if (eMap == null) throw new Exception("element Map is null")
    try {
      for (eKey: String <- eMap.keys) {
        if (eMap.get(eKey).get.isInstanceOf[keyMap]) {
          val element = eMap.get(eKey).get.asInstanceOf[Map[String, String]]
          ele = getElementData(element, eKey)
        }
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    ele
  }

  def getElementData(field: Map[String, String], key: String): Element = {
    var ele: Element = null
    var name: String = ""
    var namespace: String = ""
    var ttype: String = ""
    type string = String;
    if (field == null) throw new Exception("element Map is null")
    try {
      if (field.contains("NameSpace") && (field.get("NameSpace").get.isInstanceOf[string]))
        namespace = field.get("NameSpace").get.asInstanceOf[String]

      if (field.contains("Name") && (field.get("Name").get.isInstanceOf[String]))
        name = field.get("Name").get.asInstanceOf[String]

      if (field.contains("Type") && (field.get("Type").get.isInstanceOf[string])) {
        val fieldstr = field.get("Type").get.toString.split("\\.")
        if ((fieldstr != null) && (fieldstr.size == 2)) {
          namespace = fieldstr(0)
          ttype = field.get("Type").get.asInstanceOf[String]
        } else
          ttype = field.get("Type").get.asInstanceOf[String]

      }
      ele = new Element(namespace, name, ttype, key)
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw e
      }
    }
    ele
  }
}
