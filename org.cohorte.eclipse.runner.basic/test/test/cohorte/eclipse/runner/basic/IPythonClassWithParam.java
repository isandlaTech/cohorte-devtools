package test.cohorte.eclipse.runner.basic;

import org.cohorte.eclipse.runner.basic.jython.PythonClass;

@PythonClass(modulepath = "pyclass", classname = "TestClassWithParam")
public interface IPythonClassWithParam {

	public String method();
}
