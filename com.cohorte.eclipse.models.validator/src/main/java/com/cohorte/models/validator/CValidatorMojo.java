package main.java.com.cohorte.models.validator;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.psem2m.utilities.logging.CActivityLoggerBasicConsole;

/**
 * Goal which touches a timestamp file.
 *
 * @goal validator
 *
 * @phase process-sources
 */
public class CValidatorMojo extends AbstractMojo {

	/**
	 * cohorte base path.
	 *
	 * @parameter expression="${cohorte.base}"
	 * @required
	 */
	private String pCohorteBase;

	/**
	 * cohorte base path.
	 *
	 * @parameter expression="${cohorte.data}"
	 * @required
	 */
	private String pCohorteData;

	/**
	 * cohorte base path.
	 *
	 * @parameter expression="${prefix.module}" default-value="module_"
	 * @required
	 */
	private String pPrefixModule;

	/**
	 * tag to naalyse (default $include).
	 *
	 * @parameter expression="${tag}" default-value="$include"
	 * @required
	 */
	private String pTag;

	@Override
	public void execute() throws MojoExecutionException {

		CValidator wValidator = new CValidator(
				CActivityLoggerBasicConsole.getInstance(), pCohorteData,
				pCohorteBase, pTag, pPrefixModule);

		wValidator.validate();
	}
}
