package com.wavein.gasmeter.tools.rd64h.info

// 電文處理基底
open class BaseInfo(open val text:String) {
	override fun toString():String = text
	open var isCorrectParsed = false

	companion object {

		// 將接收的電文轉成易讀的class (DxxInfo)
		fun <T : BaseInfo> get(text:String, infoClass:Class<T>):BaseInfo {
			return infoClass.constructors.first().newInstance(text) as BaseInfo
		}
	}
}

