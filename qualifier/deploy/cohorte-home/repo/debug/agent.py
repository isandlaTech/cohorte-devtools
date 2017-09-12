#!/usr/bin/env python
# -- Content-Encoding: UTF-8 --
"""
COHORTE debug agent, to send debug info to Debug REST API.

:author: Bassem Debbabi
:license: Apache Software License 2.0

..

    Copyright 2015 isandlaTech

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
"""

# Documentation strings format
__docformat__ = "restructuredtext en"

# Boot module version
__version__ = "1.0.0"

# ------------------------------------------------------------------------------

# Cohorte
import cohorte

# Pelix framework
from pelix.ipopo.decorators import ComponentFactory, Provides, \
    Validate, Invalidate, Property, Requires
import pelix.constants
import pelix.framework
import pelix.http
import pelix.ipopo.constants
import pelix.shell
from pelix.shell.ipopo import ipopo_state_to_str
from pelix.shell.core import _ShellUtils

# Herald
import herald
import herald.beans as beans

# Python standard library
import logging
import sys
import threading
import traceback
import os
import time
import json

# cohorte plutform debug agent and api
import debug

# ------------------------------------------------------------------------------

_logger = logging.getLogger(__name__)

# ------------------------------------------------------------------------------

_SUBJECT_PREFIX = "cohorte/debug/agent"
""" Common prefix to cohorte agent  """

_SUBJECT_MATCH_ALL = "{0}/*".format(_SUBJECT_PREFIX)
""" Filter to match agent signals """

SUBJECT_GET_ISOLATE_DETAIL = "{0}/get_isolate_detail".format(_SUBJECT_PREFIX)
""" Signal to request the detail of the local isolate """

SUBJECT_GET_BUNDLES = "{0}/get_bundles".format(_SUBJECT_PREFIX)
""" Signal to request the bundles of the isolate """

SUBJECT_GET_BUNDLE_DETAIL = "{0}/get_bundle_detail".format(_SUBJECT_PREFIX)
""" Signal to request the bundle details """

SUBJECT_GET_FACTORIES = "{0}/get_factories".format(_SUBJECT_PREFIX)
""" Signal to request the component factories of the isolate """

SUBJECT_GET_FACTORY_DETAIL = "{0}/get_factory_detail".format(_SUBJECT_PREFIX)
""" Signal to request the component factory details """

SUBJECT_GET_INSTANCES = "{0}/get_instances".format(_SUBJECT_PREFIX)
""" Signal to request the instances of the isolate """

SUBJECT_GET_INSTANCE_DETAIL = "{0}/get_instance_detail".format(_SUBJECT_PREFIX)
""" Signal to request the detail of one instance """

SUBJECT_GET_SERVICES = "{0}/get_services".format(_SUBJECT_PREFIX)
""" Signal to request the services of the isolate """

SUBJECT_GET_THREADS = "{0}/get_threads".format(_SUBJECT_PREFIX)
""" Signal to request the current threads of the isolate """

SUBJECT_GET_ISOLATE_LOGS = "{0}/get_isolate_logs".format(_SUBJECT_PREFIX)
""" Signal to request the list of isolate logs """

SUBJECT_GET_ISOLATE_LOG = "{0}/get_isolate_log".format(_SUBJECT_PREFIX)
""" Signal to request the isolate logs """

SUBJECT_GET_ISOLATE_DIRECTORY = "{0}/get_isolate_directory".format(_SUBJECT_PREFIX)
""" Signal to request the isolate herald local directory """

SUBJECT_GET_ISOLATE_ACCESSES = "{0}/get_isolate_accesses".format(_SUBJECT_PREFIX)
""" Signal to request the isolate herald accesses """

SUBJECT_SET_ISOLATE_LOGS_LEVEL = "{0}/set_isolate_logs_level".format(_SUBJECT_PREFIX)
""" Signal to change the isolate logs level """

@ComponentFactory('cohorte-debug-agent-factory')
@Requires("_ipopo", pelix.ipopo.constants.SERVICE_IPOPO)
@Requires('_herald', herald.SERVICE_HERALD)
@Requires('_directory', herald.SERVICE_DIRECTORY)
@Provides([debug.SERVICE_DEBUG, herald.SERVICE_LISTENER])
@Property('_filters', herald.PROP_FILTERS, [_SUBJECT_MATCH_ALL])
@Property('_reject', pelix.remote.PROP_EXPORT_REJECT, [debug.SERVICE_DEBUG])
class DebugAgent(object):
    """
    COHORTE Debug Agent.
    This component is instantiated on each Isolate (see: conf/boot-common-py.js)
    """
    def __init__(self):
        """
        Constructor
        """
        # Bundle context
        self._context = None
        # iPOPO
        self._ipopo = None
        # Herald
        self._herald = None
        # Herald filter property
        self._filters = None
        # Herald directory
        self._directory = None
        
        

    def get_isolate_detail(self):
        """
        Returns details about the local isolate
        """
        result = {}        
        for prop_var in sorted(dir(cohorte)):
            if prop_var.startswith('PROP'):
                key = getattr(cohorte, prop_var)
                value = self._context.get_property(key)
                result[key] = value
        # add http port
        port = -1
        svc_ref = self._context.get_service_reference(
            pelix.http.HTTP_SERVICE)
        if svc_ref is not None:
            port = svc_ref.get_property(pelix.http.HTTP_SERVICE_PORT)            
        result["cohorte.isolate.http.port"] = port  
        # overrite isolate kind to python (original=pelix)
        result["cohorte.isolate.kind"] = "Python"
        return json.dumps(result)

    def get_bundles(self):
        """
        Returns the list of isolate bundles
        """
        bundles = self._context.get_bundles()
        bundles.insert(0, self._context.get_bundle(0))
        
        result = [
            { 
              "id" : bundle.get_bundle_id(),
              "name" : bundle.get_symbolic_name(),
              "state" : _ShellUtils.bundlestate_to_str(
                                    bundle.get_state()),
              "version" : bundle.get_version()
            } for bundle in bundles              
        ]
        return json.dumps(result)

    def get_bundle_detail(self, bundle_number):
        """
        Returns details about the identified instance
        """
        details = {}        
        try:
            bundle_id = int(bundle_number)            
        except ValueError as ex:
            return json.dumps({"error" : str(ex)})
        else:
            # Integer ID
            try:
                bundle = self._context.get_bundle(bundle_id)
            except:
                return {}
        if bundle is None:
            return {}
        else:
            details = {
                "id": bundle.get_bundle_id(),
                "name" : bundle.get_symbolic_name(),
                "version" : bundle.get_version(),
                "state" : _ShellUtils.bundlestate_to_str(
                                    bundle.get_state()),
                "location" : bundle.get_location(),
                "published-services" : [],
                "used-services" : []
            }                   
            try:
                services = bundle.get_registered_services()
                if services:
                    details["published-services"] = [ str(svc_ref) for svc_ref in services ]                    
                else:
                    pass
            except pelix.constants.BundleException as ex:
                # Bundle in a invalid state
                pass
            try:
                services = bundle.get_services_in_use()
                if services:
                    details["used-services"] = [ str(svc_ref) for svc_ref in services ]                         
                else:
                    pass
            except pelix.constants.BundleException as ex:
                # Bundle in a invalid state
                pass
            
            return json.dumps(details)

    def get_factories(self):
        """
        Returns the list of isolate factories
        """
        ipopo_factories = self._ipopo.get_factories()
        result = [
            { 
              "name" : name,
              "bundle" : { 
                        "id": self._ipopo.get_factory_bundle(name).get_bundle_id(), 
                        "name": self._ipopo.get_factory_bundle(name).get_symbolic_name()
              } 
            } for name in ipopo_factories              
        ]
        return json.dumps(result)
        
    def get_factory_detail(self, factory_name):
        """
        Returns the details of one factory
        """
        details = None
        try:
            details = self._ipopo.get_factory_details(factory_name)
        except ValueError as ex:
            return json.dumps({"error" : str(ex)})
        if details is not None:
            factory_detail = {
                "kind": "Python",
                "name" : details["name"],
                "bundle" : { 
                    "id" : details["bundle"].get_bundle_id(), 
                    "name": details["bundle"].get_symbolic_name()  
                },
                "properties" : { },
                "provided-services" : [],
                "requirements" : [],
                "handlers": []
            }
            # factory properties
            properties = details.get('properties', None)
            if properties:    
                for key, value in properties.items():
                    factory_detail["properties"][key] = value                            
    	    # factory provided services
            services = details.get('services', None)
            if services:                
                for spec in services:
                    factory_detail["provided-services"].append(spec)
            # requirements
            requirements = details.get('requirements', None)
            if requirements: 
                for item in requirements:
                    req = {
                        "id": item['id'],
                        "specification": item['specification'],
                        "filter": item['filter'],
                        "aggregate": item['aggregate'],
                        "optional": item['optional']
                    }
                    factory_detail["requirements"].append(req)                                               
            # handlers
            handlers = details.get('handlers', None)
            if handlers:                
                handlers_headers = ('ID', 'Configuration')
                for key in sorted(handlers):
                    handler = {
                        "id": key,
                        "configuration": handlers[key]
                    }
                    factory_detail["handlers"].append(handler)
                                                                
            return json.dumps(factory_detail)
        else:
            return json.dumps({})
           
    def get_instances(self):
        """
        Returns the list of iPOPO instances in the local isolate
        """
        ipopo_instances = self._ipopo.get_instances()
        result = [
            {"name" : name, 
             "factory": factory, 
             "state": ipopo_state_to_str(state)}
            for name, factory, state in ipopo_instances]
        return json.dumps(result)

    def get_instance_detail(self, instance_name):
        """
        Returns details about the identified instance
        """
        details = None
        try:
            details = self._ipopo.get_instance_details(instance_name)
        except ValueError as ex:
            return json.dumps({"error" : str(ex)})
        # basic info
        if details is not None:
            instance_detail = { "kind": "Python",
                                "name": details["name"],
                                "factory": details["factory"],
                                "bundle-id": details["bundle_id"],
                                "state": ipopo_state_to_str(details["state"]),
                                "services": [ str(svc_ref) for svc_ref in details["services"].values() ],
                                "dependencies": [ {dep : { "specification": details["dependencies"][dep]["specification"],                                                            
                                                           "optional": details["dependencies"][dep]["optional"],
                                                           "aggregate": details["dependencies"][dep]["aggregate"],
                                                           "handler": details["dependencies"][dep]["handler"],
                                                           "filter": "not showed here!",
                                                           "bindings": [ str(binding) for binding in details["dependencies"][dep]["bindings"] ]
                                                         } 
                                                  } for dep in details["dependencies"] 
                                                ],
                                "properties" : details["properties"],
                                "error-trace" : details["error_trace"]
                              }
            # instance properties
            return json.dumps(instance_detail)
        else:
            return json.dumps({})

    def get_services(self):
        """
        Returns the list of services in the local isolate
        """
        result = []
        for svc_ref in self._context.get_all_service_references(None, None):
            s_id = svc_ref.get_property(pelix.constants.SERVICE_ID)
            s_ranking = svc_ref.get_property(pelix.constants.SERVICE_RANKING)
            s_specs = svc_ref.get_property(pelix.constants.OBJECTCLASS)
            bundle = svc_ref.get_bundle()
            s_bundle_id = bundle.get_bundle_id()
            s_bundle_name = bundle.get_symbolic_name()            
            result.append(
                {
                    "id" : s_id, 
                     "ranking": s_ranking, 
                     "specifications": s_specs,
                     "bundle": {
                         "id" : s_bundle_id,
                         "name" : s_bundle_name
                     }       
                }
            )            
            """
            for key, value in svc_ref.get_properties().items():
                lines.append('<dt>{0}</dt>\n<dd>{1}</dd>'.format(key, value))
            lines.append('</dl></td>')
            """
        return json.dumps(result)
        
    def get_threads(self):
        """
        Returns the list of current threads of the isolate
        """
        result = []
        current_id = threading.current_thread().ident
        for thread_id, stack in sys._current_frames().items():
            if thread_id == current_id:
                current = True
            else:
                current = False   
            th = {
                    "id" : thread_id,                    
                    "stack": {},
                    "current": current
                 }
            index = 1
            for filename, lineno, name, line in traceback.extract_stack(stack):
                th["stack"][str(index)] = {}
                th["stack"][str(index)]["filename"] = filename
                th["stack"][str(index)]["lineno"] = lineno
                th["stack"][str(index)]["name"] = name
                th["stack"][str(index)]["line"] = line
                index += 1
            result.append(th)
        return json.dumps(result)
        
    def get_isolate_logs(self):
        isolate = json.loads(self.get_isolate_detail())
        if isolate is not None:            
            cohorte_base = isolate["cohorte.base"]
            if isolate["cohorte.isolate.kind"] == "forker":
                # this is a forker. returns only "000" which references var/forker.log file                
                path = os.path.join(cohorte_base, "var", "forker.log")
                if os.path.exists(path):
                    ct = time.ctime(os.path.getmtime(path))
                    ct_parser = time.strptime(ct)                                     
                    return json.dumps({"kind": isolate["cohorte.isolate.kind"] , "level": "INFO", "log-files": [{"000": time.strftime("%Y%m%d-%H%M%S", ct_parser)}]})
                else:
                    return json.dumps({"kind": isolate["cohorte.isolate.kind"] , "level": "INFO", "log-files": []})
            else:
                isolate_uid = isolate["cohorte.isolate.uid"]
                isolate_name = isolate["cohorte.isolate.name"]
                path = os.path.join(cohorte_base, "var", isolate_name)
                result = []
                if os.path.exists(path):
                    # lists the sub-directories and returens 3 first letters of their names
                    log_dirs = os.listdir(path)
                    for idx, ldir in enumerate(log_dirs):                        
                        path2 = os.path.join(path, str(ldir))                        
                        if os.path.isdir(path2):     
                            toadd =  ldir[0:3]  
                            ct = time.ctime(os.path.getctime(path))
                            ct_parser = time.strptime(ct)          
                            result.append({toadd: time.strftime("%Y-%m-%dT%H:%M:%S", ct_parser)})                                                      
                return json.dumps({"kind": isolate["cohorte.isolate.kind"] , "level": "INFO", "log-files": result})
    
    def get_isolate_log(self, log_id):
        isolate = json.loads(self.get_isolate_detail())
        if isolate is not None:            
            cohorte_base = isolate["cohorte.base"]
            if isolate["cohorte.isolate.kind"] == "forker":                
                path = os.path.join(cohorte_base, "var", "forker.log")                
            else:
                isolate_uid = isolate["cohorte.isolate.uid"]
                isolate_name = isolate["cohorte.isolate.name"]
                path = os.path.join(cohorte_base, "var", isolate_name, 
                                 log_id + "-" + isolate_uid, "log_" + isolate_name + ".log")
            with open (path, "r") as forker_log:
                log=forker_log.read()
                return json.dumps({"content": log})
                                 
        return json.dumps("")
    
    def get_isolate_directory(self):        
        return self._directory.dump()

    def get_isolate_accesses(self):
        accesses = self._directory.get_local_peer().get_accesses()
        result = {}
        for access in accesses:
            result[access] = self._directory.get_local_peer().get_access(access).dump()            
        return json.dumps(result)
    
    def set_isolate_logs_level(self, level):
        return json.dumps({"old_log_level": "ALL", "new_log_level": "INFO"})

    def herald_message(self, herald_svc, message):
        """
        Called by Herald when a message is received
        """
        subject = message.subject
        reply = None

        if subject == SUBJECT_GET_ISOLATE_DETAIL:
            reply = self.get_isolate_detail()
        elif subject == SUBJECT_GET_BUNDLES:
            reply = self.get_bundles()
        elif subject == SUBJECT_GET_BUNDLE_DETAIL:
            bundle_number = message.content
            reply = self.get_bundle_detail(bundle_number)    
        elif subject == SUBJECT_GET_FACTORIES:
            reply = self.get_factories()
        elif subject == SUBJECT_GET_FACTORY_DETAIL:
            factory_name = message.content
            reply = self.get_factory_detail(factory_name)
        elif subject == SUBJECT_GET_INSTANCES:
            reply = self.get_instances()        
        elif subject == SUBJECT_GET_INSTANCE_DETAIL:
            instance_name = message.content
            reply = self.get_instance_detail(instance_name)            
        elif subject == SUBJECT_GET_SERVICES:
            reply = self.get_services()
        elif subject == SUBJECT_GET_THREADS:
            reply = self.get_threads()
        elif subject == SUBJECT_GET_ISOLATE_LOGS:
            reply = self.get_isolate_logs()
        elif subject == SUBJECT_GET_ISOLATE_LOG:
            log_id = message.content
            reply = self.get_isolate_log(log_id)
        elif subject == SUBJECT_GET_ISOLATE_DIRECTORY:            
            reply = self.get_isolate_directory()
        elif subject == SUBJECT_GET_ISOLATE_ACCESSES:            
            reply = self.get_isolate_accesses()
        elif subject == SUBJECT_SET_ISOLATE_LOGS_LEVEL:
            level = message.content
            reply = self.set_isolate_logs_level(level)
        if reply is not None:
            herald_svc.reply(message, reply)
        else:
            herald_svc.reply(message, "No value!")

    @Validate
    def validate(self, context):
        """
        Component validated

        :param context: The bundle context
        """
        # Store the framework access
        self._context = context
        _logger.info("Debug agent Ready")

    @Invalidate
    def invalidate(self, context):
        """
        Component invalidated

        :param context: The bundle context
        """
        # Clear the framework access
        self._context = None
        _logger.info("Debug agent Gone")
