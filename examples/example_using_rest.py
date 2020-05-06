from flask import Flask
from flask import request
import json
app = Flask(__name__)

vars_keys = ['c1', 'c2', 'account', 'sender', 'receiver', 'money', 'pc']

@app.route('/', methods = ['POST'])
def transaction():
    process = request.json['self']
    vars    = dict(zip(vars_keys, request.json['vars']))

    sender   = vars['sender'][process]
    receiver = vars['receiver'][process]
    money    = vars['money'][process]
    account  = vars['account']

    account[sender]   -= money
    account[receiver] += money

    return account
