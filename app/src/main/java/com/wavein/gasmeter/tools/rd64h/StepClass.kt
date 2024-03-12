package com.wavein.gasmeter.tools.rd64h

// 傳收步驟class
open class BaseStep
data class RTestStep(val text:String) : BaseStep()
class DTestStep : BaseStep()

class __AStep : BaseStep()
class __5Step : BaseStep()
class D70Step : BaseStep()
data class R80Step(val meterIds:List<String>) : BaseStep()
data class D05Step(val count:Int) : BaseStep()

data class R89Step(val meterId:String) : BaseStep()
class D36Step : BaseStep()
data class R70Step(val meterId:String) : BaseStep()

// R87, 參數說明請參考 ALine.kt | RD64H.kt_createR87Aline
data class R87Step(
	val securityLevel:SecurityLevel = SecurityLevel.NoSecurity,
	val cc:String = "\u0021\u0040\u0000\u0000",
	val adr:String,
	val op:String,
	val data:String = ""
) : BaseStep()

class D87D01Step : BaseStep()
class D87D05Step : BaseStep()
class D87D19Step : BaseStep()
class D87D23Step : BaseStep()
class D87D24Step : BaseStep()
class D87D16Step : BaseStep()
class D87D57Step : BaseStep()
class D87D58Step : BaseStep()
class D87D59Step : BaseStep()
class D87D31Step : BaseStep()
class D87D50Step : BaseStep()
class D87D51Step : BaseStep()
class D87D41Step : BaseStep()
class D87D02Step : BaseStep()


