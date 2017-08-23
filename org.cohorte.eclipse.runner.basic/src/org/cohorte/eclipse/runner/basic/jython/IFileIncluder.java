package org.cohorte.eclipse.runner.basic.jython;

@PythonClass(modulepath = "cohorte.config.includer", classname = "FileIncluder")
public interface IFileIncluder {

	Object get_content(String aFileName, boolean aWantJson);

	void set_finder(IFileFinder aFinder);

}
