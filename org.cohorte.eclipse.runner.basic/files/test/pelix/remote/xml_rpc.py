#!/usr/bin/env python
# -- Content-Encoding: UTF-8 --
"""
Pelix remote services: XML-RPC implementation

Based on standard package xmlrpclib

:author: Thomas Calmant
:copyright: Copyright 2016, Thomas Calmant
:license: Apache License 2.0
:version: 0.6.4

..

    Copyright 2016 Thomas Calmant

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

# Standard library
import logging

# XML RPC modules
try:
    # Python 3
    # pylint: disable=F0401
    from xmlrpc.server import SimpleXMLRPCDispatcher
    import xmlrpc.client as xmlrpclib
except ImportError:
    # Python 2
    # pylint: disable=F0401
    from SimpleXMLRPCServer import SimpleXMLRPCDispatcher
    import xmlrpclib

# iPOPO decorators
from pelix.ipopo.decorators import ComponentFactory, Requires, Validate, \
    Invalidate, Property, Provides

# Pelix constants
from pelix.utilities import to_str
import pelix.http
import pelix.remote
import pelix.remote.transport.commons as commons

# ------------------------------------------------------------------------------

# Module version
__version_info__ = (0, 6, 4)
__version__ = ".".join(str(x) for x in __version_info__)

# Documentation strings format
__docformat__ = "restructuredtext en"

# ------------------------------------------------------------------------------

XMLRPC_CONFIGURATION = 'xmlrpc'
""" Remote Service configuration constant """

PROP_XMLRPC_URL = '{0}.url'.format(XMLRPC_CONFIGURATION)
""" XML-RPC servlet URL """

_logger = logging.getLogger(__name__)

# ------------------------------------------------------------------------------


class _XmlRpcServlet(SimpleXMLRPCDispatcher):
    """
    A XML-RPC servlet that can be registered in the Pelix HTTP service

    Calls the dispatch method given in the constructor
    """
    def __init__(self, dispatch_method, encoding=None):
        """
        Sets up the servlet
        """
        SimpleXMLRPCDispatcher.__init__(self, allow_none=True,
                                        encoding=encoding)

        # Register the system.* functions
        self.register_introspection_functions()

        # Make a link to the dispatch method
        self._dispatch_method = dispatch_method

    def _simple_dispatch(self, name, params):
        """
        Dispatch method
        """
        try:
            # Internal method
            return self.funcs[name](*params)
        except KeyError:
            # Other method
            pass

        # Call the other method outside the except block, to avoid messy logs
        # in case of error
        return self._dispatch_method(name, params)

    def do_POST(self, request, response):
        """
        Handles a HTTP POST request

        :param request: The HTTP request bean
        :param request: The HTTP response handler
        """
        # Get the request content
        data = to_str(request.read_data())

        # Dispatch
        result = self._marshaled_dispatch(data, self._simple_dispatch)

        # Send the result
        response.send_content(200, result, 'text/xml')

# ------------------------------------------------------------------------------


@ComponentFactory(pelix.remote.FACTORY_TRANSPORT_XMLRPC_EXPORTER)
@Provides(pelix.remote.SERVICE_EXPORT_PROVIDER)
@Requires('_http', pelix.http.HTTP_SERVICE)
@Property('_path', pelix.http.HTTP_SERVLET_PATH, '/XML-RPC')
@Property('_kinds', pelix.remote.PROP_REMOTE_CONFIGS_SUPPORTED,
          (XMLRPC_CONFIGURATION,))
class XmlRpcServiceExporter(commons.AbstractRpcServiceExporter):
    """
    XML-RPC Remote Services exporter
    """
    def __init__(self):
        """
        Sets up the exporter
        """
        # Call parent
        super(XmlRpcServiceExporter, self).__init__()

        # Handled configurations
        self._kinds = None

        # HTTP Service
        self._http = None
        self._path = None

        # XML-RPC servlet
        self._servlet = None

    def get_access(self):
        """
        Retrieves the URL to access this component
        """
        port = self._http.get_access()[1]
        return "http://{{server}}:{0}{1}".format(port, self._path)

    def make_endpoint_properties(self, svc_ref, name, fw_uid):
        """
        Prepare properties for the ExportEndpoint to be created

        :param svc_ref: Service reference
        :param name: Endpoint name
        :param fw_uid: Framework UID
        :return: A dictionary of extra endpoint properties
        """
        return {PROP_XMLRPC_URL: self.get_access()}

    @Validate
    def validate(self, context):
        """
        Component validated
        """
        # Call parent
        super(XmlRpcServiceExporter, self).validate(context)

        # Create/register the servlet
        self._servlet = _XmlRpcServlet(self.dispatch)
        self._http.register_servlet(self._path, self._servlet)

    @Invalidate
    def invalidate(self, context):
        """
        Component invalidated
        """
        # Unregister the servlet
        self._http.unregister(None, self._servlet)

        # Call parent
        super(XmlRpcServiceExporter, self).invalidate(context)

        # Clean up members
        self._servlet = None

# ------------------------------------------------------------------------------


class _ServiceCallProxy(object):
    """
    Service call proxy
    """
    def __init__(self, name, url):
        """
        Sets up the call proxy

        :param name: End point name
        :param url: End point URL
        """
        self.__name = name
        self.__url = url

    def __getattr__(self, name):
        """
        Prefixes the requested attribute name by the endpoint name
        """
        # Make a proxy for this call
        # This is an ugly trick to handle multithreaded calls, as the
        # underlying proxy re-uses the same connection when possible: sometimes
        # it means sending a request before retrieving a result
        proxy = xmlrpclib.ServerProxy(self.__url, allow_none=True)
        return getattr(proxy, "{0}.{1}".format(self.__name, name))


@ComponentFactory(pelix.remote.FACTORY_TRANSPORT_XMLRPC_IMPORTER)
@Provides(pelix.remote.SERVICE_IMPORT_ENDPOINT_LISTENER)
@Property('_kinds', pelix.remote.PROP_REMOTE_CONFIGS_SUPPORTED,
          (XMLRPC_CONFIGURATION,))
class XmlRpcServiceImporter(commons.AbstractRpcServiceImporter):
    """
    XML-RPC Remote Services importer
    """
    def __init__(self):
        """
        Sets up the exporter
        """
        # Call parent
        super(XmlRpcServiceImporter, self).__init__()

        # Component properties
        self._kinds = None

    def make_service_proxy(self, endpoint):
        """
        Creates the proxy for the given ImportEndpoint

        :param endpoint: An ImportEndpoint bean
        :return: A service proxy
        """
        # Get the access URL
        access_url = endpoint.properties.get(PROP_XMLRPC_URL)
        if not access_url:
            # No URL information
            _logger.warning("No access URL given: %s", endpoint)
            return

        if endpoint.server is not None:
            # Server information given
            access_url = access_url.format(server=endpoint.server)
        else:
            # Use the local IP as the source server, just in case
            local_server = "localhost"
            access_url = access_url.format(server=local_server)

        # Return the proxy
        return _ServiceCallProxy(endpoint.name, access_url)

    def clear_service_proxy(self, endpoint):
        """
        Destroys the proxy made for the given ImportEndpoint

        :param endpoint: An ImportEndpoint bean
        """
        # Nothing to do
        return
