#!/usr/bin/env python3
import json
import os

vars_keys = ['c1', 'c2', 'account', 'sender', 'receiver', 'money', 'pc']

process = json.loads(os.environ['self'])
vars    = dict(zip(vars_keys, json.loads(os.environ['vars'])))

sender   = vars['sender'][process]
receiver = vars['receiver'][process]
money    = vars['money'][process]
account  = vars['account']

account[sender]   -= money
account[receiver] += money

print(json.dumps(account))
