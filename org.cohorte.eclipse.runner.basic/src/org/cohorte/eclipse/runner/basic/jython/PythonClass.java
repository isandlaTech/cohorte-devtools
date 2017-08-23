package org.cohorte.eclipse.runner.basic.jython;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface PythonClass {

	String classname();

	String modulepath();
}
