package org.cohorte.eclipse.runner.basic.jython;

import java.util.List;

@PythonClass(modulepath = "cohorte.config.finder", classname = "FileFinder")
public interface IFileFinder {
	void _set_roots(List<String> rootList);

	String find_rel(String aFileName);

	String find_rel(String aFileName, String aBaseFile);
}
