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
 * Writes the given 'aText' in the buffer of the context of the script
 */
function testBuffer(aText){
	gScriptRun.logBeginStep("testBuffer");
	var wTimer = new CTimer();

	gScriptCtx.getBuffer().write( aText );

	gScriptRun.logEndStep("testBuffer. duration=[%s]",wTimer.getDuration());
}
/* ----------------------------------------------------------------------
 *  MAIN (scrip_two)
 */
try{
	gScriptRun.logBeginStep("run [%s] // timestamp [%s]) ",gScriptId,gScriptTS);
	var wTimer = new CTimer();	var wTimer = gScriptRun.newTimer();
	


	testBuffer( testReport() );
	
	

	gScriptRun.logEndStep("run. duration=[%s]",wTimer.getDurationStrMicroSec());
}catch(e){
	gScriptRun.logSevere("run",e,"Error during  exec [%s]",gScriptRun.getId() );
}
gScriptRun.logInfo("run", "Trace report: \n%s",gScriptRun.getTraceReport() );

//eof