this directory contains the standard jython lib . jython api failed to load it when it's in the standalone.jar. 
Issue : http://bugs.jython.org/issue2355 (still open in 17/08/2017)

To prevent this behavior we set the all lib and load it . use by cohorte.eclipse.runner.base

set env variable -Djython.stdlib=${project_loc:org.cohorte.eclipse.runner.basic} to be able to retreive the location of the stdlib
