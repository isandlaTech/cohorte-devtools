/*
         __  _ __                                             
  __  __/ /_(_) /   _________  ____ ___  ____ ___  ____  ____ 
 / / / / __/ / /   / ___/ __ \/ __ `__ \/ __ `__ \/ __ \/ __ \
/ /_/ / /_/ / /   / /__/ /_/ / / / / / / / / / / / /_/ / / / /
\__,_/\__/_/_/____\___/\____/_/ /_/ /_/_/ /_/ /_/\____/_/ /_/ 
            /_____/                                           

 * 
 * util_common.js
 * 
 * http://patorjk.com/software/taag/#p=display&f=Slant&t=util_common
 */

var gScriptId;
var gScriptTS;	// timestamps of all the scripts (amin and all includes)
var gScriptRun;	// instance of the runner of the current script, able to log , to instanciate the tools like "CDomUtils", "CUUIDUtils",...
var gScriptCtx;	// instance of the context


/* ----------------------------------------------------------------------
 * 
 */
function toJavaString(aObject){
	if (aObject == undefined  ){
		return "undefined";
	}
	if ( aObject == null ){
		return "null";
	}
	return java.lang.String.valueOf(aObject);	
	
}
/* ----------------------------------------------------------------------
 * @see https://stackoverflow.com/questions/8853986/converting-a-javascript-array-to-a-java-array
 */
function toJavaArray(){
	
	var wSize = arguments.length;
	
	var wArray = java.lang.reflect.Array.newInstance(java.lang.String, wSize);
	 
	for (i in arguments){
		wArray[i]=arguments[i];
	}
	//println("wArray:"+ org.psem2m.utilities.CXStringUtils.stringTableToString(  wArray))
	return wArray;
}




/* ----------------------------------------------------------------------
 * 
 */
function CTimer(){
	this.timer = gScriptRun.newTimer();
	
	this.getDuration = function(){
		return this.timer.getDurationStrMicroSec();
	}
}


/* ----------------------------------------------------------------------
 * 
 */
function CReport() {
	this.text = "";
	
	this.addLineError = function(aError){
		var wLine = "ERROR: "+aError;
		this.addLine(wLine);
	}
	this.addLine= function(aText){
		if (aText != undefined && aText != null && aText.length>0){
			this.text += "\n"+aText;
		}
	}	
	this.addLineSep= function(aChar){
		if (aChar == undefined || aChar == null || aChar.length==0){
			aChar="-";
		}
		var wLine = aChar.repeat(120);
		this.addLine(wLine);
	}
	this.addTitle = function(aTitle,aSubTitle){
		this.addLineSep("=");
		this.addLine(aTitle);
		this.addLine(aSubTitle);
		this.addLineSep("=");
	}
	this.getText= function(){
		return this.text;
	}
}




//eof
