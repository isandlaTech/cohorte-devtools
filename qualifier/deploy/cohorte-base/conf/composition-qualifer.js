{

    "name": "CohorteDevTestQualifier",
    "root": {
    	
    	/* import here (in root) the content of the root json objects contained in the files */
    	"import-files" : [ ],
    	
        "name": "CohorteDevTestQualifier",
        
        /* your component descriptions here */
        "components": [
 
           /* *************************** Isolate ONE ************************* */                                                     
           {
        	 "name" : "qualifier-cohorte-isolates-ocil-adapter",
             "factory" : "qualifier-cohorte-isolates-ocil-adapter-factory",
             "isolate" : "isolate-one"
           },
           {
             "name" : "qualifier-cohorte-isolates-main-isolate-one",
              "factory" : "qualifier-cohorte-isolates-main-isolate-one-factory",
              "isolate" : "isolate-one"
           },
           
           /* *************************** Isolate TWO ************************* */  
           {
               "name" : "qualifier-cohorte-isolates-main-isolate-two",
               "factory" : "qualifier-cohorte-isolates-main-isolate-two-factory",
                "isolate" : "isolate-two"
             }
        ]
    }
}