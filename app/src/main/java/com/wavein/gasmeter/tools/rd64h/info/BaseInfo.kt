package com.wavein.gasmeter.tools.rd64h.info


open class BaseInfo(open val text:String) {
	override fun toString():String = text

	companion object {


		fun <T : BaseInfo> get(text:String, infoClass:Class<T>):BaseInfo {
			return infoClass.constructors.first().newInstance(text) as BaseInfo
		}
	}
}

