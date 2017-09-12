#!/usr/bin/python
# -- Content-Encoding: UTF-8 --
"""
Cohorte Web Admin Servlet 

:authors: Bassem Debbabi
:copyright: Copyright 2014, isandlaTech
:license:  Apache Software License 2.0
"""

# iPOPO decorators
import cohorte.composer
import cohorte.monitor
import json, time, os
import logging
from pelix.ipopo.decorators import ComponentFactory, Provides, Property, Instantiate, \
    Validate, Invalidate, Requires, RequiresMap, Bind, BindField, UnbindField
import pelix.remote
import threading 

import herald
import herald.beans as beans


# Herald
# Cohorte 
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

_logger = logging.getLogger("webadmin.webadmin")


# collecting information 

COOKIE = "Cookie"
COOKIE_SESSION = "session"

SUBJECT_GET_HTTP = "cohorte/shell/agent/get_http"
""" Signal to request the ports to access HTTP services """

# TODO manage in a better the dispatching of request. to don't have a lot of if 
# it will help to read the code


"""
    TODO: should have a local cache of all information.
"""

# Name the component factory
@ComponentFactory("cohorte-webadmin-factory")
@Provides(['pelix.http.servlet', herald.SERVICE_DIRECTORY_LISTENER])
@Property('_path', 'pelix.http.path', "/webadmin")
# Consume a single Herald Directory service
@Requires("_directory", herald.SERVICE_DIRECTORY)
@Requires('_herald', herald.SERVICE_HERALD)
# Consume an Isolate Composer service
@RequiresMap("_icomposers", cohorte.composer.SERVICE_COMPOSER_ISOLATE, 'endpoint.framework.uuid',
             optional=True, allow_none=False)
@Requires("_icomposerlocal", cohorte.composer.SERVICE_COMPOSER_ISOLATE,
          optional=True, spec_filter="(!(service.imported=*))")
@Requires("_isolates", cohorte.composer.SERVICE_COMPOSER_ISOLATE, aggregate=True, optional=True)
@Requires("_admin", 'cohorte.admin.api')
# Reject the export the servlet specification
@Property('_reject', pelix.remote.PROP_EXPORT_REJECT, ['pelix.http.servlet', herald.SERVICE_DIRECTORY_LISTENER])
@Instantiate('WebAdmin')
class WebAdmin(object):
    """
    A component that provides a web interface for check spelling words.
    """

    def __init__(self):
        """
        Defines class members
        """
        self._path = None
        # herald directory service
        self._directory = None
        self._herald = None
        # isolate composer service
        self._icomposers = {}
        self._icomposerlocal = None
        self._isolates = []
        # admin service
        self._admin = None
        # pooling related states
        self._nodes_list_lastupdate = None
        self._platform_activities_list_lastupdate = None
        self._isolates_list_lastupdate = {}
        self._components_list_lastupdate = {}
        self._tabs_list_lastupdate = None

        # Bundle context
        self._context = None

        # Gui options
        self._show_internal_isolates = False

        # List of platform activities
        self._platform_activities = []
        self._platform_activities_index = 0

        self._lock = threading.Lock()

    """
    API ----------------------------------------------------------------------------------------------------------------
    """
    def get_platform(self):
        """
        Gets informations about the running platform
        Return example:
        {
            "meta" : {
                "code": 200,
                "lastupdate" : "1411979225.65"
            },
            "platform": {
                "app-id": "led-isandlatech-demo",
                "cohorte-version": "1.0.1"
            }
        }
        """
        platform = {"meta": {}, "platform": {}}
        platform["meta"]["code"] = 200
        platform["meta"]["lastupdate"] = time.time()
        platform["platform"]["app-id"] = "led-isandlatech-demo"
        platform["platform"]["cohorte-version"] = "1.2.0"
        return platform

    def get_platform_activities(self):
        """
        Gets the list of platform activities (events)
        Return example:
         {
            "meta" : {                
                "code": 200,
                "count": 1,
                "lastupdate" : "1411979225.65"                
            },
            "activities": [
                {
                    "order": "1",
                    "timestamp": "20150421-145700",
                    "event" : "Node Discovered",
                    "object": "Node",
                    "name" : "led-gateway",
                    "UUID" : "UUID 1234-5678-9010-3546",                                        
                    "info" : "some usefull info"
                }
            ]            
        }
        """
        activities = {"meta": {}, "activities": []}
        activities["meta"]["code"] = 200
        activities["meta"]["count"] = 1
        if self._platform_activities_list_lastupdate is None:
            self._platform_activities_list_lastupdate = time.time()
        activities["meta"]["lastupdate"] = self._platform_activities_list_lastupdate

        for activity in self._platform_activities:
            activities["activities"].append(activity)
        """                        
        activities["activities"].append({
                    "order": "1",
                    "node" : "led-gateway",
                    "timestamp": "20150421-145700",
                    "kind" : "Node Discovered",
                    "info" : "Node UUID 1234-5678-9010-3546"
                })

        activities["activities"].append({
                    "order": "2",
                    "node" : "led-gateway",
                    "timestamp": "20150421-145713",
                    "kind" : "Isolate Created",
                    "info" : "Isolate UUID 4734-9878-7410-3577"
                })
        """
        return activities

    def get_nodes(self):
        """
        Gets the list of nodes of all the system.
        Return example:
        {
            "meta" : {
                "code": 200,
                "lastupdate" : "1411979225.65",
                "count": 2
            },
            "nodes": [
                {
                    "uid": "41110b1d-b510-4e51-9945-a752da04a16d",
                    "name": "central"
                },
                {
                    "uid": "56e5b100-c8a2-4bd8-a818-30edaf9a8fe9",
                    "name": "raspberry-pi-1"
                }
            ]
        }"""
        nodes = {"meta": {}, "nodes": []}
        lp = self._directory.get_local_peer()
        nodes["nodes"].append({"uid": lp.node_uid, "name": lp.node_name})
        count = 1
        for p in self._directory.get_peers():
            # if nodes["nodes"]["uid"] is None:
            found = False
            for i in nodes["nodes"]:
                if i["uid"] == p.node_uid:
                    found = True
            if found == False:
                nodes["nodes"].append({"uid": p.node_uid, "name": p.node_name})
                count += 1
        if self._nodes_list_lastupdate is None:
            self._nodes_list_lastupdate = time.time()
        nodes["meta"]["code"] = 200
        nodes["meta"]["lastupdate"] = self._nodes_list_lastupdate
        nodes["meta"]["count"] = count
        return nodes

    def get_isolates(self):
        """
        Get the list of isolates of all the system.
        Return example:
        {
            "meta" : {
                "code": 200,
                "count": 2
            },
            "isolates": [
                {
                    "uid": "6c4cd65b-b501-41db-ab40-d4cf612b2ffe",
                    "name": "WebAdmin-isolate",
                    "node_uid": "41110b1d-b510-4e51-9945-a752da04a16d",
                    "node_name": "central"
                },
                {
                    "uid": "03ff839a-df24-4bdf-b734-9fac1c886c65",
                    "name": "spellcheck-isolate",
                    "node_uid": "41110b1d-b510-4e51-9945-a752da04a16d",
                    "node_name": "central"
                }
            ]
        }"""
        isolates = {"meta": {}, "isolates": []}
        lp = self._directory.get_local_peer()
        isolates["isolates"].append({"uid": lp.uid, "name": lp.name,
                                     "node_uid": lp.node_uid, "node_name": lp.node_name})
        count = 1
        for p in self._directory.get_peers():
            isolates["isolates"].append({"uid": p.uid, "name": p.name,
                                         "node_uid": p.node_uid, "node_name": p.node_name})
            count += 1
        isolates["meta"]["code"] = 200
        isolates["meta"]["count"] = count
        return isolates

    def get_components(self):
        """
        Get All Components.
        Return example:
        {
            "meta": {
                "code": 200,
                "count": 2
            }
            "components": [
                {
                    "name": "spell_check_client",
                    "factory": "spell_check_client_factory",
                    "language": "python",
                    "isolate_uid": "03ff839a-df24-4bdf-b734-9fac1c886c65",
                    "isolate_name": "spellcheck-isolate",
                    "node_uid": "41110b1d-b510-4e51-9945-a752da04a16d",
                    "node_name": "central",
                },
                {
                    "name": "spell_dictionray_FR",
                    "factory": "spell_dictionary_FR_factory",
                    "language": "python",
                    "isolate_uid": "03ff839a-df24-4bdf-b734-9fac1c886c65",
                    "isolate_name": "spellcheck-isolate",
                    "node_uid": "41110b1d-b510-4e51-9945-a752da04a16d",
                    "node_name": "central",
                }
            ]
        }
        """
        components = {"meta": {}, "components": []}
        count = 0
        for rcomposer in self._icomposers.values():
            uid = rcomposer.get_isolate_uid()
            info = rcomposer.get_isolate_info()
            # _logger.critical('Getting info: %s -- %s', info, info.components)
            for com in info.components:
                components["components"].append({"name": com.name,
                                                 "factory": com.factory,
                                                 "language": com.language,
                                                 "isolate_uid": uid,
                                                 "isolate_name": info.name})
                count += 1
        if self._icomposerlocal is not None:
            for c in self._icomposerlocal.get_isolate_info().components:
                components["components"].append({"name": c.name,
                                                 "factory": c.factory,
                                                 "language": c.language,
                                                 "isolate_uid": self._icomposerlocal.get_isolate_uid(),
                                                 "isolate_name": self._icomposerlocal.get_isolate_info().name})
                count += 1

        components["meta"]["code"] = 200
        components["meta"]["count"] = count
        return components

    def get_node_detail(self, node_uid):
        """
        Get Node details.
        Return example:
        {
            "meta" : {
                "node": "41110b1d-b510-4e51-9945-a752da04a16d"
                "code": 200
            },
            "node": {
                "name": "central",
                "nbr_isolates": 3
            }
        }"""
        node = {"meta": {}, "node": {}}
        lp = self._directory.get_local_peer()
        count = 0
        if lp.node_uid == node_uid:
            node["node"]["name"] = lp.node_name
            count = 1
        for p in self._directory.get_peers():
            if p.node_uid == node_uid:
                node["node"]["name"] = p.node_name
                count += 1
        node["node"]["nbr_isolates"] = count
        node["meta"]["node"] = node_uid
        node["meta"]["code"] = 200
        return node

    def get_isolate_http_port(self, uid):
        lp = self._directory.get_local_peer()
        if lp.uid != uid:
            msg = beans.Message(SUBJECT_GET_HTTP)
            reply = self._herald.send(uid, msg)
            return reply.content['http.port']
        else:
            # Get the isolate HTTP port
            port = -1
            svc_ref = self._context.get_service_reference(
                pelix.http.HTTP_SERVICE)
            if svc_ref is not None:
                port = svc_ref.get_property(pelix.http.HTTP_SERVICE_PORT)            
            return port

    def get_isolate_detail(self, isolate_uid):
        """
        Get Isolate details.
        Return example:
        {
            "meta" : {
                "isolate": "03ff839a-df24-4bdf-b734-9fac1c886c65"
                "code": 200
            },
            "isolate": {
                "name": "spellcheck-isolate",
                "type": "application dynamic isolate",
                "nbr_components": 3,
                "node_uid": "",
                "node_name": "",
                "http_port": 9000,
                "http_access" : "localhost",
                "shell_port": 9001
            }
        }"""
        isolate = {"meta": {}, "isolate": {}}
        isolate["isolate"]["type"] = "app-dynamic-isolate"
        lp = self._directory.get_local_peer()
        if lp.uid == isolate_uid:
            isolate["isolate"]["name"] = lp.name
            isolate["isolate"]["node_uid"] = lp.node_uid
            isolate["isolate"]["node_name"] = lp.node_name
            isolate["isolate"]["http_port"] = self.get_isolate_http_port(isolate_uid)
            try:                
                http_access = self._directory.get_local_peer().get_access("http").host
                if http_access.startswith("::ffff:"):
                    http_access = http_access[7:]
            except KeyError:
                http_access = ""
            isolate["isolate"]["http_access"] = http_access

            isolate["isolate"]["shell_port"] = 0
            if lp.name == "cohorte.internals.forker":
                isolate["isolate"]["type"] = "cohorte-isolate"
        else:
            for p in self._directory.get_peers():
                if p.uid == isolate_uid:
                    isolate["isolate"]["name"] = p.name
                    isolate["isolate"]["node_uid"] = p.node_uid
                    isolate["isolate"]["node_name"] = p.node_name
                    isolate["isolate"]["http_port"] = self.get_isolate_http_port(isolate_uid)
                    try:
                        http_access = p.get_access("http").host
                        if http_access.startswith("::ffff:"):
                            http_access = http_access[7:]
                    except KeyError:
                        http_access = None
                    isolate["isolate"]["http_access"] = http_access
                    isolate["isolate"]["shell_port"] = 0
                    if p.name == "cohorte.internals.forker":
                        isolate["isolate"]["type"] = "cohorte-isolate"
                    break

        if isolate["isolate"]["type"] == "cohorte-isolate":
            isolate["isolate"]["nbr_components"] = -1
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
            isolate["isolate"]["nbr_components"] = count

        isolate["meta"]["isolate"] = isolate_uid
        isolate["meta"]["code"] = 200
        return isolate

    def get_component_detail(self, component_name):
        """
        Get Isolate details.
        Return example:
        {
            "meta" : {
                "component": "spell_check_client"
                "code": 200
            },
            "component": {
                "factory": "spell_check_client_factory",
                "language": "python",
                "isolate_uid": "03ff839a-df24-4bdf-b734-9fac1c886c65",
                "isolate_name": "spellcheck-isolate",
                "bundle_name": "spell_checker",
                "bundle_version": "1.0.0",
                "properties": {
                    "p1": "v1",
                    "p2": "v2"
                }
            }
        }"""
        component = {"meta": {}, "component": {}}
        if self._icomposerlocal is not None:
            for c in self._icomposerlocal.get_isolate_info().components:
                if c.name == component_name:
                    component["component"]["factory"] = c.factory
                    component["component"]["language"] = c.language
                    component["component"]["isolate_uid"] = self._icomposerlocal.get_isolate_uid()
                    component["component"]["isolate_name"] = self._icomposerlocal.get_isolate_info().name
                    component["component"]["bundle_name"] = c.bundle_name
                    component["component"]["bundle_version"] = c.bundle_version
                    component["component"]["properties"] = {}
                    component["component"]["properties"] = c.properties
                    break

        for c in self._icomposers.keys():
            comps = self._icomposers.get(c).get_isolate_info().components
            for com in comps:
                if com.name == component_name:
                    component["component"]["factory"] = com.factory
                    component["component"]["language"] = com.language
                    component["component"]["isolate_uid"] = self._icomposers.get(c).get_isolate_uid()
                    component["component"]["isolate_name"] = self._icomposers.get(c).get_isolate_info().name
                    component["component"]["bundle_name"] = com.bundle_name
                    component["component"]["bundle_version"] = com.bundle_version
                    component["component"]["properties"] = {}
                    component["component"]["properties"] = com.properties
                    break

        component["meta"]["code"] = 200
        component["meta"]["component"] = component_name
        return component


    def get_node_isolates(self, node_uid):
        """
        Get the list of isolates of one particular Node.
        Return example:
        {
            "meta" : {
                "node": "41110b1d-b510-4e51-9945-a752da04a16d"
                "code": 200,
                "count": 2
            },
            "isolates": [
                {
                    "uid": "6c4cd65b-b501-41db-ab40-d4cf612b2ffe",
                    "name": "WebAdmin-isolate",
                    "type": "app-dynamic-isolate"
                },
                {
                    "uid": "03ff839a-df24-4bdf-b734-9fac1c886c65",
                    "name": "spellcheck-isolate",
                    "type": "cohorte-isolate"
                }
            ]
        }"""
        isolates = {"meta": {}, "isolates": []}
        lp = self._directory.get_local_peer()
        count = 0

        if lp.node_uid == node_uid:
            if lp.name == "cohorte.internals.forker":
                itype = "cohorte-isolate"
            else:
                itype = "app-dynamic-isolate"
            isolates["isolates"].append({"uid": lp.uid, "name": lp.name, "type": itype})
            count = 1
        for p in self._directory.get_peers():
            if p.node_uid == node_uid:
                if p.name == "cohorte.internals.forker":
                    itype = "cohorte-isolate"
                else:
                    itype = "app-dynamic-isolate"
                isolates["isolates"].append({"uid": p.uid, "name": p.name, "type": itype})
                count += 1
        isolates["meta"]["node"] = node_uid
        isolates["meta"]["code"] = 200
        isolates["meta"]["count"] = count
        return isolates


    def get_isolate_components(self, isolate_uid):
        """
        Get Components of one particular Isolate
        {
            "meta": {
                "isolate": "50684926acb4387d0f007ced"
                "code": 200,
                "count": 3
            }
            "components": [
                {
                    "name": "spell_dictionary_FR",
                    "factory": "spell_dictionary_FR_factory",
                    "language": "python"
                },
                {
                    "name": "spell_check",
                    "factory": "spell_check_factory",
                    "language": "python"
                },
                {
                    "name": "spell_client",
                    "factory": "spell_client_factory",
                    "language": "python"
                }
            ]
        }
        """

        components = {"meta": {}, "components": []}
        count = 0
        try:
            for c in self._icomposers.keys():
                if self._icomposers.get(c) is not None:
                    if self._icomposers.get(c).get_isolate_uid() == isolate_uid:
                        comps = self._icomposers.get(c).get_isolate_info().components
                        for com in comps:
                            components["components"].append(dict(name=com.name, factory=com.factory, language=com.language))
                            count += 1
            if self._icomposerlocal is not None:
                if self._icomposerlocal.get_isolate_uid() == isolate_uid:
                    for c in self._icomposerlocal.get_isolate_info().components:
                        components["components"].append(dict(name=c.name, factory=c.factory, language=c.language))
                        count += 1
        except:
            pass
        components["meta"]["isolate"] = isolate_uid
        components["meta"]["code"] = 200
        components["meta"]["count"] = count
        return components

    """
    Actions-------------------------------------------------------------------------------------------------------------
    """

    def killall_nodes(self):
        """
        Safely destroy all nodes
        {
            "meta": {                
                "code": 200
            },
            "status": {
                "code": 0,
                "description": "Node successfully destroyed"
            }
        }
        """
        status = {"meta": {}, "status": {}}        
        status["meta"]["code"] = 200
        msg = beans.Message(cohorte.monitor.SIGNAL_STOP_PLATFORM)
        try:
            # send to other monitors            
            self._herald.fire_group('monitors', msg)                        
        except:
            pass

        try:
            msg2 = beans.MessageReceived(msg.uid, msg.subject, msg.content, "local", "local", "local")
            threading.Thread(target=self._herald.handle_message, args=[msg2]).start()            
            status["status"]["code"] = 0
            status["status"]["description"] = "All nodes successfully destroyed"    
        except:    
            # send to local monitor
            status["status"]["code"] = 1
            status["status"]["description"] = "Error destroying all nodes"
        return status

    def kill_node(self, node_uid):
        """
        Safely destroys the identified node 
        {
            "meta": {
                "node": "41110b1d-b510-4e51-9945-a752da04a16d",
                "code": 200
            },
            "status": {
                "code": 0,
                "description": "Node successfully destroyed"
            }
        }
        """        
        status = {"meta": {}, "status": {}}
        status["meta"]["node"] = node_uid
        status["meta"]["code"] = 200

        # get its "cohorte.internals.forker" UID
        isolates = self.get_node_isolates(node_uid)
        forker_uid = None
        for i in isolates["isolates"]:
            if i["name"] == "cohorte.internals.forker":
                forker_uid = i["uid"]     

        msg = beans.Message(cohorte.monitor.SIGNAL_STOP_NODE)
        try:            
            self._herald.fire(forker_uid, msg)
        except KeyError:
            # if forker is the local one, send the message locally
            lp = self._directory.get_local_peer()
            if lp.node_uid == node_uid:
                if lp.name == "cohorte.internals.forker":                   
                    msg2 = beans.MessageReceived(msg.uid, msg.subject, msg.content, "local", "local", "local")
                    threading.Thread(target=self._herald.handle_message, args=[msg2]).start()
            
        status["status"]["code"] = 0
        status["status"]["description"] = "Node successfully destroyed"
        return status

    def kill_isolate(self, isolate_uid):
        """
        Safely destroys the identified isolate
        {
            "meta": {
                "isolate": "41110b1d-b510-4e51-9945-a752da04a16d",
                "code": 200
            },
            "status": {
                "code": 0,
                "description": "Isolate successfully destroyed"
            }
        }
        """        
        status = {"meta": {}, "status": {}}
        status["meta"]["isolate"] = isolate_uid
        status["meta"]["code"] = 200

        # STOP ISOLATE
        # fire (uid, cohorte.monitor.SIGNAL_STOP_ISOLATE)
        msg = beans.Message(cohorte.monitor.SIGNAL_STOP_ISOLATE)
        self._herald.fire(isolate_uid, msg)

        status["status"]["code"] = 0
        status["status"]["description"] = "Isolate successfully destroyed"
        return status


    """
    Polling-------------------------------------------------------------------------------------------------------------
    """

    def get_platform_activities_lastupdate(self):
        nodes = {"meta": {}}
        nodes["meta"]["list"] = "platform_activities"
        nodes["meta"]["code"] = 200
        if self._platform_activities_list_lastupdate is None:
            self._platform_activities_list_lastupdate = time.time()
        nodes["meta"]["lastupdate"] = self._platform_activities_list_lastupdate
        return nodes

    def get_nodes_lastupdate(self):
        nodes = {"meta": {}}
        nodes["meta"]["list"] = "nodes"
        nodes["meta"]["code"] = 200
        if self._nodes_list_lastupdate is None:
            self._nodes_list_lastupdate = time.time()
        nodes["meta"]["lastupdate"] = self._nodes_list_lastupdate
        return nodes

    def peer_registered(self, peer):
        self._nodes_list_lastupdate = time.time()     
        with self._lock:
            inow = time.time()
            now = time.strftime("%Y-%m-%d %H:%M:%S")
            self._platform_activities_index += 1
            self._platform_activities.append({                
                    "order": self._platform_activities_index,
                    "timestamp": str(now),
                    "event" : "Isolate Created",
                    "object": "Isolate",
                    "name" : peer.name,
                    "uuid" : peer.uid,
                    "node" : peer.node_name,
                    "info" : ""
                })
            self._platform_activities_list_lastupdate = inow

    def peer_updated(self, peer, access_id, data, previous):
        self._nodes_list_lastupdate = time.time()

    def peer_unregistered(self, peer):        
        self._nodes_list_lastupdate = time.time()
        with self._lock:         
            inow = time.time()
            now = time.strftime("%Y-%m-%d %H:%M:%S")
            self._platform_activities_index += 1
            self._platform_activities.append({
                    "order": self._platform_activities_index,
                    "timestamp": str(now),
                    "event" : "Isolate Lost",
                    "object": "Isolate",
                    "name" : peer.name,
                    "uuid" : peer.uid,
                    "node" : peer.node_name,
                    "info" : ""                    
                })
            self._platform_activities_list_lastupdate = inow


    """
    Resources-----------------------------------------------------------------------------------------------------------
    """

    def root_dir(self):  # pragma: no cover
        return os.path.abspath(os.path.dirname(__file__))

    def get_file(self, filename):  # pragma: no cover
        try:
            src = os.path.join(self.root_dir(), filename)           
            with open(src, 'rb') as fp:
                return fp.read()
        except IOError as exc:
            return str(exc)            

    def load_resource(self, path, request, response):
        mimetypes = {
        ".css": "text/css",
        ".html": "text/html",
        ".js": "application/javascript",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".gif": "image/gif"
        }
        complete_path = os.path.join(self.root_dir(), path)        
        ext = os.path.splitext(path)[1]
        mimetype = mimetypes.get(ext, "text/html")

        content = self.get_file(complete_path)
        return response.send_content(200, content, mimetype)


    """
    GUI ----------------------------------------------------------------------------------------------------------------
    """

    def get_tabs(self):
        """
        Get the list of active tabs of the GUI.
        Return example:
        {
            "meta" : {
                "code": 200,
                "lastupdate" : "1411979225.65",
                "count": 1
            },
            "tabs": [
                {
                    "name": "Dashboard",
                    "icon": "fa-dashboard",
                    "page": "ajax/dashboard.html"
                }
            ]
        }"""
        tabs = {"meta": {}, "tabs": []}
        tabs["tabs"].append({"name": "Dashboard", "icon": "fa-dashboard", "page": "ajax/dashboard.html"})
        tabs["tabs"].append({"name": "Global view", "icon": "fa-sitemap", "page": "ajax/globalview.html"})
        tabs["tabs"].append({"name": "Activity Log", "icon": "fa-desktop", "page": "ajax/activitylog.html"})        
        tabs["tabs"].append({"name": "Composition", "icon": "fa-cogs", "page": "ajax/composition.html"})
        # tabs["tabs"].append({"name": "Configuration", "icon": "fa-book", "page": "ajax/configuration.html"})
        # tabs["tabs"].append({"name": "Timeline", "icon": "fa-sort-amount-asc", "page": "ajax/timeline.html"})
        tabs["meta"]["code"] = 200
        tabs["meta"]["lastupdate"] = self._nodes_list_lastupdate
        tabs["meta"]["count"] = 1
        return tabs

    def get_globalview(self):
        """
        {
         "name": "COHORTE",
         "children": [
            {"name": "gateway",
              "children": [
                {"name": "forker", "size":10, 
                  "children": [
                    
                  ]},
                {"name": "web-interface", "size":10, 
                  "children": [
                    {"name": "UI", "size":1}
                  ]
                }
              ]
            },
            {"name": "python-sensor-pc",
              "children": [
                {"name": "forker", "size":10, 
                  "children": [
                    
                  ]},
                {"name": "py.components", "size":10, 
                  "children": [
                    {"name": "PS", "size":1}
                  ]
                }
              ]},
            {"name": "java-sensor-pc",
              "children": [
                {"name": "forker", "size":10, 
                  "children": [
                    
                  ]}
              ]},
            {"name": "raspberry-pi",
              "children": [
                {"name": "forker", "size":10, 
                  "children": [
                    
                  ]},
                {"name": "rasp-components", "size":10, 
                  "children": [
                    {"name": "PS-rasp", "size":1}
                  ]
                }
              ]}
          ]  
        }
        """
        gv = {"name":"COHORTE", "children": []}
        nodes = self.get_nodes()
        for n in nodes["nodes"]:
            node = {"name": n["name"], "size":100, "children": []}
            isolates = self.get_node_isolates(n["uid"])
            for i in isolates["isolates"]:
                # if i["name"] == "cohorte.internals.forker":
                #    continue                
                isolate = {"name": i["name"], "size":10, "children": []}
                components = self.get_isolate_components(i["uid"])
                for c in components["components"]:
                    component = {"name": c["name"], "size":1}
                    isolate["children"].append(component)
                node["children"].append(isolate)
            gv["children"].append(node)
        return gv

    def change_gui_options(self, request, params):

        params_list = params.split("&")
        for param in params_list:
            options = param.split("=")
            if (options[0] == "show-internal-isolates"):
                self._show_internal_isolates = options[1]            

        # prepare response
        result = {"meta": {}, "options": {}}
        result["meta"]["code"] = 200
        result["options"]["show-internal-isolates"] = self._show_internal_isolates                                
        return result

    """
    Pages --------------------------------------------------------------------------------------------------------------
    """

    def show_webadmin_page(self, request, response):
        content = "<html><head><meta http-equiv='refresh' content='0; URL=" + self._path 
        content += "/static/web/index.html'/></head><body></body></html>"
        response.send_content(200, content)

    def show_error_page(self, request, response):
        content = """<html>
    <head><title>COHORTE</title><head><body><h3>404 This is not the web page you are looking for!</h3></body></html>"""
        response.send_content(404, content)

    def show_api_welcome_page(self, request, response):
        content = """<html>
    <head><title>COHORTE</title><head><body><h3>Welcome to COHORTE API v1!</h3></body></html>"""
        response.send_content(200, content)


    def get_session_cookie(self, request):
        """
            return the session cookie 
        """
        cookies = request.get_header(COOKIE)
        if cookies:
            cookie = Cookie.SimpleCookie()
            cookie.load(cookies)
            session_id = cookie[COOKIE_SESSION].value if COOKIE_SESSION in cookie else None
            return session_id
        return None
        
    """
    SERVLET ------------------------------------------------------------------------------------------------------------
    """
    
    
    def do_gui(self, parts, request, response):
        if len(parts) == 3:
            if str(parts[2]).lower() == "tabs":
                tabs = self.get_tabs()
                self.sendJson(tabs, response)
            elif str(parts[2]).lower().startswith("options"):
                result = self.change_gui_options(request, parts[2][8:])
                self.sendJson(result, response)
            else:
                self.show_error_page(request, response)
        elif len(parts) == 4:
            if str(parts[2]).lower() == "tabs":
                if str(parts[3]).lower() == "globalview":
                    globalview = self.get_globalview()
                    if globalview is None:
                        self.show_error_page(request, response)
                    result = json.dumps(globalview, sort_keys=False,
                                        indent=4, separators=(',', ': '))
                    response.send_content(200, result, "application/json")
        else:
            self.show_error_page(request, response)

    def do_api(self, parts, request, response):
        if len(parts) == 3:
            self.show_api_welcome_page(request, response)
        if len(parts) == 4:
            if str(parts[3]).lower() == "platform":
                platform = self.get_platform()
                self.sendJson(platform, response)

            if str(parts[3]).lower() == "nodes":
                nodes = self.get_nodes()
                self.sendJson(nodes, response)

            elif str(parts[3]).lower() == "isolates":
                isolates = self.get_isolates()
                self.sendJson(isolates, response)

            elif str(parts[3]).lower() == "components":
                components = self.get_components()
                self.sendJson(components, response)

        if len(parts) == 5:
            if str(parts[3]).lower() == "platform":
                if str(parts[4]).lower() == "activities":
                    activities = self.get_platform_activities()
                    self.sendJson(activities, response)
            if str(parts[3]).lower() == "nodes":
                if str(parts[4]).lower() == "killall":
                    result = self.killall_nodes()
                    self.sendJson(result, response)
                elif str(parts[4]) == "lastupdate":
                    node = self.get_nodes_lastupdate()
                    self.sendJson(node, response)
                else:
                    node = self.get_node_detail(str(parts[4]))
                    self.sendJson(node, response)

            elif str(parts[3]).lower() == "isolates":
                isolate = self.get_isolate_detail(str(parts[4]))
                self.sendJson(isolate, response)

            elif str(parts[3]).lower() == "components":
                isolate = self.get_component_detail(str(parts[4]))
                self.sendJson(isolate, response)

        if len(parts) == 6:
            if str(parts[3]).lower() == "platform":
                if str(parts[4]).lower() == "activities":
                    if str(parts[5]).lower() == "lastupdate":
                        session_id = self.get_session_cookie(request)
                        if session_id:
                            if self._admin.check_session_timeout(request, response, None, None, session_id, False) == False:                                                  
                                lastupdate = self.get_platform_activities_lastupdate()
                                self.sendJson(lastupdate, response)
                            else:
                                # session timeout
                                response.set_header("Location", "login.html")
                                response.send_content(302, "Redirection...")
                                # pass
                      
                        else:
                            response.set_header("Location", "login.html")
                            response.send_content(302, "Redirection...")  
                    
            if str(parts[3]).lower() == "nodes":
                if str(parts[5]).lower() == "isolates":
                    isolates = self.get_node_isolates(str(parts[4]))
                    self.sendJson(isolates, response)
                elif str(parts[5]).lower() == "kill":
                    result = self.kill_node(str(parts[4]))
                    self.sendJson(result, response)

            if str(parts[3]).lower() == "isolates":
                if str(parts[5]).lower() == "components":
                    components = self.get_isolate_components(str(parts[4]))
                    self.sendJson(components, response)
                elif str(parts[5]).lower() == "kill":
                    result = self.kill_isolate(str(parts[4]))
                    self.sendJson(result, response)
                    
        
    def do_static(self, parts, request, response):
        if len(parts) > 2:
            res_path = '/'.join(parts[2:])                        
            if str(res_path).startswith("web/index.html"):
                session_id = self.get_session_cookie(request)
                if session_id:
                    if self._admin.check_session_timeout(request, response, None, None, session_id) == False:                                                  
                        self.load_resource('/'.join(parts[2:]), request, response)
                        # pass
                    else:
                        # session timeout
                        response.set_header("Location", "login.html")
                        response.send_content(302, "Redirection...")
                        # pass
                else:
                    response.set_header("Location", "login.html")
                    response.send_content(302, "Redirection...")
                             
            else:
                self.load_resource(res_path, request, response)   
        else:
            self.show_error_page(request, response)
            

    def do_GET(self, request, response):
        """
        Handle a GET
        """
        query = request.get_path()
        # prepare query path: remove first and last '/' if exists
        if query[0] == '/':
            query = query[1:]
        if query[-1] == '/':
            query = query[:-1]
        parts = str(query).split('/')        
        if str(parts[0]) == "webadmin":            
            if len(parts) == 1:
                self.show_webadmin_page(request, response)
            elif len(parts) > 1:
                if str(parts[1]) == "static":                    
                    self.do_static(parts, request, response)
                elif str(parts[1]) == "api":
                    self.do_api(parts, request, response)
                elif str(parts[1]) == "gui":
                    self.do_gui(parts, request, response)
                else:
                    self.show_error_page(request, response)
            else:
                self.show_error_page(request, response)
        else:
            self.show_error_page(request, response)  
            
    def sendJson(self, data, response):
        result = json.dumps(data, sort_keys=False,
                            indent=4, separators=(',', ': '))
        response.send_content(data["meta"]["code"], result, "application/json")


    """
    OTHER STUFF --------------------------------------------------------------------------------------------------------
    """

    @Validate
    def validate(self, context):
        """
        Component validated, just print a trace to visualize the event.
        Between this call and the call to invalidate, the _spell_checker member
        will point to a valid spell checker service.
        """
        _logger.info("Webadmin validated")
        self._context = context


    @Invalidate
    def invalidate(self, context):
        """
        Component invalidated, just print a trace to visualize the event
        """
        _logger.info("Webadmin invalidated")
        

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
