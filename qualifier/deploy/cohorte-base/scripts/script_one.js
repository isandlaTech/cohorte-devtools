//ATTENTION : the next line to include the script "util_common.js" before compiling this script.
//#include "./utils/util_common.js"
//#include "./utils/util_sprintf.js"


/*
 * 4 global variables managed by "org.psem2m.utilities.scripting":
 * 
 * gScriptId : l'id du script
 * gScriptTS : timestamp de du source 
 * gScriptCtx: l'instance de IXJsRuningContext
 * gScriptRun: l'instance de IXJsRunner
 * 
 * @see the method : org.psem2m.utilities.scripting.CXJsRunner.run(IXJsRuningContext)
 * 
 * 
 * n extended global variables managed by ...
 * eg.
 * 
 * gPlatformDirsSvc : l'instance de IPlatformDirsSvc 
 * 
 * @see the method qualif.cohorte.isolates.main.isolate.one.scripts.CCpntQualifierScripts.runScript(String, Map<String, Object>)
 * 
 * 
 */


/* ----------------------------------------------------------------------
 * Generates trace lines in the IActivityLogger set in the runner 
 * 
...scripting.CXJsRunner_8029;              logBeginStep; dumpInfos
...scripting.CXJsRunner_8029;                 dumpInfos; getDurationNs=[0.0]
...scripting.CXJsRunner_8029;                 dumpInfos; getfomatedTS=[2018/02/03 17:47:56:216]
...scripting.CXJsRunner_8029;                 dumpInfos; getFormatedText=[v1=[value01] v2]
...scripting.CXJsRunner_8029;                 dumpInfos; getFormatedTitle=[SCRIPT[script_one.js]]
...scripting.CXJsRunner_8029;                 dumpInfos; getId=[script_one.js]
...scripting.CXJsRunner_8029;                 dumpInfos; getSourceName=[script_one.js]
...scripting.CXJsRunner_8029;                 dumpInfos; getTimeStamps=[name=[script_one.js],timeStamp=[2018/02/8/02/03 11:50:48:00];name=[...il_common.js],timeStamp=[2018/02/03 17:40:57:00]]
...scripting.CXJsRunner_8029;                logEndStep; dumpInfos. duration=[ 0.430]
 * 
 */
function dumpRunnerInfos(){
	gScriptRun.logBeginStep("dumpInfos");
	var wTimer = new CTimer();

	gScriptRun.logInfo("dumpInfos", "getDurationNs=[%s]",gScriptRun.getDurationNs() );
	gScriptRun.logInfo("dumpInfos", "getfomatedTS=[%s]",gScriptRun.getfomatedTS() );
	gScriptRun.logInfo("dumpInfos", "getFormatedText=[%s]",gScriptRun.stringFormat("v1=[%s] v2","value01","value02") );
	gScriptRun.logInfo("dumpInfos", "getFormatedTitle=[%s]",gScriptRun.getFormatedTitle() );
	gScriptRun.logInfo("dumpInfos", "getId=[%s]",gScriptRun.getId() );
	gScriptRun.logInfo("dumpInfos", "getSourceName=[%s]",gScriptRun.getSourceName() );
	gScriptRun.logInfo("dumpInfos", "getTimeStamps=[%s]",gScriptRun.getTimeStamps() );
	
	gScriptRun.logEndStep("dumpInfos. duration=[%s]",wTimer.getDuration());
}
/* ----------------------------------------------------------------------
 * 
   ========================================================================================================================
   TEST DUMP BINDINGS
   ========================================================================================================================
   EngineBinding(0) : 'CReport'=[org.mozilla.javascript.InterpretedFunction@47bb892d]
   EngineBinding(1) : 'CTimer'=[org.mozilla.javascript.InterpretedFunction@438e1cfe]
   EngineBinding(2) : 'FORMATER'=[org.psem2m.utilities.scripting.CXJsFormater@1a09ab5a]
...
   EngineBinding(27) : 'toto'=[tutu]
   EngineBinding(28) : 'wIdx'=[2.0]
   EngineBinding(29) : 'wNodeName'=[qualifier-node-a]
   EngineBinding(30) : 'wTimer'=[org.psem2m.utilities.CXTimer@7e1c9ecb]
   Duration=[ 6.649]
   ------------------------------------------------------------------------------------------------------------------------
    
 */
function testDumpBindings(){
	gScriptRun.logBeginStep("testDumpBindings");
	
	var wTimer = new CTimer();
	var wReport = new CReport();
	wReport.addTitle("TEST DUMP BINDINGS");
	try{
	
		// get the Bindings stored in an extend of a Map<String, Object>
		var wEngineBindings = gScriptCtx.getEngineBindings();
		
		// sort
		wEngineBindings = new java.util.TreeMap(wEngineBindings);
		
		// get the entry set
		var wKeySet = wEngineBindings.entrySet();		
		//println ("wKeySet:"+wKeySet);
		
		var wBindingIdx =0;
		// ATTENTION the "var" is important to ConcurrentModificationException
		for ( var wEntry in  Iterator(wKeySet) ){

			try{
				var wKey = wEntry.getKey();
				var wObject = toJavaString(wEntry.getValue());
				var wIdx = wBindingIdx.toFixed(0).toString();
				gScriptRun.logInfo("dumpEngineBindings","EngineBinding(%4s) : %-40s=[%s]", wIdx,wKey,wObject );			
				wReport.addLine(sprintf("EngineBinding(%3s) : '%40s'=[%s]", wBindingIdx.valueOf(),wKey,wObject ) );
			}
			catch(e){
				wReport.addLineError(e);
			}
			wBindingIdx++;
		}
	}
	catch(e){
		wReport.addLineError(e);
	}
	wReport.addLine(sprintf("Duration=[%s]",wTimer.getDuration()));
	wReport.addLineSep("-");
	wReport.addLine(" ");	
	
	gScriptRun.logEndStep("testDumpBindings. duration=[%s]",wTimer.getDuration());
	return wReport.getText();
}
/* ----------------------------------------------------------------------
 * 
   ========================================================================================================================
   TEST REPORT
   2 lines
   ========================================================================================================================
   line=[0]
   line=[1]
   Duration=[ 0.207]
   ------------------------------------------------------------------------------------------------------------------------
 * 
 */
function testReport(){
	gScriptRun.logBeginStep("testReport");

	var wTimer = new CTimer();
	var wReport = new CReport();
	wReport.addTitle("TEST REPORT","2 lines");
	
	for (wIdx = 0; wIdx < 2; wIdx++) { 
		wReport.addLine( sprintf("\nline=[%s]",wIdx) );
	}

	wReport.addLine(sprintf("Duration=[%s]",wTimer.getDuration()));
	wReport.addLineSep("-");
	wReport.addLine(" ");
	
	gScriptRun.logEndStep("testReport. duration=[%s]",wTimer.getDuration());
	return wReport.getText();
}
/* ----------------------------------------------------------------------
 * Generates a report
 * 
   ========================================================================================================================
   TEST JAVA ARRAY
   2 test
   ========================================================================================================================
   value1=[111] value2=[222]
   value3=[333] value4=[444]
   ERROR: ReferenceError: "qsdf" n'est pas défini
   Duration=[ 0.474]
   ------------------------------------------------------------------------------------------------------------------------
 * 
 */
function testJavaArray(){
	gScriptRun.logBeginStep("testJavaArray");

	var wTimer = new CTimer();
	var wReport = new CReport();
	wReport.addTitle("TEST JAVA ARRAY","2 test");
	try{
		// produce a java string using the method "stringFormat()" of the current script runner
		var wJavaLine = gScriptRun.stringFormat("value1=[%s] value2=[%s]","111","222" );
		// convert java String to Javascript String
		var wLine = new String(wJavaLine).valueOf()
		// add the line in the report
		wReport.addLine( wLine );
		
		// add a line in the report produced by the method "sprintf"
		wReport.addLine(sprintf("value3=[%s] value4=[%s]","333",444) );
		
		qsdf;
	}
	catch(e){
		wReport.addLineError(e);
	}
	wReport.addLine(sprintf("Duration=[%s]",wTimer.getDuration()));
	wReport.addLineSep("-");
	wReport.addLine(" ");

	gScriptRun.logEndStep("testJavaArray. duration=[%s]",wTimer.getDuration());
	return wReport.getText();
}
/* ----------------------------------------------------------------------
 * Writes the given 'aText' in the buffer of the context of the script
 */
function testBuffer(aText){
	gScriptRun.logBeginStep("testBuffer");
	var wTimer = new CTimer();

	gScriptCtx.getBuffer().write( aText );

	gScriptRun.logEndStep("testBuffer. duration=[%s]",wTimer.getDuration());
}
/* ----------------------------------------------------------------------
 *  MAIN (scrip_one)
 */
try{
	gScriptRun.logBeginStep("run [%s] // timestamp [%s]) ",gScriptId,gScriptTS);
	var wTimer = new CTimer();	var wTimer = gScriptRun.newTimer();
	
	var wNodeName = gPlatformDirsSvc.getNodeName();

	gScriptRun.logInfo("run","NodeName=[%s]",wNodeName);

	dumpRunnerInfos();

	testBuffer( testReport() );
	
	testBuffer( testJavaArray() );
	
	testBuffer( testDumpBindings() );

	var wDuration = wTimer.getDurationStrMicroSec();
	testBuffer(sprintf("*** End of script OK *** duration=[%s]",wDuration));
	gScriptRun.logEndStep("run. duration=[%s]",wDuration);
}catch(e){
	gScriptRun.logSevere("run",e,"Error during  exec [%s]",gScriptRun.getId() );
}
gScriptRun.logInfo("run", "Trace report: \n%s",gScriptRun.getTraceReport() );

//eof