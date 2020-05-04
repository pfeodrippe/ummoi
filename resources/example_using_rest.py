from flask import Flask
from flask import request
app = Flask(__name__)

@app.route('/', methods = ['POST'])
def hello_world():
    return 'Hello, World!'
