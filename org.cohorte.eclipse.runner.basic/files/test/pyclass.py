

class TestClass(object):
    def __init__(self):
        pass
    
    def method(self, str):
        return str
        
    
        
class TestClassWithParam(object):
    def __init__(self, str):
        self.str = str
        
    def method(self):
        return self.str

    @staticmethod   
    def static(str):
        return str    
        
        

        
