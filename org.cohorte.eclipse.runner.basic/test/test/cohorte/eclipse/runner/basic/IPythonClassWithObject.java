package test.cohorte.eclipse.runner.basic;

import org.cohorte.eclipse.runner.basic.jython.PythonClass;

@PythonClass(modulepath = "pyclass2", classname = "TestClassWithObject")
public interface IPythonClassWithObject {

	public IPythonClassWithParam get();

	public String method();

	public void set(IPythonClassWithParam aObj);

}
