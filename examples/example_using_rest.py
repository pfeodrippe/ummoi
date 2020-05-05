from flask import Flask
from flask import request
import json
app = Flask(__name__)

vars_keys = ['c1', 'c2', 'account', 'receiver-new-amount', 'sender-new-amount',
             'sender', 'receiver', 'money', 'pc']

@app.route('/', methods = ['POST'])
def transaction():
    process = request.json['self']
    account = request.json['account']
    vars    = dict(zip(vars_keys, request.json['vars']))

    sender   = vars['sender'][process]
    receiver = vars['receiver'][process]
    money    = vars['money'][process]

    account[sender]   -= money
    account[receiver] += money

    return account
