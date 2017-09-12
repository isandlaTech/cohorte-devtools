#!/usr/bin/python
# -- Content-Encoding: UTF-8 --
"""
Cohorte Admin REST API

:authors: Bassem Debbabi
:copyright: Copyright 2015, isandlaTech
:license:  Apache Software License 2.0
"""

# iPOPO decorators
from pelix.ipopo.decorators import ComponentFactory, Provides, Property, Instantiate, \
    Validate, Invalidate, Requires, RequiresMap, Bind, BindField, UnbindField
import pelix.remote

# Herald
import herald
import herald.beans as beans

# Cohorte 
import cohorte.composer
import cohorte.monitor

# Standard library
import logging
import threading
import json, time, os

try:
    # Python 3
    import urllib.parse as urlparse

except ImportError:
    # Python 2
    import urlparse

_logger = logging.getLogger("admin.admin")

# collecting information 
SUBJECT_GET_HTTP = "cohorte/shell/agent/get_http"

# API path
ADMIN_REST_API_PATH = "admin/api/v2"

# VERSION
COHORTE_VERSION = "1.0.1"

"""
	TODO: should have a local cache of all information.
"""


@ComponentFactory("cohorte-admin-factory")
@Provides(['pelix.http.servlet', herald.SERVICE_DIRECTORY_LISTENER])
@Property('_path', 'pelix.http.path', "/admin")\
# Consume a single Herald Directory service
@Requires("_directory", herald.SERVICE_DIRECTORY)
@Requires('_herald', herald.SERVICE_HERALD)
# Consume an Isolate Composer service
@RequiresMap("_icomposers", cohorte.composer.SERVICE_COMPOSER_ISOLATE, 'endpoint.framework.uuid',
             optional=True, allow_none=False)
@Requires("_icomposerlocal", cohorte.composer.SERVICE_COMPOSER_ISOLATE,
          optional=True, spec_filter="(!(service.imported=*))")
@Requires("_isolates", cohorte.composer.SERVICE_COMPOSER_ISOLATE, aggregate=True, optional=True)
@Property('_reject', pelix.remote.PROP_EXPORT_REJECT, ['pelix.http.servlet', herald.SERVICE_DIRECTORY_LISTENER])
@Instantiate('cohorte-admin-api')
class Admin(object):
    """
    A Component that provides the REST Admin API
    """

    def __init__(self):

        # lock
        self._lock = threading.Lock()

        # servlet's path
        self._path = None

        # herald directory service
        self._directory = None
        self._herald = None
        # isolate composer service
        self._icomposers = {}
        self._icomposerlocal = None
        self._isolates = []

        # List of platform activities
        self._platform_activities = []
        self._platform_activities_index = 0

        # a Map of last updated lists
        self._last_updates = {}
        time_now = time.time()
        self._last_updates["nodes"] = time_now
        self._last_updates["platform_activities"] = time_now

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
                in_data = json.loads(str(data))
            else:
                in_data = urlparse.parse_qs(query, keep_blank_values=True)

        #print(json.dumps(in_data, sort_keys=False, indent=4, separators=(',', ': ')))
        return (path, parts, in_data)

    def prepare_response(self, request, action):
        data = {"meta": {}}
        data["meta"]["status"] = 200
        data["meta"]["msg"] = "OK"
        data["meta"]["api-version"] = "v2"
        data["meta"]["api-method"] = ""
        data["meta"]["cohorte-version"] = COHORTE_VERSION
        data["meta"]["request-path"] = request.get_path()
        data["meta"]["request-method"] = action
        data["meta"]["duration"] = 0.0
        return data

    def send_json(self, data, response):
        result = json.dumps(data, sort_keys=False,
                            indent=4, separators=(',', ': '))
        response.send_content(data["meta"]["status"], result, "application/json")

    def bad_request(self, request, response, in_data, out_data):
        out_data["meta"]["status"] = 400
        out_data["meta"]["msg"] = "BAD REQUEST"

    """
    GET actions ========================================================================
    """

    def get_platform(self, request, response, in_data, out_data):
        out_data["platform"] = {}

    def get_platform_activities(self, request, response, in_data, out_data):
        out_data["platform"] = {}
        out_data["activities"] = []
        count = 0

        for activity in self._platform_activities:
            out_data["activities"].append(activity)
            count = count + 1

        out_data["meta"]["count"] = count
        out_data["meta"]["lastupdate"] = self._last_updates["platform_activities"]

    def get_platform_composition(self, request, response, in_data, out_data):
        out_data["platform"] = {}
        out_data["composition"] = {"name": "hello"}

    def get_nodes(self, request, response, in_data, out_data):
        out_data["nodes"] = []
        lp = self._directory.get_local_peer()
        out_data["nodes"].append({"uid": lp.node_uid, "name": lp.node_name})
        count = 1
        for p in self._directory.get_peers():
            # if nodes["nodes"]["uid"] is None:
            found = False
            for i in out_data["nodes"]:
                if i["uid"] == p.node_uid:
                    found = True
            if found == False:
                out_data["nodes"].append({"uid": p.node_uid, "name": p.node_name})
                count += 1

        out_data["meta"]["lastupdate"] = self._last_updates["nodes"]
        out_data["meta"]["count"] = count


    def get_node(self, request, response, in_data, out_data, uuid):
        out_data["node"] = {}        
        out_data["node"]["uuid"] = uuid
        out_data["node"]["status"] = "RUNNING"
        lp = self._directory.get_local_peer()
        count = 0
        if lp.node_uid == uuid:
            out_data["node"]["name"] = lp.node_name
            count = 1
        for p in self._directory.get_peers():
            if p.node_uid == uuid:
                out_data["node"]["name"] = p.node_name
                count += 1
        out_data["node"]["nbr_isolates"] = count


    def get_node_isolates(self, request, response, in_data, out_data, uuid):
        out_data["node"] = {}
        out_data["node"]["uuid"] = uuid
        out_data["isolates"] = []
        
        lp = self._directory.get_local_peer()
        count = 0
        if lp.node_uid == uuid:
            out_data["node"]["name"] = lp.node_name
            if lp.name == "cohorte.internals.forker":
                itype = "cohorte-isolate"
            else:
                itype = "app-dynamic-isolate"
            out_data["isolates"].append({"uid": lp.uid, "name": lp.name, "type": itype})
            count = 1
        for p in self._directory.get_peers():
            if p.node_uid == uuid:
                if p.name == "cohorte.internals.forker":
                    itype = "cohorte-isolate"
                else:
                    itype = "app-dynamic-isolate"
                out_data["isolates"].append({"uid": p.uid, "name": p.name, "type": itype})
                count += 1        
        out_data["node"]["nbr_isolates"] = count
        out_data["meta"]["count"] = count

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
        out_data["isolate"] = {}
        out_data["isolate"]["type"] = "app-dynamic-isolate"
        lp = self._directory.get_local_peer()
        if lp.uid == isolate_uid:
            out_data["isolate"]["name"] = lp.name
            out_data["isolate"]["node_uid"] = lp.node_uid
            out_data["isolate"]["node_name"] = lp.node_name
            out_data["isolate"]["http_port"] = self.get_isolate_http_port(isolate_uid)
            try:                
                http_access = self._directory.get_local_peer().get_access("http").host
                if http_access.startswith("::ffff:"):
                    http_access = http_access[7:]
            except KeyError:
                http_access = ""
            out_data["isolate"]["http_access"] = http_access

            out_data["isolate"]["shell_port"] = 0
            if lp.name == "cohorte.internals.forker":
                out_data["isolate"]["type"] = "cohorte-isolate"
        else:
            for p in self._directory.get_peers():
                if p.uid == isolate_uid:
                    out_data["isolate"]["name"] = p.name
                    out_data["isolate"]["node_uid"] = p.node_uid
                    out_data["isolate"]["node_name"] = p.node_name
                    out_data["isolate"]["http_port"] = self.get_isolate_http_port(isolate_uid)
                    try:
                        http_access = p.get_access("http").host
                        if http_access.startswith("::ffff:"):
                            http_access = http_access[7:]
                    except KeyError:
                        http_access = None
                    out_data["isolate"]["http_access"] = http_access
                    out_data["isolate"]["shell_port"] = 0
                    if p.name == "cohorte.internals.forker":
                        out_data["isolate"]["type"] = "cohorte-isolate"
                    break

        if out_data["isolate"]["type"] == "cohorte-isolate":
            out_data["isolate"]["nbr_components"] = -1
        else:
            count = 0
            try:
                for c in self._icomposers.keys():
                    if self._icomposers.get(c).get_isolate_uid() == isolate_uid:
                        comps = self._icomposers.get(c).get_isolate_info().components
                        for com in comps:
                            count += 1
                if self._icomposerlocal is not None:            
                    if self._icomposerlocal.get_isolate_uid() == isolate_uid:
                        for c in self._icomposerlocal.get_isolate_info().components:
                            count += 1
            except:
                pass
            out_data["isolate"]["nbr_components"] = count

        out_data["meta"]["isolate"] = isolate_uid
        out_data["meta"]["code"] = 200

    def get_isolate_components(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {}
        out_data["components"] = []

    def get_components(self, request, response, in_data, out_data):
        out_data["components"] = []

    def get_component(self, request, response, in_data, out_data, uuid):
        out_data["component"] = {}



    """
    DELETE actions =====================================================================
    """

    def delete_platform(self, request, response, in_data, out_data):
        out_data["platform"] = {}

    def delete_platform_activities(self, request, response, in_data, out_data):
        out_data["platform"] = {}
        out_data["activities"] = []

    def delete_nodes(self, request, response, in_data, out_data):
        out_data["nodes"] = []


    def delete_node(self, request, response, in_data, out_data, uuid):
        out_data["node"] = {}


    def delete_node_isolates(self, request, response, in_data, out_data, uuid):
        out_data["node"] = {}
        out_data["isolates"] = []

    def delete_isolates(self, request, response, in_data, out_data):
        out_data["isolates"] = []

    def delete_isolate(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {}

    def delete_isolate_components(self, request, response, in_data, out_data, uuid):
        out_data["isolate"] = {}
        out_data["components"] = []

    def delete_components(self, request, response, in_data, out_data):
        out_data["components"] = []

    def delete_component(self, request, response, in_data, out_data, uuid):
        out_data["component"] = {}


    """
    Listeners --------------------------------------------------------------------------------------------------------
    """

    def peer_registered(self, peer):
        with self._lock:
            time_now = time.time()
            self._last_updates["nodes"] = time_now
            self._last_updates["platform_activities"] = time_now
            self._platform_activities_index += 1
            self._platform_activities.append({
                    "order": self._platform_activities_index,
                    "timestamp": str(time.strftime("%Y-%m-%d %H:%M:%S")),
                    "event" : "Isolate Created",
                    "object": "Isolate",
                    "name" : peer.name,
                    "uuid" : peer.uid,
                    "node" : peer.node_name,
                    "info" : ""
                })

    def peer_updated(self, peer, access_id, data, previous):
        pass

    def peer_unregistered(self, peer):
        with self._lock:
            time_now = time.time()
            self._last_updates["nodes"] = time_now
            self._last_updates["platform_activities"] = time_now
            self._platform_activities_index += 1
            self._platform_activities.append({
                    "order": self._platform_activities_index,
                    "timestamp": str(time.strftime("%Y-%m-%d %H:%M:%S")),
                    "event" : "Isolate Lost",
                    "object": "Isolate",
                    "name" : peer.name,
                    "uuid" : peer.uid,
                    "node" : peer.node_name,
                    "info" : ""
                })

    """
    Servlet (url mapping to rest api) ================================================================
    """

    def do_GET(self, request, response):
        """
        Handle a GET
        """
        path, parts, in_data = self.decrypt_request(request)

        out_data = self.prepare_response(request, "GET")

        if path.startswith(ADMIN_REST_API_PATH):

            # Platform API
            if path == ADMIN_REST_API_PATH + "/platform":
                out_data["meta"]["api-method"] = "get_platform"
                self.get_platform(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/platform/activities":
                """
                path data:
                {
                    "rang": "1..10", # possible values: *..* (if 0..0 return only associated meta-data)
                    "filter": "(node=led-raspberry)"
                }
                """
                out_data["meta"]["api-method"] = "get_platform_activities"
                self.get_platform_activities(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/platform/composition":
                out_data["meta"]["api-method"] = "get_platform_composition"
                self.get_platform_composition(request, response, in_data, out_data)
            # Nodes API
            elif path == ADMIN_REST_API_PATH + "/nodes":
                out_data["meta"]["api-method"] = "get_nodes"
                self.get_nodes(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/components":
                out_data["meta"]["api-method"] = "get_components"
                self.get_components(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/isolates":
                out_data["meta"]["api-method"] = "get_isolates"
                self.get_isolates(request, response, in_data, out_data)

            elif len(parts) >= 5:
                if path == ADMIN_REST_API_PATH + "/nodes/" + parts[4]:
                    out_data["meta"]["api-method"] = "get_node"
                    self.get_node(request, response, in_data, out_data, parts[4])
                elif path == ADMIN_REST_API_PATH + "/nodes/" + parts[4] + "/isolates":
                    out_data["meta"]["api-method"] = "get_node_isolates"
                    self.get_node_isolates(request, response, in_data, out_data, parts[4])

                elif path == ADMIN_REST_API_PATH + "/isolates/" + parts[4]:
                    out_data["meta"]["api-method"] = "get_isolate"
                    self.get_isolate(request, response, in_data, out_data, parts[4])
                elif path == ADMIN_REST_API_PATH + "/isolates/" + parts[4] + "/components":
                    out_data["meta"]["api-method"] = "get_isolate_components"
                    self.get_isolate_components(request, response, in_data, out_data, parts[4])

                elif path == ADMIN_REST_API_PATH + "/components/" + parts[4]:
                    out_data["meta"]["api-method"] = "get_component"
                    self.get_component(request, response, in_data, out_data, parts[4])
                else:
                    self.bad_request(request, response, in_data, out_data)
            else:
                self.bad_request(request, response, in_data, out_data)

        else:
            self.bad_request(request, response, in_data, out_data)

        self.send_json(out_data, response)

    def do_delete(self, request, response):
        """
		Handle Delete actions
		"""
        path, parts, in_data = self.decrypt_request(request)

        out_data = self.prepare_response(request, "DELETE")

        if path.startswith(ADMIN_REST_API_PATH):

            # Platform API
            if path == ADMIN_REST_API_PATH + "/platform":
                out_data["meta"]["api-method"] = "delete_platform"
                self.delete_platform(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/platform/activities":
                out_data["meta"]["api-method"] = "delete_platform_activities"
                self.delete_platform_activities(request, response, in_data, out_data)

            # Nodes API
            elif path == ADMIN_REST_API_PATH + "/nodes":
                out_data["meta"]["api-method"] = "delete_nodes"
                self.delete_nodes(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/components":
                out_data["meta"]["api-method"] = "delete_components"
                self.delete_components(request, response, in_data, out_data)
            elif path == ADMIN_REST_API_PATH + "/isolates":
                out_data["meta"]["api-method"] = "delete_isolates"
                self.delete_isolates(request, response, in_data, out_data)

            elif len(parts) >= 5:
                if path == ADMIN_REST_API_PATH + "/nodes/" + parts[4]:
                    out_data["meta"]["api-method"] = "delete_node"
                    self.delete_node(request, response, in_data, out_data, parts[4])
                elif path == ADMIN_REST_API_PATH + "/nodes/" + parts[4] + "/isolates":
                    out_data["meta"]["api-method"] = "delete_node_isolates"
                    self.delete_node_isolates(request, response, in_data, out_data, parts[4])

                elif path == ADMIN_REST_API_PATH + "/isolates/" + parts[4]:
                    out_data["meta"]["api-method"] = "delete_isolate"
                    self.delete_isolate(request, response, in_data, out_data, parts[4])
                elif path == ADMIN_REST_API_PATH + "/isolates/" + parts[4] + "/components":
                    out_data["meta"]["api-method"] = "delete_isolate_components"
                    self.delete_isolate_components(request, response, in_data, out_data, parts[4])

                elif path == ADMIN_REST_API_PATH + "/components/" + parts[4]:
                    out_data["meta"]["api-method"] = "delete_component"
                    self.delete_component(request, response, in_data, out_data, parts[4])
                else:
                    self.bad_request(request, response, in_data, out_data)
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
        _logger.info("Admin REST API validated")
        self._context = context


    @Invalidate
    def invalidate(self, context):
        _logger.info("Admin REST API invalidated")


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