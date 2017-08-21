#!/usr/bin/env python
# -- Content-Encoding: UTF-8 --
"""
Defines the iPOPO decorators classes to manipulate component factory classes

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
import inspect
import logging
import threading
import types

# Pelix modules
from pelix.utilities import is_string, to_iterable
from pelix.ipopo.contexts import FactoryContext, Requirement
import pelix.ipopo.constants as constants

# ------------------------------------------------------------------------------

# Module version
__version_info__ = (0, 6, 4)
__version__ = ".".join(str(x) for x in __version_info__)

# Documentation strings format
__docformat__ = "restructuredtext en"

# ------------------------------------------------------------------------------

# Prepare the module logger
_logger = logging.getLogger("ipopo.decorators")

# ------------------------------------------------------------------------------


def is_from_parent(cls, attribute_name, value=None):
    """
    Tests if the current attribute value is shared by a parent of the given
    class.

    Returns None if the attribute value is None.

    :param cls: Child class with the requested attribute
    :param attribute_name: Name of the attribute to be tested
    :param value: The exact value in the child class (optional)
    :return: True if the attribute value is shared with a parent class
    """
    if value is None:
        try:
            # Get the current value
            value = getattr(cls, attribute_name)
        except AttributeError:
            # No need to go further: the attribute does not exist
            return False

    for base in cls.__bases__:
        # Look for the value in each parent class
        try:
            return getattr(base, attribute_name) is value
        except AttributeError:
            pass

    # Attribute value not found in parent classes
    return False


def get_factory_context(cls):
    """
    Retrieves the factory context object associated to a factory. Creates it
    if needed

    :param cls: The factory class
    :return: The factory class context
    """
    context = getattr(cls, constants.IPOPO_FACTORY_CONTEXT, None)

    if context is None:
        # Class not yet manipulated
        context = FactoryContext()

    elif is_from_parent(cls, constants.IPOPO_FACTORY_CONTEXT):
        # Create a copy the context
        context = context.copy(True)

        # * Manipulation has not been applied yet
        context.completed = False

    else:
        # Nothing special to do
        return context

    # Context has been created or copied, inject the new bean
    setattr(cls, constants.IPOPO_FACTORY_CONTEXT, context)
    return context


def get_method_description(method):
    """
    Retrieves a description of the given method. If possible, the description
    contains the source file name and line.

    :param method: A method
    :return: A description of the method (at least its name)
    """
    try:
        try:
            line_no = inspect.getsourcelines(method)[1]

        except IOError:
            # Error reading the source file
            line_no = -1

        return "'{method}' ({file}:{line})" \
            .format(method=method.__name__,
                    file=inspect.getfile(method),
                    line=line_no)

    except TypeError:
        # Method can't be inspected
        return "'{0}'".format(method.__name__)


def validate_method_arity(method, *needed_args):
    """
    Tests if the decorated method has a sufficient number of parameters.

    :param method: The method to be tested
    :param needed_args: The name (for description only) of the needed
                        arguments, without "self".
    :return: Nothing
    :raise TypeError: Invalid number of parameter
    """
    nb_needed_args = len(needed_args) + 1

    # Test the number of parameters
    argspec = inspect.getargspec(method)
    method_args = argspec.args

    if len(method_args) == 0:
        # No argument at all
        raise TypeError("Decorated method {0} must have at least the 'self' "
                        "parameter".format(get_method_description(method)))

    if argspec.varargs is not None:
        # Variable arguments
        if len(method_args) != 1 or method_args[0] != "self":
            # Other arguments detected
            raise TypeError("When using '*args', the decorated {0} method must"
                            " only accept the 'self' argument"
                            .format(get_method_description(method)))

    elif len(method_args) != nb_needed_args or method_args[0] != 'self':
        # "Normal" arguments
        raise TypeError("The decorated method {0} must accept exactly {1} "
                        "parameters : (self, {2})"
                        .format(get_method_description(method), nb_needed_args,
                                ", ".join(needed_args)))

# ------------------------------------------------------------------------------


def _ipopo_setup_callback(cls, context):
    """
    Sets up the class _callback dictionary

    :param cls: The class to handle
    :param context: The factory class context
    """
    assert inspect.isclass(cls)
    assert isinstance(context, FactoryContext)

    if context.callbacks is not None:
        callbacks = context.callbacks.copy()

    else:
        callbacks = {}

    functions = inspect.getmembers(cls, inspect.isroutine)

    for _, function in functions:

        if not hasattr(function, constants.IPOPO_METHOD_CALLBACKS):
            # No attribute, get the next member
            continue

        method_callbacks = getattr(function, constants.IPOPO_METHOD_CALLBACKS)

        if not isinstance(method_callbacks, list):
            # Invalid content
            _logger.warning("Invalid callback information %s in %s",
                            constants.IPOPO_METHOD_CALLBACKS,
                            get_method_description(function))
            continue

        # Keeping it allows inheritance : by removing it, only the first
        # child will see the attribute -> Don't remove it

        # Store the call backs
        for _callback in method_callbacks:
            if _callback in callbacks and \
                    not is_from_parent(cls, callbacks[_callback].__name__,
                                       callbacks[_callback]):
                _logger.warning("Redefining the callback %s in class '%s'.\n"
                                "\tPrevious callback : %s\n"
                                "\tNew callback : %s", _callback, cls.__name__,
                                get_method_description(callbacks[_callback]),
                                get_method_description(function))

            callbacks[_callback] = function

    # Update the factory context
    context.callbacks.clear()
    context.callbacks.update(callbacks)


def _ipopo_setup_field_callback(cls, context):
    """
    Sets up the class _field_callback dictionary

    :param cls: The class to handle
    :param context: The factory class context
    """
    assert inspect.isclass(cls)
    assert isinstance(context, FactoryContext)

    if context.field_callbacks is not None:
        callbacks = context.field_callbacks.copy()

    else:
        callbacks = {}

    functions = inspect.getmembers(cls, inspect.isroutine)

    for name, function in functions:
        if not hasattr(function, constants.IPOPO_METHOD_FIELD_CALLBACKS):
            # No attribute, get the next member
            continue

        method_callbacks = getattr(function,
                                   constants.IPOPO_METHOD_FIELD_CALLBACKS)
        if not isinstance(method_callbacks, list):
            # Invalid content
            _logger.warning("Invalid attribute %s in %s",
                            constants.IPOPO_METHOD_FIELD_CALLBACKS, name)
            continue

        # Keeping it allows inheritance : by removing it, only the first
        # child will see the attribute -> Don't remove it

        # Store the call backs
        for kind, field, if_valid in method_callbacks:
            fields_cbs = callbacks.setdefault(field, {})

            if kind in fields_cbs and \
                    not is_from_parent(cls, fields_cbs[kind][0].__name__):
                _logger.warning("Redefining the callback %s in '%s'. "
                                "Previous callback : '%s' (%s). "
                                "New callback : %s", kind, name,
                                fields_cbs[kind][0].__name__,
                                fields_cbs[kind][0], function)

            fields_cbs[kind] = (function, if_valid)

    # Update the factory context
    context.field_callbacks.clear()
    context.field_callbacks.update(callbacks)

# ------------------------------------------------------------------------------


def _append_object_entry(obj, list_name, entry):
    """
    Appends the given entry in the given object list.
    Creates the list field if needed.

    :param obj: The object that contains the list
    :param list_name: The name of the list member in *obj*
    :param entry: The entry to be added to the list
    :raise ValueError: Invalid attribute content
    """
    # Get the list
    obj_list = getattr(obj, list_name, None)
    if obj_list is None:
        # We'll have to create it
        obj_list = []
        setattr(obj, list_name, obj_list)

    assert isinstance(obj_list, list)

    # Set up the property, if needed
    if entry not in obj_list:
        obj_list.append(entry)

# ------------------------------------------------------------------------------


class Holder(object):
    """
    Simple class that holds a value
    """
    def __init__(self, value):
        """
        Sets up the holder instance
        """
        self.value = value


def _ipopo_class_field_property(name, value, methods_prefix):
    """
    Sets up an iPOPO field property, using Python property() capabilities

    :param name: The property name
    :param value: The property default value
    :param methods_prefix: The common prefix of the getter and setter injected
                           methods
    :return: A generated Python property()
    """
    # The property lock
    lock = threading.RLock()

    # Prepare the methods names
    getter_name = "{0}{1}".format(methods_prefix,
                                  constants.IPOPO_GETTER_SUFFIX)
    setter_name = "{0}{1}".format(methods_prefix,
                                  constants.IPOPO_SETTER_SUFFIX)

    local_holder = Holder(value)

    def get_value(self):
        """
        Retrieves the property value, from the iPOPO dictionaries
        """
        getter = getattr(self, getter_name, None)
        if getter is not None:
            # Use the component getter
            with lock:
                return getter(self, name)
        else:
            # Use the local holder
            return local_holder.value

    def set_value(self, new_value):
        """
        Sets the property value and trigger an update event

        :param new_value: The new property value
        """
        setter = getattr(self, setter_name, None)
        if setter is not None:
            # Use the component setter
            with lock:
                setter(self, name, new_value)
        else:
            # Change the local holder
            local_holder.value = new_value

    return property(get_value, set_value)

# ------------------------------------------------------------------------------


class Instantiate(object):
    """
    Decorator that sets up a future instance of a component
    """
    def __init__(self, name, properties=None):
        """
        Sets up the decorator

        :param name: Instance name
        :param properties: Instance properties
        """
        if not is_string(name):
            raise TypeError("Instance name must be a string")

        if properties is not None and not isinstance(properties, dict):
            raise TypeError("Instance properties must be a dictionary or None")

        name = name.strip()
        if not name:
            raise ValueError("Invalid instance name '{0}'".format(name))

        self.__name = name
        self.__properties = properties

    def __call__(self, factory_class):
        """
        Sets up and registers the instances descriptions

        :param factory_class: The factory class to instantiate
        :return: The decorated factory class
        :raise TypeError: The given object is not a class
        """
        if not inspect.isclass(factory_class):
            raise TypeError("@Instantiate can decorate only classes, "
                            "not '{0}'".format(type(factory_class).__name__))

        # Store the instance in the factory context
        context = get_factory_context(factory_class)
        try:
            context.add_instance(self.__name, self.__properties)

        except NameError:
            _logger.warning("Component '%s' defined twice, new definition "
                            "ignored", self.__name)

        return factory_class

# ------------------------------------------------------------------------------


class ComponentFactory(object):
    """
    Decorator that sets up a component factory class
    """
    def __init__(self, name=None, excluded=None):
        """
        Sets up the decorator

        :param name: Name of the component factory
        :param excluded: List of IDs of handlers which configuration must not
                         be inherited from the parent class
        """
        self.__factory_name = name
        self.__excluded_inheritance = to_iterable(excluded)

    def __call__(self, factory_class):
        """
        Sets up and registers the factory class

        :param factory_class: The class to decorate
        :return: The decorated class
        :raise TypeError: The given object is not a class
        """
        if not inspect.isclass(factory_class):
            raise TypeError("@ComponentFactory can decorate only classes, "
                            "not '{0}'".format(type(factory_class).__name__))

        # Get the factory context
        context = get_factory_context(factory_class)

        # Test if a manipulation has already been applied
        if not context.completed:
            # Set up the factory name
            if not self.__factory_name:
                self.__factory_name = factory_class.__name__ + "Factory"

            # Manipulate the class...

            # Update the factory context
            context.name = self.__factory_name
            context.inherit_handlers(self.__excluded_inheritance)
            context.is_singleton = False
            context.completed = True

            # Find callbacks
            _ipopo_setup_callback(factory_class, context)
            _ipopo_setup_field_callback(factory_class, context)

            # Store the factory context in its field
            setattr(factory_class, constants.IPOPO_FACTORY_CONTEXT, context)

            # Inject the properties getter and setter if needed
            if context.properties_fields:
                setattr(factory_class, constants.IPOPO_PROPERTY_PREFIX +
                        constants.IPOPO_GETTER_SUFFIX, None)
                setattr(factory_class, constants.IPOPO_PROPERTY_PREFIX +
                        constants.IPOPO_SETTER_SUFFIX, None)
        else:
            # Manipulation already applied: do nothing more
            _logger.error("%s has already been manipulated with the name '%s'."
                          " Keeping the old name.",
                          get_method_description(factory_class), context.name)

        return factory_class


class SingletonFactory(ComponentFactory):
    """
    Decorator that sets up a component factory class which supports only one
    instantiated component at a time
    """
    def __call__(self, factory_class):
        """
        Sets up and registers the factory class

        :param factory_class: The class to decorate
        :return: The decorated class
        :raise TypeError: The given object is not a class
        """
        # Manipulate the class
        factory_class = super(SingletonFactory, self).__call__(factory_class)

        # Set the singleton flag
        context = get_factory_context(factory_class)
        context.is_singleton = True
        return factory_class

# ------------------------------------------------------------------------------


class Property(object):
    """
    @Property decorator

    Defines a component property.
    """
    HANDLER_ID = constants.HANDLER_PROPERTY
    """ ID of the handler configured by this decorator """

    def __init__(self, field=None, name=None, value=None):
        """
        Sets up the property

        :param field: The property field in the class (can't be None nor empty)
        :param name: The property name (if None, this will be the field name)
        :param value: The property value
        :raise TypeError: Invalid argument type
        :raise ValueError: If the name or the name is None or empty
        """
        # Field validity test
        if not is_string(field):
            raise TypeError("Field name must be a string")

        field = field.strip()
        if not field or ' ' in field:
            raise ValueError("Empty or invalid property field name '{0}'"
                             .format(field))

        # Name validity test
        if name is not None:
            if not is_string(name):
                raise TypeError("Property name must be a string")

            name = name.strip()

        if not name:
            # No name given: use the field name
            name = field

        self._field = field
        self._name = name
        self._value = value

    def __call__(self, clazz):
        """
        Adds the property to the class iPOPO properties field.
        Creates the field if needed.

        :param clazz: The class to decorate
        :return: The decorated class
        :raise TypeError: If *clazz* is not a type
        """
        if not inspect.isclass(clazz):
            raise TypeError("@Property can decorate only classes, not '{0}'"
                            .format(type(clazz).__name__))

        # Get the factory context
        context = get_factory_context(clazz)
        if context.completed:
            # Do nothing if the class has already been manipulated
            _logger.warning("@Property: Already manipulated class: %s",
                            get_method_description(clazz))
            return clazz

        # Set up the property in the class
        context.properties[self._name] = self._value

        # Associate the field to the property name
        context.properties_fields[self._field] = self._name

        # Mark the handler in the factory context
        context.set_handler(self.HANDLER_ID, None)

        # Inject a property in the class. The property will call an instance
        # level getter / setter, injected by iPOPO after the instance creation
        setattr(clazz, self._field,
                _ipopo_class_field_property(self._name, self._value,
                                            constants.IPOPO_PROPERTY_PREFIX))

        return clazz


class HiddenProperty(Property):
    """
    @HiddenProperty decorator

    Defines a component property.
    """
    def __call__(self, clazz):
        """
        Adds the property to the class iPOPO properties field.
        Creates the field if needed.

        :param clazz: The class to decorate
        :return: The decorated class
        :raise TypeError: If *clazz* is not a type
        """
        if not inspect.isclass(clazz):
            raise TypeError(
                "@HiddenProperty can decorate only classes, not '{0}'"
                .format(type(clazz).__name__))

        # Get the factory context
        context = get_factory_context(clazz)
        if context.completed:
            # Do nothing if the class has already been manipulated
            _logger.warning("@HiddenProperty: Already manipulated class: %s",
                            get_method_description(clazz))
            return clazz

        # Set up the property in the class
        context.hidden_properties[self._name] = self._value

        # Mark the handler in the factory context
        context.set_handler(self.HANDLER_ID, None)

        # Inject a property in the class. The property will call an instance
        # level getter / setter, injected by iPOPO after the instance creation
        setattr(clazz, self._field,
                _ipopo_class_field_property(
                    self._name, self._value,
                    constants.IPOPO_HIDDEN_PROPERTY_PREFIX))

        return clazz

# ------------------------------------------------------------------------------


def _get_specifications(specifications):
    """
    Computes the list of strings corresponding to the given specifications

    :param specifications: A string, a class or a list of specifications
    :return: A list of strings
    :raise ValueError: Invalid specification found
    """
    if not specifications:
        raise ValueError("No specifications given")

    if inspect.isclass(specifications):
        # Get the name of the class
        return [specifications.__name__]

    elif is_string(specifications):
        # Specification name
        specifications = specifications.strip()
        if not specifications:
            raise ValueError("Empty specification given")

        return [specifications]

    elif isinstance(specifications, (list, tuple)):
        # List given: normalize its content
        results = []
        for specification in specifications:
            results.extend(_get_specifications(specification))

        return results

    else:
        raise ValueError("Unhandled specifications type : {0}"
                         .format(type(specifications).__name__))


class Provides(object):
    """
    @Provides decorator

    Defines an interface exported by a component.
    """
    HANDLER_ID = constants.HANDLER_PROVIDES
    """ ID of the handler configured by this decorator """

    def __init__(self, specifications, controller=None):
        """
        Sets up a provided service.
        A service controller can be defined to enable or disable the service.

        :param specifications: A list of provided interface(s) name(s)
                               (can't be empty)
        :param controller: Name of the service controller class field
                           (optional)
        :raise ValueError: If the specifications are invalid
        """
        if controller is not None:
            if not is_string(controller):
                raise ValueError("Controller name must be a string")

            controller = controller.strip()
            if not controller:
                # Empty controller name
                _logger.warning("Empty controller name given")
                controller = None

            elif ' ' in controller:
                raise ValueError("Controller name contains spaces")

        self.__specifications = _get_specifications(specifications)
        self.__controller = controller

    def __call__(self, clazz):
        """
        Adds the provided service information to the class context iPOPO field.
        Creates the field if needed.

        :param clazz: The class to decorate
        :return: The decorated class
        :raise TypeError: If *clazz* is not a type
        """
        if not inspect.isclass(clazz):
            raise TypeError("@Provides can decorate only classes, not '{0}'"
                            .format(type(clazz).__name__))

        # Get the factory context
        context = get_factory_context(clazz)
        if context.completed:
            # Do nothing if the class has already been manipulated
            _logger.warning("@Provides: Already manipulated class: %s",
                            get_method_description(clazz))
            return clazz

        # Avoid duplicates (but keep the order)
        filtered_specs = []
        for spec in self.__specifications:
            if spec not in filtered_specs:
                filtered_specs.append(spec)

        # Store the service information
        config = context.set_handler_default(self.HANDLER_ID, [])
        config.append((filtered_specs, self.__controller))

        if self.__controller:
            # Inject a property in the class. The property will call an
            # instance level getter / setter, injected by iPOPO after the
            # instance creation
            setattr(clazz, self.__controller,
                    _ipopo_class_field_property(
                        self.__controller, True,
                        constants.IPOPO_CONTROLLER_PREFIX))

            # Inject the future controller methods
            setattr(clazz, constants.IPOPO_CONTROLLER_PREFIX +
                    constants.IPOPO_GETTER_SUFFIX, None)
            setattr(clazz, constants.IPOPO_CONTROLLER_PREFIX +
                    constants.IPOPO_SETTER_SUFFIX, None)

        return clazz

# ------------------------------------------------------------------------------


class Requires(object):
    """
    @Requires decorator

    Defines a required service
    """
    HANDLER_ID = constants.HANDLER_REQUIRES
    """ ID of the handler configured by this decorator """

    def __init__(self, field, specification, aggregate=False, optional=False,
                 spec_filter=None, immediate_rebind=False):
        """
        Sets up the requirement

        :param field: The injected field
        :param specification: The injected service specification
        :param aggregate: If true, injects a list
        :param optional: If true, this injection is optional
        :param spec_filter: An LDAP query to filter injected services upon
                            their properties
        :param immediate_rebind: If True, the component won't be invalidated
                                 then re-validated if a matching service is
                                 available when the injected dependency is
                                 unbound
        :raise TypeError: A parameter has an invalid type
        :raise ValueError: An error occurred while parsing the filter or an
                           argument is incorrect
        """
        if not field:
            raise ValueError("Empty field name.")

        if not is_string(field):
            raise TypeError("The field name must be a string, not {0}"
                            .format(type(field).__name__))

        if ' ' in field:
            raise ValueError("Field name can't contain spaces.")

        self._field = field

        # Be sure that there is only one required specification
        specifications = _get_specifications(specification)
        self._multi_specs = len(specifications) > 1

        # Construct the requirement object
        self._requirement = Requirement(specifications[0], aggregate,
                                        optional, spec_filter,
                                        immediate_rebind)

    def __call__(self, clazz):
        """
        Adds the requirement to the class iPOPO field

        :param clazz: The class to decorate
        :return: The decorated class
        :raise TypeError: If *clazz* is not a type
        """
        if not inspect.isclass(clazz):
            raise TypeError("@{0} can decorate only classes, not '{1}'"
                            .format(type(self).__name__, type(clazz).__name__))

        if self._multi_specs:
            _logger.warning("%s: Only one specification can be required: "
                            "%s -> %s", type(self).__name__, clazz.__name__,
                            self._field)

        # Set up the property in the class
        context = get_factory_context(clazz)
        if context.completed:
            # Do nothing if the class has already been manipulated
            _logger.warning("@%s: Already manipulated class: %s",
                            type(self).__name__, get_method_description(clazz))
            return clazz

        # Store the requirement information
        config = context.set_handler_default(self.HANDLER_ID, {})
        config[self._field] = self._requirement

        # Inject the field
        setattr(clazz, self._field, None)
        return clazz

# ------------------------------------------------------------------------------


class RequiresVarFilter(Requires):
    """
    @RequiresVarFilter decorator: acts like @Requires but its filter adapts to
    component properties
    """
    HANDLER_ID = constants.HANDLER_REQUIRES_VARIABLE_FILTER
    """ ID of the handler configured by this decorator """

# ------------------------------------------------------------------------------


class RequiresBest(Requires):
    """
    @RequiresBest decorator

    Defines a requirement of the service with best ranking
    """
    HANDLER_ID = constants.HANDLER_REQUIRES_BEST
    """ ID of the handler configured by this decorator """

    def __init__(self, field, specification, optional=False, spec_filter=None,
                 immediate_rebind=True):
        """
        Sets up the requirement

        :param field: The injected field
        :param specification: The injected service specification
        :param optional: If true, this injection is optional
        :param spec_filter: An LDAP query to filter injected services upon
                            their properties
        :param immediate_rebind: If True, the component won't be invalidated
                                 then re-validated if a matching service is
                                 available when the injected dependency is
                                 unbound
        :raise TypeError: A parameter has an invalid type
        :raise ValueError: An error occurred while parsing the filter or an
                           argument is incorrect
        """
        super(RequiresBest, self).__init__(field, specification, False,
                                           optional, spec_filter,
                                           immediate_rebind)

# ------------------------------------------------------------------------------


class RequiresMap(Requires):
    """
    @RequiresMap decorator

    Defines a required service, injected in a dictionary
    """
    HANDLER_ID = constants.HANDLER_REQUIRES_MAP
    """ ID of the handler configured by this decorator """

    def __init__(self, field, specification, key, allow_none=False,
                 aggregate=False, optional=False, spec_filter=None):
        """
        Sets up the requirement

        :param field: The injected field
        :param specification: The injected service specification
        :param key: Name of the service property to use as a dictionary key
        :param allow_none: If True, inject services with a None property value
        :param aggregate: If true, injects a list
        :param optional: If true, this injection is optional
        :param spec_filter: An LDAP query to filter injected services upon
                            their properties
        :raise TypeError: A parameter has an invalid type
        :raise ValueError: An error occurred while parsing the filter or an
                           argument is incorrect
        """
        super(RequiresMap, self).__init__(field, specification, aggregate,
                                          optional, spec_filter, False)
        # Check if key is valid
        if not key:
            raise ValueError("No property key given")

        # Store the flags
        self._key = key
        self._allow_none = allow_none

    def __call__(self, clazz):
        """
        Adds the requirement to the class iPOPO field

        :param clazz: The class to decorate
        :return: The decorated class
        :raise TypeError: If *clazz* is not a type
        """
        clazz = super(RequiresMap, self).__call__(clazz)

        # Set up the property in the class
        context = get_factory_context(clazz)
        if not context.completed:
            # Store the requirement information
            config = context.set_handler_default(self.HANDLER_ID, {})
            config[self._field] = (self._requirement, self._key,
                                   self._allow_none)
        return clazz

# ------------------------------------------------------------------------------


class Temporal(Requires):
    """
    @Temporal decorator

    Defines a required service
    """
    HANDLER_ID = constants.HANDLER_TEMPORAL
    """ ID of the handler configured by this decorator """

    def __init__(self, field, specification, optional=False, spec_filter=None,
                 timeout=10):
        """
        Sets up the requirement

        :param field: The injected field
        :param specification: The injected service specification
        :param optional: If true, this injection is optional
        :param spec_filter: An LDAP query to filter injected services upon
                            their properties
        :param timeout: Temporal timeout, in seconds (must be greater than 0)
        :raise TypeError: A parameter has an invalid type
        :raise ValueError: An error occurred while parsing the filter or an
                           argument is incorrect
        """
        super(Temporal, self).__init__(field, specification, False, optional,
                                       spec_filter, True)
        if timeout <= 0:
            _logger.warning("@Temporal timeout must be greater than 0. "
                            "Using default value.")
            self._timeout = 10
        else:
            self._timeout = timeout

    def __call__(self, clazz):
        """
        Adds the requirement to the class iPOPO field

        :param clazz: The class to decorate
        :return: The decorated class
        :raise TypeError: If *clazz* is not a type
        """
        clazz = super(Temporal, self).__call__(clazz)

        # Store the requirement information
        context = get_factory_context(clazz)
        if not context.completed:
            config = context.set_handler_default(self.HANDLER_ID, {})
            config[self._field] = (self._requirement, self._timeout)
        return clazz

# ------------------------------------------------------------------------------


class BindField(object):
    """
    BindField callback decorator, called when a component is bound to a
    dependency, injected in the given field.

    The decorated method must have the following prototype :

    .. python::
       def bind_method(self, field, service, service_reference):
           '''
           Method called when a service is bound to the component

           field: Field wherein the dependency is injected
           service: The injected service instance.
           service_reference: The injected service ServiceReference
           '''
           # ...

    If the service is a required one, the bind callback is called **before**
    the component is validated.
    The bind field callback is called **after** the global bind method.

    The service reference can be stored *if its reference is deleted on
    unbind*.

    Exceptions raised by a bind callback are ignored.
    """
    def __init__(self, field, if_valid=False):
        """
        Sets up the decorator

        :param field: Field associated to the binding
        :param if_valid: Call the method only if the component is valid
        """
        self._field = field
        self._if_valid = if_valid

    def __call__(self, method):
        """
        Updates the "field callback" list for this method

        :param method: Method to decorate
        :return: Decorated method
        :raise TypeError: The decorated element is not a valid function
        """
        if not inspect.isroutine(method):
            raise TypeError("@BindField can only be applied on functions")

        # Tests the number of parameters
        validate_method_arity(method, "field", "service", "service_reference")

        _append_object_entry(method, constants.IPOPO_METHOD_FIELD_CALLBACKS,
                             (constants.IPOPO_CALLBACK_BIND_FIELD,
                              self._field, self._if_valid))
        return method


class UpdateField(object):
    """
    UpdateField callback decorator, called when a component dependency property
    has been modified.

    The decorated method must have the following prototype :

    .. python::
       def update_method(self, service, service_reference, old_properties):
           '''
           Method called when a service is bound to the component

           service: The injected service instance.
           service_reference: The injected service ServiceReference
           old_properties: Previous service properties
           '''
           # ...

    Exceptions raised by an update callback are ignored.
    """
    def __init__(self, field, if_valid=False):
        """
        Sets up the decorator

        :param field: Field associated to the binding
        :param if_valid: Call the method only if the component is valid
        """
        self._field = field
        self._if_valid = if_valid

    def __call__(self, method):
        """
        Updates the "field callback" list for this method

        :param method: Method to decorate
        :return: Decorated method
        :raise TypeError: The decorated element is not a valid function
        """
        if not inspect.isroutine(method):
            raise TypeError("@UnbindField can only be applied on functions")

        # Tests the number of parameters
        validate_method_arity(method, "field", "service", "service_reference",
                              "old_properties")

        _append_object_entry(method, constants.IPOPO_METHOD_FIELD_CALLBACKS,
                             (constants.IPOPO_CALLBACK_UPDATE_FIELD,
                              self._field, self._if_valid))
        return method


class UnbindField(object):
    """
    UnbindField callback decorator, called when a component is unbound to a
    dependency, removed from the given field.

    The decorated method must have the following prototype :

    .. python::
       def unbind_method(self, field, service, service_reference):
           '''
           Method called when a service is bound to the component

           field: Field wherein the dependency is injected
           service: The injected service instance.
           service_reference: The injected service ServiceReference
           '''
           # ...

    If the service is a required one, the unbind callback is called **after**
    the component has been invalidated.
    The unbind field callback is called **before** the global unbind method.

    Exceptions raised by an unbind callback are ignored.
    """
    def __init__(self, field, if_valid=False):
        """
        Sets up the decorator

        :param field: Field associated to the binding
        :param if_valid: Call the method only if the component is valid
        """
        self._field = field
        self._if_valid = if_valid

    def __call__(self, method):
        """
        Updates the "field callback" list for this method

        :param method: Method to decorate
        :return: Decorated method
        :raise TypeError: The decorated element is not a valid function
        """
        if not inspect.isroutine(method):
            raise TypeError("@UnbindField can only be applied on functions")

        # Tests the number of parameters
        validate_method_arity(method, "field", "service", "service_reference")

        _append_object_entry(method, constants.IPOPO_METHOD_FIELD_CALLBACKS,
                             (constants.IPOPO_CALLBACK_UNBIND_FIELD,
                              self._field, self._if_valid))
        return method

# ------------------------------------------------------------------------------


def Bind(method):
    """
    Bind callback decorator, called when a component is bound to a dependency.

    The decorated method must have the following prototype :

    .. python::
       def bind_method(self, service, service_reference):
           '''
           Method called when a service is bound to the component

           service: The injected service instance.
           service_reference: The injected service ServiceReference
           '''
           # ...

    If the service is a required one, the bind callback is called **before**
    the component is validated.

    The service reference can be stored *if its reference is deleted on
    unbind*.

    Exceptions raised by a bind callback are ignored.

    :param method: The decorated method
    :raise TypeError: The decorated element is not a valid function
    """
    if not inspect.isroutine(method):
        raise TypeError("@Bind can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "service", "service_reference")

    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_BIND)
    return method


def Update(method):
    """
    Update callback decorator, called when a component dependency property has
    been modified.

    The decorated method must have the following prototype :

    .. python::
       def update_method(self, service, service_reference, old_properties):
           '''
           Method called when a service is bound to the component

           service: The injected service instance.
           service_reference: The injected service ServiceReference
           old_properties: Previous service properties
           '''
           # ...

    Exceptions raised by an update callback are ignored.

    :param method: The decorated method
    :raise TypeError: The decorated element is not a valid function
    """
    if not isinstance(method, types.FunctionType):
        raise TypeError("@Update can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "service", "service_reference",
                          "old_properties")

    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_UPDATE)
    return method


def Unbind(method):
    """
    Unbind callback decorator, called when a component dependency is unbound.

    The decorated method must have the following prototype :

    .. python::
       def unbind_method(self, service, service_reference):
           '''
           Method called when a service is bound to the component

           service: The injected service instance.
           service_reference: The injected service ServiceReference
           '''
           # ...

    If the service is a required one, the unbind callback is called **after**
    the component has been invalidated.

    Exceptions raised by an unbind callback are ignored.

    :param method: The decorated method
    :raise TypeError: The decorated element is not a valid function
    """
    if not isinstance(method, types.FunctionType):
        raise TypeError("@Unbind can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "service", "service_reference")

    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_UNBIND)
    return method


def Validate(method):
    """
    Validation callback decorator, called when a component becomes valid,
    i.e. if all of its required dependencies has been injected.

    The decorated method must have the following prototype :

    .. python::
       def validation_method(self, bundle_context):
           '''
           Method called when the component is validated

           bundle_context: The component's bundle context
           '''
           # ...

    If the validation callback raises an exception, the component is considered
    not validated.

    If the component provides a service, the validation method is called before
    the provided service is registered to the framework.

    :param method: The decorated method
    :raise TypeError: The decorated element is not a valid function
    """
    if not isinstance(method, types.FunctionType):
        raise TypeError("@Validate can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "bundle_context")

    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_VALIDATE)
    return method


def Invalidate(method):
    """
    Invalidation callback decorator, called when a component becomes invalid,
    i.e. if one of its required dependencies disappeared

    The decorated method must have the following prototype :

    .. python::
       def invalidation_method(self, bundle_context):
           '''
           Method called when the component is invalidated

           bundle_context: The component's bundle context
           '''
           # ...

    Exceptions raised by an invalidation callback are ignored.

    If the component provides a service, the invalidation method is called
    after the provided service has been unregistered to the framework.

    :param method: The decorated method
    :raise TypeError: The decorated element is not a function
    """

    if not isinstance(method, types.FunctionType):
        raise TypeError("@Invalidate can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "bundle_context")

    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_INVALIDATE)
    return method


def PostRegistration(method):
    """
    Post service registration callback decorator, called when a service of the
    component has been registered.

    The decorated method must have the following prototype :

    .. python::
       def callback_method(self, service_reference):
           '''
           Method called when a service of the component has been registered

           service_reference: The ServiceReference of the provided service
           '''
           # ...

    :param method: The decorated method
    :raise TypeError: The decorated element is not a valid function
    """
    if not isinstance(method, types.FunctionType):
        raise TypeError("@PostRegistration can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "service_reference")
    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_POST_REGISTRATION)
    return method


def PostUnregistration(method):
    """
    Post service unregistration callback decorator, called when a service of
    the component has been unregistered.

    The decorated method must have the following prototype :

    .. python::
       def callback_method(self, service_reference):
           '''
           Method called when a service of the component has been unregistered

           service_reference: The ServiceReference of the provided service
           '''
           # ...

    :param method: The decorated method
    :raise TypeError: The decorated element is not a valid function
    """
    if not isinstance(method, types.FunctionType):
        raise TypeError("@PostUnregistration can only be applied on functions")

    # Tests the number of parameters
    validate_method_arity(method, "service_reference")
    _append_object_entry(method, constants.IPOPO_METHOD_CALLBACKS,
                         constants.IPOPO_CALLBACK_POST_UNREGISTRATION)
    return method
