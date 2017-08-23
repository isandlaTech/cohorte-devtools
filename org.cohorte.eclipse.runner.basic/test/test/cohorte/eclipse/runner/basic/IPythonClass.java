package test.cohorte.eclipse.runner.basic;

import org.cohorte.eclipse.runner.basic.jython.PythonClass;

@PythonClass(modulepath = "pyclass", classname = "TestClass")
public interface IPythonClass {

	public String method(String aString);

}
