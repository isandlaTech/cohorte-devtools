#!/usr/bin/python
# -- Content-Encoding: UTF-8 --
"""
Cohorte Debug REST API

:authors: Bassem Debbabi
:copyright: Copyright 2015, isandlaTech
:license:  Apache Software License 2.0

HISTORY
2016/08/08: API V2
    - Adding get isolate directory (herald directory) function

"""

# iPOPO decorators
import cohorte.composer
import cohorte.monitor
import hashlib
import json, time, os, uuid
import logging
from pelix.ipopo.decorators import ComponentFactory, Provides, Property, Instantiate, \
    Validate, Invalidate, Requires, RequiresMap, Bind, BindField, UnbindField
import pelix.remote
import threading

import debug
import herald
import herald.beans as beans


# Herald
# Cohorte 
# Standard library
try:
    import Cookie
except ImportError:
    import http.cookies as Cookie

try:
    # Python 3
    import urllib.parse as urlparse

except ImportError:
    # Python 2
    import urlparse

# cohorte plutform debug agent and api

_logger = logging.getLogger("debug.debug")

# collecting information 
SUBJECT_GET_HTTP = "cohorte/shell/agent/get_http"

# API path
DEBUG_REST_API_PATH = "debug/api/v2"

# API Version
DEBUG_REST_API_VERSION = "v2"

PROP_USERNAME = "username"

PROP_PASSWORD = "password"

PROP_SESSION_TIMEOUT = "session.timeout"

# ------------------------------------------------------------------------------------

class SetEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, set):
            return list(obj)
        return json.JSONEncoder.default(self, obj)

# ------------------------------------------------------------------------------------

@ComponentFactory("cohorte-admin-api-factory")
@Provides(['pelix.http.servlet', 'cohorte.admin.api'])
@Property('_path', 'pelix.http.path', "/debug")\

@Requires("_agent", debug.SERVICE_DEBUG)
# Consume a single Herald Directory service
@Requires("_directory", herald.SERVICE_DIRECTORY)
@Requires('_herald', herald.SERVICE_HERALD)
# Consume an Isolate Composer service
@RequiresMap("_icomposers", cohorte.composer.SERVICE_COMPOSER_ISOLATE, 'endpoint.framework.uuid',
             optional=True, allow_none=False)
@Requires("_icomposerlocal", cohorte.composer.SERVICE_COMPOSER_ISOLATE,
          optional=True, spec_filter="(!(service.imported=*))")
@Requires("_composer_top", cohorte.composer.SERVICE_COMPOSER_TOP)
@Requires("_isolates", cohorte.composer.SERVICE_COMPOSER_ISOLATE, aggregate=True, optional=True)
@Property('_reject', pelix.remote.PROP_EXPORT_REJECT, ['pelix.http.servlet', 'cohorte.admin.api'])
@Property('_username', PROP_USERNAME, 'admin')
@Property('_password', PROP_PASSWORD, 'admin')
@Property('_sessions_timeout', PROP_SESSION_TIMEOUT, 120000)
class DebugAPI(object):
    """
    A Component that provides the REST Admin API
    """

    def __init__(self):

        # lock
        self._lock = threading.Lock()

        # servlet's path
        self._path = None

        # cohorte platform debug agent
        self._agent = None

        # herald directory service
        self._directory = None
        self._herald = None

        # composer services
        self._icomposers = {}
        self._icomposerlocal = None
        self._composer_top = None
        self._isolates = []
        
        # properties
        self._username = None
        self._password = None
        
        # List of platform activities
        self._platform_activities = []
        self._platform_activities_index = 0

        # a Map of last updated lists
        self._last_updates = {}
        time_now = time.time()
        self._last_updates["nodes"] = time_now
        self._last_updates["platform_activities"] = time_now

        # local infos
        self._version_json = None
        
        # sessions
        # uuid -> {"user":"admin", "last-activity": "123454444323"}
        self._sessions = {}
        # in muliseconds
        self._sessions_timeout = 1 * 60 * 1000

    def decrypt_request(self, request, action="GET"):
        """
        Decrypts the request and extracts these information:

        :return path: full path without host:port (first and last / are removed)
        :return parts: list of query parts
        :return in_data: json object of the associated request data
        """
        o = urlparse.urlparse(request.get_path())
        path = o.path
        query = o.query

        # prepare query path: remove first and last '/' if exists
        if path[0] == '/':
            path = path[1:]
        if path[-1] == '/':
            path = path[:-1]
        parts = str(path).split('/')
        in_data = None
        if action == "GET":
            in_data = urlparse.parse_qs(query, keep_blank_values=True)
        else:
            data = request.read_data()
            if data != None:                                
                indata = data.decode('UTF-8')              
                in_data = json.loads(str(indata))
            else:
                in_data = urlparse.parse_qs(query, keep_blank_values=True)

        # print(json.dumps(in_data, sort_keys=False, indent=4, separators=(',', ': ')))
        return (path, parts, in_data)

    def prepare_response(self, request, action):
        data = {"meta": {}}
        data["meta"]["status"] = 200
        data["meta"]["msg"] = "OK"
        data["meta"]["api-version"] = DEBUG_REST_API_VERSION
        data["meta"]["api-method"] = ""
        data["meta"]["cohorte-version"] = self._get_cohorte_version()
        data["meta"]["request-path"] = request.get_path()
        data["meta"]["request-method"] = action
        data["meta"]["duration"] = 0.0
        return data

    def send_json(self, data, response):
        result = json.dumps(data, sort_keys=False,
                            indent=4, separators=(',', ': '),
                            cls=SetEncoder)
        response.send_content(data["meta"]["status"], result, "application/json")
    	
    def send_text(self, data, response, status):
        response.send_content(status, data, "text/plain")
        
    def bad_request(self, request, response, in_data, out_data, msg=None):
        out_data["meta"]["status"] = 400
        if msg:
            out_data["meta"]["msg"] = "BAD REQUEST: " + msg
        else:
            out_data["meta"]["msg"] = "BAD REQUEST"

    def internal_server_error(self, request, response, in_data, out_data, msg=None):
        out_data["meta"]["status"] = 500
        if msg:
            out_data["meta"]["msg"] = "INTERNAL SERVER ERROR: " + msg
        else:
            out_data["meta"]["msg"] = "INTERNAL SERVER ERROR"

    """
    GET actions ========================================================================
    """

    def get_auth_info(self, request, response, in_data, out_data, session_id):                            
        out_data["auth"] = {
            "session-id": session_id,
            "session-user": self._sessions[session_id]["user"],
            "session-timeout": self._sessions_timeout,
            }        
                        
    def get_api_info(self, request, response, in_data, out_data):
        out_data["api"] = {"name": "debug"} 

    def get_platform_details(self, request, response, in_data, out_data):
        out_data["platform"] = {}        
        out_data["platform"]["cohorte-version"] = self._get_cohorte_version()

    def get_application_details(self, request, response, in_data, out_data):
        out_data["application"] = {}        
        out_data["application"]["id"] = self._get_application_id()
        out_data["application"]["name"] = self._get_application_name()

    def get_application_composition(self, request, response, in_data, out_data):
        out_data["application"] = {}        
        out_data["application"]["id"] = self._get_application_id()
        out_data["application"]["name"] = self._get_application_name()
        out_data["application"]["composition"] = self._get_application_composition()

    def get_isolates(self, request, response, in_data, out_data):
        out_data["isolates"] = []
        lp = self._directory.get_local_peer()
        out_data["isolates"].append({"uid": lp.uid, "name": lp.name,
                                     "node_uid": lp.node_uid, "node_name": lp.node_name})
        count = 1
        for p in self._directory.get_peers():
            out_data["isolates"].append({"uid": p.uid, "name": p.name,
                                         "node_uid": p.node_uid, "node_name": p.node_name})
            count += 1         
        out_data["meta"]["count"] = count

    def get_isolate(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = self._get_isolate_detail(uuid)
        
    def get_isolate_bundles(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        bundles = self._get_isolate_bundles(uuid)
        out_data["bundles"] = bundles
        if bundles is not None:
            count = len(bundles)
        else:
            count = 0        
        out_data["meta"]["count"] = count
    
    def get_bundle_detail(self, request, response, in_data, out_data, isolate_uuid, bundle_id):
        out_data["isolate"] = {"uuid" : isolate_uuid}
        out_data["bundle"] = {}
        out_data["bundle"] = self._get_bundle_detail(isolate_uuid, bundle_id)   
            
    def get_isolate_factories(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        factories = self._get_isolate_factories(uuid)
        out_data["factories"] = factories
        if factories is not None:
            count = len(factories)
        else:
            count = 0        
        out_data["meta"]["count"] = count

    def get_factory_detail(self, request, response, in_data, out_data, isolate_uuid, factory_name):
        out_data["isolate"] = {"uuid" : isolate_uuid}
        out_data["factory"] = {}
        out_data["factory"] = self._get_factory_detail(isolate_uuid, factory_name) 

    def get_isolate_instances(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}                
        instances = self._get_isolate_instances(uuid)
        out_data["instances"] = instances        
        if instances is not None:
            count = len(instances)
        else:
            count = 0
        out_data["meta"]["count"] = count
    
    def get_instance_detail(self, request, response, in_data, out_data, isolate_uuid, instance_name):
        out_data["isolate"] = {"uuid" : isolate_uuid}
        out_data["instance"] = {}
        out_data["instance"] = self._get_instance_detail(isolate_uuid, instance_name)         
        
    def get_isolate_services(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        services = self._get_isolate_services(uuid)
        out_data["services"] = services
        if services is not None:
            count = len(services)
        else:
            count = 0
        out_data["meta"]["count"] = count

    def get_isolate_threads(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        threads = self._get_isolate_threads(uuid)
        out_data["threads"] = threads
        if threads is not None:
            count = len(threads)
        else:    
            count = 0        
        out_data["meta"]["count"] = count
        
    def get_isolate_logs(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        logs = self._get_isolate_logs(uuid)
        out_data["logs"] = logs
        if logs is not None:
            count = len(logs)
        else:
            count = 0        
        out_data["meta"]["count"] = count

    def get_isolate_log(self, request, response, in_data, out_data, isolate_uuid, log_id):
        out_data["isolate"] = {"uuid" : isolate_uuid}        
        out_data["log"] = self._get_isolate_log(isolate_uuid, log_id)        

    def get_isolate_directory(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        directory = self._get_isolate_directory(uuid)
        out_data["directory"] = directory  

    def get_isolate_accesses(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}
        accesses = self._get_isolate_accesses(uuid)
        out_data["accesses"] = accesses        


    """
    POST actions ========================================================================
    """

    def auth_login(self, request, response, in_data, out_data):
        username = in_data["username"]
        password = in_data["password"]
        rediect = in_data["redirect"]
        if self.check_credentials(username, password):
            # create new session
            session_id = str(uuid.uuid1())
            # session creation time in seconds
            current_time = int(round(time.time() * 1000))
            self._sessions[session_id] = {"user": username, "last-activity": current_time}        
            out_data["login"] = {"redirect": rediect, "session": session_id}
        else:
            out_data["meta"]["status"] = 401
            out_data["meta"]["msg"] = "Unauthorized"    
        
        # get session number
        # if exists, check if not timeout
        # else, check credentials and creates new session id
        
    def auth_logout(self, request, response, in_data, out_data):
        session = None
        rediect = None
        if "session" in in_data:
            session = in_data["session"]
        if "redirect" in in_data:
            rediect = in_data["redirect"]
        out_data["logout"] = {"redirect": rediect}     

    def set_isolate_logs_level(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {"uuid" : uuid}        
        level = in_data["logLevel"]
        logs_level = self._set_isolate_logs_level(uuid, level)        
        if logs_level:
            out_data["logs"] = logs_level
        else:
            self.internal_server_error(request, response, in_data, out_data, "Cannot change log level!")
        

    """
    Internal api methods ===========================================================================
    """
    
    def check_credentials(self, username, password):
        if username and password:
            if self._username == username:
                if self._password.startswith("hash:"):
                    hashed_password = self._password[5:]
                    hashed_user_pass_obj = hashlib.md5(password.encode('UTF-8'))
                    hashed_user_pass = hashed_user_pass_obj.hexdigest()
                    return hashed_password == hashed_user_pass
                else:
                    return self._password == password                        
        return False
    
    def check_session_timeout(self, request, response, in_data, out_data, session_id, update=True):
        if session_id in self._sessions:
            current_time = int(round(time.time() * 1000))
            session_time = self._sessions[session_id]["last-activity"]            
            if current_time - session_time < self._sessions_timeout:
                # update session time
                if update == True:
                    self._sessions[session_id]["last-activity"] = current_time
                return False
        return True 
    
    """
    Internal agent methods ===========================================================================
    """

    def _get_isolate_detail(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate   
            try:       
                msg = beans.Message(debug.agent.SUBJECT_GET_ISOLATE_DETAIL)
                reply = self._herald.send(uuid, msg)
                return json.loads(reply.content)
            except KeyError:
                return None
        else:
            # this is the local isolate
            return json.loads(self._agent.get_isolate_detail())

    def _get_isolate_bundles(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_BUNDLES)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_bundles())
    
    def _get_bundle_detail(self, uuid, bundle_id):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_BUNDLE_DETAIL, bundle_id)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_bundle_detail(bundle_id))
                
    def _get_isolate_factories(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_FACTORIES)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_factories())

    def _get_factory_detail(self, uuid, factory_name):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_FACTORY_DETAIL, factory_name)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_factory_detail(factory_name))
            
    def _get_isolate_instances(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_INSTANCES)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_instances())

    def _get_instance_detail(self, uuid, instance_name):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_INSTANCE_DETAIL, instance_name)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_instance_detail(instance_name))

    def _get_isolate_services(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_SERVICES)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_services())

    def _get_isolate_threads(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_THREADS)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_threads())
    	
    def _get_isolate_logs(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_ISOLATE_LOGS)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_isolate_logs())
            
    def _get_isolate_log(self, uuid, log_id):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_ISOLATE_LOG, log_id)
            reply = self._herald.send(uuid, msg)
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_isolate_log(log_id))

    def _get_isolate_directory(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_ISOLATE_DIRECTORY)
            reply = self._herald.send(uuid, msg)
            return reply.content
        else:
            # this is the local isolate
            return self._agent.get_isolate_directory()

    def _get_isolate_accesses(self, uuid):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_GET_ISOLATE_ACCESSES)
            reply = self._herald.send(uuid, msg)            
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.get_isolate_accesses())

    def _set_isolate_logs_level(self, uuid, level):
        lp = self._directory.get_local_peer()
        if lp.uid != uuid:  
            # this is another isolate          
            msg = beans.Message(debug.agent.SUBJECT_SET_ISOLATE_LOGS_LEVEL, level)
            reply = self._herald.send(uuid, msg)            
            return json.loads(reply.content)
        else:
            # this is the local isolate
            return json.loads(self._agent.set_isolate_logs_level(level))
        # Not yet implemented in Python
        return None

    """
    Internal api methods ===========================================================================
    """

    def _get_cohorte_version_details(self):
        if not self._version_json:
            conf_dir = os.path.join(self._context.get_property("cohorte.home"), "conf")
            file_name = os.path.join(conf_dir, "version.js")        
            with open(file_name, "r") as version_json_file:
                self._version_json = json.load(version_json_file)
        return self._version_json

    def _get_cohorte_version(self):
        version = self._get_cohorte_version_details()
        return "{0}_{1}_{2}".format(version["version"], version["timestamp"], version["stage"]) 

    def _get_application_id(self):        
        app_id = self._context.get_property("herald.application.id")
        return app_id

    def _get_application_name(self):        
        comp = self._get_application_composition()
        return comp["name"]

    def _get_application_composition(self):
        return self._composer_top.get_composition_json()    

    """
    Servlet (url mapping to rest api) ================================================================
    """

    def do_GET(self, request, response):
        """
        Handle a GET
        """
        path, parts, in_data = self.decrypt_request(request)

        out_data = self.prepare_response(request, "GET")
        
        # check session
        cookies = request.get_header("Cookie")
        if cookies:
            cookie = Cookie.SimpleCookie()
            cookie.load(cookies)
            session_id = cookie["session"].value
            if session_id:
                out_data["meta"]["session"] = session_id
                if self.check_session_timeout(request, response, in_data, out_data, session_id) == False:                    
                    # valid session                
                    if path.startswith(DEBUG_REST_API_PATH):
                        if path.startswith(DEBUG_REST_API_PATH + "/auth"):
                            out_data["meta"]["api-method"] = "get_auth_info"
                            self.get_auth_info(request, response, in_data, out_data, session_id)
                        elif path == DEBUG_REST_API_PATH:                            
                            out_data["meta"]["api-method"] = "get_api_info"            
                            self.get_api_info(request, response, in_data, out_data)
                        elif path == DEBUG_REST_API_PATH + "/platform":
                            out_data["meta"]["api-method"] = "get_platform_details"
                            self.get_platform_details(request, response, in_data, out_data)
                        elif path == DEBUG_REST_API_PATH + "/application":
                            out_data["meta"]["api-method"] = "get_application_details"
                            self.get_application_details(request, response, in_data, out_data)    
                        elif path == DEBUG_REST_API_PATH + "/isolates":
                            out_data["meta"]["api-method"] = "get_isolates"
                            self.get_isolates(request, response, in_data, out_data)

                        elif len(parts) == 5:    
                            if path == DEBUG_REST_API_PATH + "/application/composition":
                                out_data["meta"]["api-method"] = "get_application_composition"
                                self.get_application_composition(request, response, in_data, out_data)
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4]:
                                out_data["meta"]["api-method"] = "get_isolate"
                                self.get_isolate(request, response, in_data, out_data, parts[4])
                            else:
                                self.bad_request(request, response, in_data, out_data)
                        
                        elif len(parts) == 6:
                            if path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/bundles":
                                out_data["meta"]["api-method"] = "get_isolate_bundles"
                                self.get_isolate_bundles(request, response, in_data, out_data, parts[4])
                            
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/factories":
                                out_data["meta"]["api-method"] = "get_isolate_factories"
                                self.get_isolate_factories(request, response, in_data, out_data, parts[4])
                                        
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/instances":
                                out_data["meta"]["api-method"] = "get_isolate_instances"
                                self.get_isolate_instances(request, response, in_data, out_data, parts[4])
                                    
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/services":
                                out_data["meta"]["api-method"] = "get_isolate_services"
                                self.get_isolate_services(request, response, in_data, out_data, parts[4])
                            
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/threads":
                                out_data["meta"]["api-method"] = "get_isolate_threads"
                                self.get_isolate_threads(request, response, in_data, out_data, parts[4])
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/logs":
                                out_data["meta"]["api-method"] = "get_isolate_logs"
                                self.get_isolate_logs(request, response, in_data, out_data, parts[4])
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/directory":
                                out_data["meta"]["api-method"] = "get_isolate_directory"
                                self.get_isolate_directory(request, response, in_data, out_data, parts[4])
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/accesses":
                                out_data["meta"]["api-method"] = "get_isolate_accesses"
                                self.get_isolate_accesses(request, response, in_data, out_data, parts[4])

                        elif len(parts) == 7:
                            if path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/bundles/" + parts[6]:
                                out_data["meta"]["api-method"] = "get_bundle_detail"
                                self.get_bundle_detail(request, response, in_data, out_data, parts[4], parts[6])
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/factories/" + parts[6]:
                                out_data["meta"]["api-method"] = "get_factory_detail"
                                self.get_factory_detail(request, response, in_data, out_data, parts[4], parts[6])
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/instances/" + parts[6]:
                                out_data["meta"]["api-method"] = "get_instance_detail"
                                self.get_instance_detail(request, response, in_data, out_data, parts[4], parts[6])
                            elif path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/logs/" + parts[6]:
                                if 'raw' in in_data:
                                    # send raw log
                                    log = self._get_isolate_log(parts[4], parts[6])
                                    self.send_text(log["content"], response, 200)
                                else:
                                    # send log within a json object data["log"]
                                    out_data["meta"]["api-method"] = "get_isolate_log"
                                    self.get_isolate_log(request, response, in_data, out_data, parts[4], parts[6])
                        else:
                            self.bad_request(request, response, in_data, out_data)

                    else:
                        self.bad_request(request, response, in_data, out_data)
                
                else:
                    # session timeout
                    out_data["meta"]["status"] = 401
                    out_data["meta"]["msg"] = "Unauthorized - session timeout!" 
            else:
                # session timeout
                out_data["meta"]["status"] = 401
                out_data["meta"]["msg"] = "Unauthorized - session cookie no provided!"
        else:
            # session timeout
            out_data["meta"]["status"] = 401
            out_data["meta"]["msg"] = "Unauthorized - request cookie not provided!" 
                
        self.send_json(out_data, response)

    
    def do_POST(self, request, response):
        """
        Handle a POST
        """
        path, parts, in_data = self.decrypt_request(request, "POST")

        out_data = self.prepare_response(request, "POST")

        if path.startswith(DEBUG_REST_API_PATH): 
            if path.startswith(DEBUG_REST_API_PATH + "/auth/login"):
                out_data["meta"]["api-method"] = "auth_login"
                self.auth_login(request, response, in_data, out_data)
            elif path.startswith(DEBUG_REST_API_PATH + "/auth/logout"):
                out_data["meta"]["api-method"] = "auth_logout"
                self.auth_logout(request, response, in_data, out_data)
            elif len(parts) == 7:
                if path == DEBUG_REST_API_PATH + "/isolates/" + parts[4] + "/logs/level":
                    out_data["meta"]["api-method"] = "set_isolate_logs_level"
                    # check session
                    cookies = request.get_header("Cookie")
                    if cookies:
                        cookie = Cookie.SimpleCookie()
                        cookie.load(cookies)
                        session_id = cookie["session"].value
                        if session_id:
                            out_data["meta"]["session"] = session_id
                            if self.check_session_timeout(request, response, in_data, out_data, session_id) == False: 
                                if 'logLevel' in in_data:                                           
                                    self.set_isolate_logs_level(request, response, in_data, out_data, parts[4])
                                else:
                                    self.bad_request(request, response, in_data, out_data, "no logLevel parameter provided!")
            else:
                self.bad_request(request, response, in_data, out_data)

        else:
            self.bad_request(request, response, in_data, out_data)

        self.send_json(out_data, response)

    """
	iPOPO STUFF --------------------------------------------------------------------------------------------------------
	"""

    @Validate
    def validate(self, context):
        _logger.info("Debug REST API validated")
        self._context = context


    @Invalidate
    def invalidate(self, context):
        _logger.info("Debug REST API invalidated")


    def bound_to(self, path, params):
        """
		Servlet bound to a path
		"""
        _logger.info('Bound to ' + path)
        return True

    def unbound_from(self, path, params):
        """
		Servlet unbound from a path
		"""
        _logger.info('Unbound from ' + path)
        return None
