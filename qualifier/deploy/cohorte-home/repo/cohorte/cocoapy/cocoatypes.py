#!/usr/bin/python
# -- Content-Encoding: UTF-8 --
"""
:author: Phillip Nguyen
:license: BSD 3-Clause License

# objective-ctypes
#
# Copyright (c) 2011, Phillip Nguyen
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
# Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
# Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
# Neither the name of objective-ctypes nor the names of its
# contributors may be used to endorse or promote products derived from
# this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
# FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
# INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
# LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
# LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
# ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
# POSSIBILITY OF SUCH DAMAGE.
"""

from ctypes import *
import platform
import struct

__LP64__ = (8 * struct.calcsize("P") == 64)
__i386__ = (platform.machine() == 'i386')

PyObjectEncoding = b'{PyObject=@}'


def encoding_for_ctype(vartype):
    typecodes = {c_char: b'c', c_int: b'i', c_short: b's', c_long: b'l',
                 c_longlong: b'q', c_ubyte: b'C', c_uint: b'I', c_ushort: b'S',
                 c_ulong: b'L', c_ulonglong: b'Q', c_float: b'f',
                 c_double: b'd', c_bool: b'B', c_char_p: b'*', c_void_p: b'@',
                 py_object: PyObjectEncoding}
    return typecodes.get(vartype, b'?')

# Note CGBase.h located at
# /System/Library/Frameworks/ApplicationServices.framework/Frameworks/CoreGraphics.framework/Headers/CGBase.h
# defines CGFloat as double if __LP64__, otherwise it's a float.
if __LP64__:
    NSInteger = c_long
    NSUInteger = c_ulong
    CGFloat = c_double
    NSPointEncoding = b'{CGPoint=dd}'
    NSSizeEncoding = b'{CGSize=dd}'
    NSRectEncoding = b'{CGRect={CGPoint=dd}{CGSize=dd}}'
    NSRangeEncoding = b'{_NSRange=QQ}'
else:
    NSInteger = c_int
    NSUInteger = c_uint
    CGFloat = c_float
    NSPointEncoding = b'{_NSPoint=ff}'
    NSSizeEncoding = b'{_NSSize=ff}'
    NSRectEncoding = b'{_NSRect={_NSPoint=ff}{_NSSize=ff}}'
    NSRangeEncoding = b'{_NSRange=II}'

NSIntegerEncoding = encoding_for_ctype(NSInteger)
NSUIntegerEncoding = encoding_for_ctype(NSUInteger)
CGFloatEncoding = encoding_for_ctype(CGFloat)

# Special case so that NSImage.initWithCGImage_size_() will work.
CGImageEncoding = b'{CGImage=}'

NSZoneEncoding = b'{_NSZone=}'


# from /System/Library/Frameworks/Foundation.framework/Headers/NSGeometry.h
class NSPoint(Structure):
    _fields_ = [("x", CGFloat), ("y", CGFloat)]


CGPoint = NSPoint


class NSSize(Structure):
    _fields_ = [("width", CGFloat), ("height", CGFloat)]


CGSize = NSSize


class NSRect(Structure):
    _fields_ = [("origin", NSPoint), ("size", NSSize)]


CGRect = NSRect


def NSMakeSize(w, h):
    return NSSize(w, h)


def NSMakeRect(x, y, w, h):
    return NSRect(NSPoint(x, y), NSSize(w, h))

# NSDate.h
NSTimeInterval = c_double

CFIndex = c_long
UniChar = c_ushort
unichar = c_wchar  # (actually defined as c_ushort in NSString.h, but need ctypes to convert properly)
CGGlyph = c_ushort


# CFRange struct defined in CFBase.h
# This replaces the CFRangeMake(LOC, LEN) macro.
class CFRange(Structure):
    _fields_ = [("location", CFIndex), ("length", CFIndex)]


# NSRange.h  (Note, not defined the same as CFRange)
class NSRange(Structure):
    _fields_ = [("location", NSUInteger), ("length", NSUInteger)]


NSZeroPoint = NSPoint(0, 0)

CFTypeID = c_ulong
CFNumberType = c_uint32
