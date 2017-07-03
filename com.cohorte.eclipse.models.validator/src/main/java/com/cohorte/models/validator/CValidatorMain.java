package main.java.com.cohorte.models.validator;

import org.psem2m.utilities.logging.CActivityLoggerBasicConsole;
import org.psem2m.utilities.logging.IActivityLogger;

public class CValidatorMain {
	public static void main(final String[] aArgs) {
		IActivityLogger wLogger = CActivityLoggerBasicConsole.getInstance();
		try {
			CValidator wValidator = new CValidator(wLogger);
			wValidator.traceContext();
			wValidator.validate();
			System.exit(0);

		} catch (Exception e) {
			wLogger.logSevere(null, "main", "ERROR; exception [%s]", e);
			System.exit(1);
		}
	}
}
