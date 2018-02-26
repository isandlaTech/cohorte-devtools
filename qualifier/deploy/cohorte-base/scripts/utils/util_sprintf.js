/*
         __  _ __                     _       __  ____
  __  __/ /_(_) /    _________  _____(_)___  / /_/ __/
 / / / / __/ / /    / ___/ __ \/ ___/ / __ \/ __/ /_  
/ /_/ / /_/ / /    (__  ) /_/ / /  / / / / / /_/ __/  
\__,_/\__/_/_/____/____/ .___/_/  /_/_/ /_/\__/_/     
            /_____/   /_/                                                               

 * 
 * util_sprintf.js
 * 
 * http://patorjk.com/software/taag/#p=display&f=Slant&t=util_common
 *
 *-------------------------------------------------------------------------------------------
 * 
 * @see http://stackoverflow.com/questions/2405425/is-there-a-sprintf-equivalent-in-javascript
 * @see http://plugins.jquery.com/project/sprintf
 * 
 * www.mindstep.com                              www.mjslib.com
 * info-oss@mindstep.com                         mjslib@mjslib.com
 * 
 **-----------------------------------------------------------------------------------------*/

 /*
  *  Just an equivalent of the corresponding libc function
  *
  *  var str= sprintf("%010d %-10s",intvalue,strvalue);
  *
  */

  function sprintf(fmt){
      return _sprintf_(fmt,arguments,1);
  }

/*-------------------------------------------------------------------------------------------
 **
 **	Following code is private; don't use it directly !
 **
 **-----------------------------------------------------------------------------------------*/

// var FREGEXP = new RegExp("^([^%]*)%([-+])?(0)?(\d+)?(\.(\d+))?([doxXcsf])(.*)$","g");

// var HDIGITS = [ "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c","d", "e", "f" ];

/* ---------------------------------------------------------- */
function _empty(str) {
	if (str === undefined || str === null) {
		return true;
	}
	return (str == "") ? true : false;
}

/* ---------------------------------------------------------- */
function _int_(val) {
	return Math.floor(val);
}
/* ---------------------------------------------------------- */
function _printf_num_(val, base, pad, sign, width) {
	val = parseInt(val, 10);
	if (isNaN(val)) {
		return "NaN";
	}
	aval = (val < 0) ? -val : val;
	var ret = "";

	if (aval == 0) {
		ret = "0";
	} else {
		var HDIGITS = [ "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c","d", "e", "f" ];
		while (aval > 0) {
			ret = HDIGITS[aval % base] + ret;
			aval = _int_(aval / base);
		}
	}
	if (val < 0) {
		ret = "-" + ret;
	}
	if (sign == "-") {
		pad = " ";
	}
	return _printf_str_(ret, pad, sign, width, -1);
}

/* ---------------------------------------------------------- */
function _printf_float_(val, base, pad, sign, prec) {
	if (prec == undefined) {
		if (parseInt(val) != val) {
			// No decimal part and no precision -> use int formatting
			return "" + val;
		}
		prec = 5;
	}

	var p10 = Math.pow(10, prec);
	var ival = "" + Math.round(val * p10);
	var ilen = ival.length - prec;
	if (ilen == 0) {
		return "0." + ival.substr(ilen, prec);
	}
	return ival.substr(0, ilen) + "." + ival.substr(ilen, prec);
}

/* ---------------------------------------------------------- */
function _printf_str_(val, pad, sign, width, prec) {
	var npad;

	if (val === undefined) {
		return "(undefined)";
	}
	if (val === null) {
		return "(null)";
	}
	if ((npad = width - val.length) > 0) {
		if (sign == "-") {
			while (npad > 0) {
				val += pad;
				npad--;
			}
		} else {
			while (npad > 0) {
				val = pad + val;
				npad--;
			}
		}
	}
	if (prec > 0) {
		return val.substr(0, prec);
	}
	return val;
}

/* ---------------------------------------------------------- */
function _sprintf_(fmt, av, index) {
	var output = "";
	var i, m, line, match;
	var FREGEXP = /^([^%]*)%([-+])?(0)?(\d+)?(\.(\d+))?([doxXcsf])(.*)$/;

	line = fmt.split("\n");
	for (i = 0; i < line.length; i++) {
		if (i > 0) {
			output += "\n";
		}
		fmt = line[i];
		while (match = FREGEXP.exec(fmt)) {
			var sign = "";
			var pad = " ";

			if (!_empty(match[1])) // the left part
			{
				// You can't add this blindly because mozilla set the value to
				// <undefined> when
				// there is no match, and we don't want the "undefined" string be
				// returned !
				output += match[1];
			}
			if (!_empty(match[2])) // the sign (like in %-15s)
			{
				sign = match[2];
			}
			if (!_empty(match[3])) // the "0" char for padding (like in %03d)
			{
				pad = "0";
			}

			var width = match[4]; // the with (32 in %032d)
			var prec = match[6]; // the precision (10 in %.10s)
			var type = match[7]; // the parameter type

			fmt = match[8];

			if (index >= av.length) {
				output += "[missing parameter for type '" + type + "']";
				continue;
			}

			var val = av[index++];

			switch (type) {
			case "d":
				output += _printf_num_(val, 10, pad, sign, width);
				break;
			case "o":
				output += _printf_num_(val, 8, pad, sign, width);
				break;
			case "x":
				output += _printf_num_(val, 16, pad, sign, width);
				break;
			case "X":
				output += _printf_num_(val, 16, pad, sign, width).toUpperCase();
				break;
			case "c":
				output += String.fromCharCode(parseInt(val, 10));
				break;
			case "s":
				output += _printf_str_(val, pad, sign, width, prec);
				break;
			case "f":
				output += _printf_float_(val, pad, sign, width, prec);
				break;
			default:
				output += "[unknown format '" + type + "']";
				break;
			}
		}
		output += fmt;
	}
	return output;
}
