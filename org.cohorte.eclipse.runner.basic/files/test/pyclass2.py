
import pyclass



class TestClassWithObject(object):
    def __init__(self, str):
        self.str = str
        
    def method(self):
        return self.str
        
    def set(self, testClass):
        self.test = testClass
        
    def get(self):
        return self.test;
    
        
        
        
